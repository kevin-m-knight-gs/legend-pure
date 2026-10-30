# Plan: PELT consistency checks

Status: proposal, 2026-10-15.
Prerequisite: [byte-identical PELT files](2026-10-15-pelt-byte-identity.md), through its Phase 2. This plan starts
after that phase, and does not wait for that plan's decision on compression.
Related: [path provider and reference ID versions](2026-10-16-pelt-path-provider-and-reference-id-versions.md),
and the [roadmap](2026-10-16-pelt-serialization-roadmap.md) for how the plans fit together.

## Goal

When PELT modules are loaded, find out before any element is deserialized whether any module was built against
different versions of its dependencies than the ones being loaded, and decide precisely whether that matters.
If it does, fail with an error that names the modules, versions, elements, classifiers, and properties involved.
Never silently corrupt the graph.

The design has two tiers. The fast path compares module digests and costs almost nothing. The slow path runs
only on a digest mismatch and checks whether the change affects what the module actually uses.

| Concern | How this design meets it |
|---|---|
| Clear error | One aggregated report that names the modules, versions, elements, classifiers, and properties |
| As early as possible | Runs when the `ElementLoader` is built, before any element loads |
| As accurate as possible | The slow path checks what can actually break: referenced elements, and instance shapes including multiplicity |
| As targeted as possible | Only the elements a module references and the classifiers its instances use |
| Minimal storage | One file per module: a small summary, then details that are read only on a mismatch |
| Minimal cost | The fast path reads the start of one file per module and compares a few digests |

## Constraints

- **PELT content is stable.** The checks rely on what the byte-identity plan's Phase 2 establishes, together with
  any compiler fixes its determinism check calls for: the same sources, compiled by the same legend-pure version,
  produce the same logical content in every environment and language, and its uncompressed encoding is canonical.
  Digests here are computed from that plan's
  [serializable form](2026-10-15-pelt-byte-identity.md#f0-the-serializable-form) of each element, in its digest
  encoding, never from a file's bytes. So they do not depend on how that plan settles compression, or on which
  format versions write the files. The file this plan adds must preserve whatever invariant that plan settles on:
  byte identity, or identical uncompressed counterparts.
- **Existing binaries must keep loading.** They carry no consistency data, so the checks skip them.
- **Builds may insist** that dependencies carry consistency data.
- **There is more than one loader.** `PureCompilerLoader`, `MetadataPelt` (used in legend-engine by `PureModel`,
  among others), and others to come. The check must not rely on each loader remembering to call it.
- **Non-Java loaders should be able to run the check** (see the byte-identity plan). So it must work from
  metadata alone, without a compiled graph or `ProcessorSupport`.

## What can go wrong

1. **Metamodel change.** Instances were written with a shape (property names, multiplicities, and types) that the
   reader's classes no longer have. Today, the generated readers (`M3LazyCoreInstanceGenerator`,
   `ClassPeltImplProcessor`) silently drop properties they no longer declare and leave missing ones empty. An
   added property with a lower bound above zero is as much a problem as a removed one. *Addressed by the
   footprint check.*
2. **Model change.** An element a module references changed or disappeared. A positional reference ID can then
   resolve to a different instance without error, and a dependent's back-references can land on the wrong
   instances. *Addressed by the referenced-element check.*
3. **Anything else that changed.** The fast path detects that *something* changed, and the slow path decides
   whether it matters.

**Not covered:** staleness that is not inconsistency. For example, a new overload that a recompile would select:
the module's data is still consistent with what it references.

## Design overview

At build time, for each module M:

- an **element content digest (ECD)** for each element, ignoring source information;
- a **module consistency digest (MCD)** over the ECDs of M's elements;
- a **consistency file** (`.pcd`), which starts with a **summary**: M's MCD and, for every transitive dependency D,
  D's MCD as seen at build time. **Details** follow:
  1. the ECDs of M's elements,
  2. the referenced elements in other modules, with their ECDs at build time,
  3. M's metamodel footprint,
  4. M's type declarations.

At load time:

- **Fast path:** for each module A that has consistency data, read the summary at the start of its file, and
  compare each recorded dependency MCD with that dependency's current MCD. If they all match, A is done.
- **Slow path:** for each module A with a mismatch, read the details it needs, then run the referenced-element
  check for each mismatched dependency and the footprint check for all of A's classifiers.
  - If everything passes, log at INFO and continue.
  - If anything fails, throw one exception listing every failure.

## Definitions

### Element content digest (ECD)

`ECD(e)` is SHA-256 over the ASCII bytes `pelt-ecd-1`, a zero byte, and the element's serializable form in the
byte-identity plan's digest encoding, without source information (no source ID, and no source information on any
node). Whichever format versions write the element's file, its ECD is the same; reference ID versions are another
matter (below).

**Why ignore source information:** a line shift in a dependency does not make its dependents inconsistent.
Including it would send every platform edit down the slow path.

**Why not add back references from other elements:** comparisons can pair them with a serializable form, but they
come from the elements that refer to it, which are mostly in its dependents, so including them would change a
dependency's ECDs whenever a dependent changed.

**Why digest content, not source:** the same source compiled by a different legend-pure version can produce
different shapes and reference IDs.

**Why not a file format:** a new element format version, or a new string index version inside one, that encodes
the same content differently must not change ECDs. Otherwise, a dependency rebuilt with a newer legend-pure would
fail the referenced-element check of every dependent that was not rebuilt, though nothing they rely on changed.
The element V1 payload, for example, is fixed only in part: it starts with a string index that records its own
version (`StringIndexer.java:46`), and the newest loaded string index version is the default. The digest encoding
has no string index, and a new kind of value only adds a tag to it. The prefix changes, to `pelt-ecd-2`, only if
the digest encoding changes in a way that changes existing digests, and the digest version changes with it. A
prefix is hashed into a digest, not stored with it, so the consistency file records the digest version of every
digest it holds ([Files and formats](#files-and-formats)).

**Reference ID versions do change ECDs.** A dependency rebuilt with another reference ID version has different IDs,
and IDs are content. Its dependents' back-reference files are keyed by those IDs, so they need rebuilding, and the
referenced-element check correctly fails them. The summary records each dependency's reference ID version, so the
failure can name that cause (see the [path provider and reference ID
versions plan](2026-10-16-pelt-path-provider-and-reference-id-versions.md)).

### Module consistency digest (MCD)

`MCD(M)` is SHA-256 over the ASCII bytes `pelt-mcd-1`, a zero byte, and then, for each element of M in manifest
order (sorted by path): the path as length-prefixed UTF-8, followed by its ECD.

**An MCD covers only M's own content, not its dependencies'.** Every loaded module is checked against its own
recorded dependencies, so chaining digests through dependencies would only add false mismatches.

**Digests are copied, not recomputed.** At build time, a dependency's MCD and ECDs come from its consistency file,
together with their digest version, so they do not depend on the build environment. They are recomputed only for a
dependency compiled from source in the same build, and since its content is stable, recomputation gives the same
digests in every environment.

### Footprint

For each exact classifier K of any node that M serializes, the footprint records:

- **The writer shape.** Every property of K, sorted by name. Each entry has:
  - the name,
  - the source type (the owning class or association),
  - the multiplicity: lower bound, upper bound or unbounded, or a multiplicity parameter,
  - the raw type path, or a marker for a type parameter.

  The shape is flattened (own, inherited, and association properties) by the same algorithm the loader uses (see
  [Declarations](#declarations)).
- **A shape hash** over the writer shape.
- **Observed counts.** For each property, the minimum and maximum number of values over M's instances of K. A
  property never written is `[0, 0]`.
- **Observed value types.** For each property, the classifiers of its instance values and the primitive types of
  its primitive values. Only types that conformed to the declared type at build time are recorded.

Back-references stored in module metadata are not part of the footprint. Loader code applies them from typed
records, so a change to them is a metadata format change, handled by format versions. Back-reference properties
whose values are written in the element stream *are* in the footprint, like any other property.

### Declarations

For each type M defines, the details record:

- **Every type:** its path and its direct generalizations (raw type paths). This lets subtype checks work for
  classes, enumerations, primitive types, and so on.
- **Each class:** its own properties, each with name, multiplicity, and raw type.
- **Each association:** for each of its properties, the class it is added to, plus name, multiplicity, and raw
  type.

The current reader shape of K for module A is computed by flattening over these declarations from the modules in
**A's dependency closure**, at their current versions. A's closure, rather than the whole load set, is correct:
an association in a module A cannot see would be "added" from A's point of view, yet it is not version skew.

The flattening algorithm must give the same result as `class_getSimplePropertiesByName`. At build time the
footprint's writer shapes are computed with this algorithm too, and tests assert that it agrees with
`ProcessorSupport` for every class in platform and in the DSL modules.

**Assumption:** declarations come from the metamodel module's PELT files and stand in for its generated Java
classes, which actually do the reading. The two ship in the same jar, so this holds unless jars from different
builds are mixed. See [decision 6](#decisions-and-open-questions).

## Rules

### Footprint check

For module A, classifier K, recorded writer shape W, and current reader shape R:

1. **Classifier gone.** If K no longer exists, fail.
2. **Multiplicity rule.** For each property name p in W or R, let:
   - `obs` be A's observed counts for p, `[0, 0]` if never written;
   - `W(p)` and `R(p)` be the declared multiplicities, with `[0..0]` standing in when p is absent.

   **p fails if `obs` fits `W(p)` but does not fit `R(p)`.** The "fits `W(p)`" condition keeps pre-existing
   mismatches, where M3 data already disagrees with its declared multiplicity, from raising errors.
3. **Source-type rule.** If p is in both shapes but its source type changed, fail.
   - This is conservative. `SimpleLazyCoreInstanceState` keys values by name *and* source type, while the
     generated readers key by name only.
   - It can be relaxed once every reader keys by name only.
4. **Type rule.** For each recorded value type v of p, fail if v does not conform to the raw type of `R(p)` under
   the current generalizations. Type parameters are skipped. Recorded types all conformed at build time, so a
   failure means something changed.
5. **Shortcut.** If W's shape hash equals R's, rules 2 and 3 cannot fail, so skip them. Rule 4 still runs, since a
   value type's generalizations can change without K's shape changing.

How the multiplicity rule plays out:

| Change from W to R | Example | Observed | Outcome |
|---|---|---|---|
| Property added, lower bound 0 | new `name: String[0..1]` | `[0, 0]` | pass |
| Property added, lower bound above 0 | new `name: String[1]` | `[0, 0]` | **fail**: instances have no value |
| Property added, lower bound above 0, with a default | new `name: String[1] = 'x'` | `[0, 0]` | **fail**: the default is not evaluated at load |
| Property removed, never written | | `[0, 0]` | pass |
| Property removed, written | `value` removed | `[1, 1]` | **fail**: the data would be dropped |
| Property renamed | `value` becomes `name` | | both of the above: **fail** |
| Lower bound raised, every instance has a value | `[0..1]` to `[1]` | `[1, 1]` | pass |
| Lower bound raised, some instance has none | `[0..1]` to `[1]` | `[0, 1]` | **fail** |
| Upper bound lowered below what was written | `[*]` to `[0..1]` | `[0, 3]` | **fail** |
| Bounds widened | `[1]` to `[*]` | `[1, 1]` | pass |

**Default values never satisfy an added required property.** Evaluating a default can require execution, and PELTs
may be loaded where no execution is available, for example only to compile against them. So the rule treats a
property with a default exactly like one without.

### Referenced-element check

**Built from:**

- each external reference in the serializable forms of the module's elements, reduced to the element it points
  into, as the resolver for the module's reference ID version reports it (R6 in the
  [versions plan](2026-10-16-pelt-path-provider-and-reference-id-versions.md#2-rules)), since parsing the ID as a
  `GraphPath` works only for V1;
- each target element in `ModuleBackReferenceMetadata`;
- filtered to elements in other modules, and recorded with each element's ECD and its module at build time.

Those serializable forms are what the element files hold, so these references cannot disagree with them.
`ModuleExternalReferenceMetadata` comes from a separate walk of the graph
(`ConcreteElementMetadataGenerator.java:136`), which agrees with the element files only as long as the two walks
agree.

**On a mismatch with dependency D,** for each recorded element whose module was D:

1. Look up the element's current module in `MetadataIndex`. Elements can move between modules, and references are
   by path, so a move alone is harmless.
2. If the element no longer exists, fail.
3. If its ECD differs, fail.

If D's reference ID version differs from the one A's summary recorded for D, the findings say so: D's IDs changed,
not necessarily its content.

**Why whole elements:** identical content covers everything a dependent can rely on: reference IDs into the
element (keyed and positional), back-reference instance IDs, and data derived from the element, such as types
inferred from a function signature.

**Known coarseness:** an edit to the body of a referenced function fails every dependent that calls it. A later
refinement could digest a per-classifier "interface" projection of the serializable form, for example without a
concrete function's expression sequence. Even unrefined, a false positive is no worse than the fast-path-only
phase: an error asking for a rebuild.

## Files and formats

One new file goes beside the existing module files under `org/finos/legend/pure/module/`: `<module>.pcd`, the
module's consistency data. Serialization already writes a great many files, so the summary and the details share
one file rather than adding two kinds.

**Layout.** The file follows the conventions of the other module files: a signature, a version, string indexes,
and big-endian integers. It starts with the summary, uncompressed, so the fast path reads only the start of the
file, whether from a directory or from a jar, where reading the start of an entry inflates little more than that
start. The details follow, compressed the way module metadata is compressed under the byte-identity plan's
outcome: with its encoder, or not at all if it settles on uncompressed files. As the byte-identity plan's
[F2](2026-10-15-pelt-byte-identity.md#f2-an-uncompressed-counterpart-for-every-compressed-file) requires of every
compressed file, the extension states the file's uncompressed counterpart, which is the same file with its details
uncompressed, and its envelope rule: compressed details are exactly one raw deflate stream, ending at the end of
the file.

A path method goes on `FilePathProviderExtension` as a default method, to keep the interface compatible.

**A new serializer extension interface,** `ModuleConsistencySerializerExtension`, writes and reads the file, with
its own version line. It is loaded and wired through `FileSerializer` and `FileDeserializer` like the existing
ones. With its own line, the consistency format can change without a new module metadata version, and module
metadata can change without a new consistency format.

**It is a PELT file,** so it must satisfy the byte-identity invariant like every other kind. It comes with its
specification, its serializable form, and its conformance-corpus entries.

**Why a separate file rather than a field in the manifest.** Data that earlier readers may safely ignore goes in a
file they skip (see the [roadmap](2026-10-16-pelt-serialization-roadmap.md)). Consistency data is that kind, and
every module carries it, so a manifest field would move every new module's manifest to a metadata version that
earlier readers reject. Version records are the opposite case: earlier readers must not ignore them, and most
modules have none, so the [path provider and reference ID
versions plan](2026-10-16-pelt-path-provider-and-reference-id-versions.md) puts them in the manifest only when they
are needed. The price of a separate file is one lookup per module on every load, which the fast path's cost
includes.

**Summary contents:**

- format version,
- digest version: which definitions of ECDs and MCDs the module's own digests use, 1 for `pelt-ecd-1` and
  `pelt-mcd-1`,
- module name,
- MCD,
- an optional label (the Maven project version, passed in by the mojo),
- for each transitive dependency, sorted by name: its name, MCD, digest version (which also covers its ECDs in the
  details), reference ID version, and label.

Every reference ID version is 1 until a module can record another, and every digest version is 1 until the digest
encoding changes in a way that changes existing digests. Both fields are in the format from the start, so that the
format does not have to change then. Without the digest version, a dependency rebuilt under a new definition would
look as if every element its dependents reference had changed; with it, the checker reports the change of
definition instead.

**Detail contents:**

1. the ECDs of the module's elements, in manifest order,
2. referenced elements, grouped by module at build time: path and ECD,
3. the footprint,
4. the declarations.

**Size:**

- The summary grows with the dependency closure: for each transitive dependency, a name, a 32-byte MCD, a digest
  version, a reference ID version, and a label.
- The details are dominated by ECDs: 32 bytes per element, or 16 if truncated (see
  [decision 2](#decisions-and-open-questions)).
- Referenced elements grow with what the module references, footprints with the classifiers its instances use,
  and declarations with the types the module defines.

## Where the check runs

- **A new `ModuleConsistencyChecker`** in `serialization/compiler/metadata`, or a new `consistency` package. It
  takes a `MetadataIndex` and a source of consistency files (a `FileDeserializer` plus a class loader or a
  directory). It returns a result: pass, pass with notes, or failures. It finds a module's file the way the
  module's other files are found, which matters once a module can record a path provider version other than 1.
- **It is called from `ElementLoader.Builder.build()`** (`ElementLoader.java:398`).
  - `PureCompilerLoader` and `MetadataPelt` (`MetadataPelt.java:264`) both build an `ElementLoader` from a
    `MetadataIndex` before loading any element. `FSPeltGraphLoaderHybridPureGraphCache` goes through
    `PureCompilerLoader`.
  - So this one call covers every current loader, and every future loader that uses `ElementLoader`.
  - Loaders that bypass `ElementLoader` can call the checker directly; document that contract.
- **Audit legend-engine's PELT users** to confirm they all go through `ElementLoader`: `PureModel`,
  `GenerateJavaProject`, `GenerateDiff`, `ElasticsearchPureSpecificationGenerator`, and `InterpretedMetadata`. The
  [path provider and reference ID versions plan](2026-10-16-pelt-path-provider-and-reference-id-versions.md)
  relies on the same audit.
- **Modes:**
  - `STRICT` (the default): failures throw.
  - `WARN`: failures are logged and loading continues. This is the emergency escape hatch.
  - `OFF`: no check.

  The mode is set on the builder, or by a system property.

## Build-time behavior

- **In `PureCompilerSerializer`:**
  - Compute each element's ECD while serializing it, from the serializable form that the byte-identity plan's
    generator produces and the element's encoder writes: hash it in the digest encoding, without source
    information. The serializable form is already in memory, so this is one more pass over it, not over the graph.
  - Collect the footprint from the same serializable form, which holds each node's classifier, its properties with
    their source types, and its values. Only the classifiers of external references, and the check that observed
    types conform, need the graph.
  - Collect the declarations.
  - Derive referenced elements from the same serializable form, using R6, and from the back-reference metadata
    that `ModuleMetadataGenerator` already produces.
  - Write the consistency file after the other module files.
- **Dependency digests:**
  - For dependencies loaded from PELT (`PureCompilerBinaryGenerator`), read them, with their digest versions, from
    the dependencies' consistency files.
  - For a dependency compiled from source in the same build, compute them in memory.
  - A dependency's reference ID version is 1 until manifests can record another, and then comes from its manifest.
- **Insistence:** if a dependency loaded from PELT has no consistency file, or one in a version this build does
  not support, fail the build: "Dependency 'X' has no consistency metadata. Rebuild it with legend-pure N or
  later."
- **The load-time check also runs during builds in `STRICT` mode,** because dependencies are loaded through
  `PureCompilerLoader`.
- **The label** passes from `PureCompilerMojo` (the project version) through `PureCompilerBinaryGenerator`.

## Load-time behavior and backward compatibility

| Module A | Dependency D | Result |
|---|---|---|
| Legacy (no consistency file) | any | Not checked. Legacy modules are listed once, at DEBUG. |
| Consistency format newer than this loader understands | any | A itself is not checked, with an INFO log, so older loaders tolerate newer files. |
| New | New | Fast path; slow path on a mismatch. |
| New | Legacy | **Fail**: "cannot verify". A recorded D's MCD at build time, so D was replaced by a build from an older legend-pure. |
| New | Consistency format newer than this loader understands | **Fail**: "cannot verify". D was built by a newer legend-pure than this loader, which cannot read D's MCD. The message says to upgrade the loader. |
| New | Rebuilt under a digest version other than the one A recorded | **Fail**: "cannot verify". D's digests no longer use the definitions that A's copies of them use, so they cannot be compared. The message names the change and says to rebuild A. |
| New | Not in the load set | **Fail**: dependency not loaded. |

## Error reporting

- **One exception type** with structured findings that tools can read, plus a readable message.
- **Every finding is reported at once,** with per-module details capped at, say, the first 20 lines plus a count.
- **Example:**

```text
PELT modules are inconsistent: some were built against different versions of their dependencies.
  core_relational (4.12.0) was built against platform 5.81.0; platform 5.82.0 is being loaded:
    - references meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_, which has changed
    - instances of meta::pure::metamodel::extension::Stereotype were written with property 'value' [1],
      which Stereotype no longer has
    - Stereotype now requires property 'name' [1], which those instances do not have
Rebuild core_relational against platform 5.82.0, or load platform 5.81.0.
```

## Phases

The checker is phased, but the format is not. Binaries outlive the release that wrote them, so a release that wrote
only part of the consistency data would leave modules that every later release must handle, and that later builds
would reject as dependencies. Phase 1 writes the complete file.

### Phase 1: the consistency file, and the fast path

- ECD and MCD computation, dependency digests, and build-time insistence.
- The complete consistency file: the summary, and the details (ECDs, referenced elements, footprints, and
  declarations).
- The flattening algorithm, which computes the footprints' writer shapes, with the test of agreement with
  `ProcessorSupport`.
- `ModuleConsistencySerializerExtension`, and the file's format in the PELT specification and the conformance
  corpus.
- The checker's fast path, where any mismatch is an error naming the modules and versions.
- Handling of legacy modules, and the mode switch.
- Exit: every module built from here on carries complete consistency data, and fast-path time is measured on the
  full legend-engine module set.

### Phase 2: slow path

- The rules, and the slow path in the checker, with the messages above.
- Exit: compatible mismatches pass with an INFO log; incompatible ones fail with specific findings.

### Releasing Phases 1 and 2

Phases 1 and 2 ship in the same release. The fast path alone fails every module whose dependency changed in any
way, including changes the module does not use, while the goal is to fail only when a change matters. If a release
must carry Phase 1 without Phase 2, its default mode is `WARN`.

### Phase 3: rollout

- Write user-facing documentation on the errors and modes.
- Adopt in legend-engine.

## Tests

### Unit tests

- **The rules, table-driven:** every row of the multiplicity table, the pre-existing-mismatch exemption, the type
  rule after a generalization change, and a source-type move.
- **ECD:** the same element shifted by some lines gives the same ECD; changed content gives a different one; the
  same element written by the element V1 and V2 extensions, or with different string index versions, gives the
  same ECD.
- **Flattening:** agrees with `class_getSimplePropertiesByName` for every class in platform and in the DSL modules.
- **Referenced elements:** external references written with the test-only reference ID version reduce to the right
  elements (R6).

### Integration tests

Integration tests run in `m3-core`: compile and serialize to a `TemporaryFolder` with `PureCompilerSerializer`,
then load with `PureCompilerLoader` and with an `ElementLoader` configured the way `MetadataPelt` configures one.

| Scenario | Expected result |
|---|---|
| Unchanged dependency | Fast path |
| Dependency changed only by line shifts | Fast path, since ECDs ignore source information |
| Change to an element nothing references | Slow path passes, with an INFO log |
| A referenced function's signature changed | Fails, naming the function |
| A referenced element removed | Fails |
| A referenced element moved, unchanged, to another module | Passes |
| Dependency replaced by a legacy build | Fails |
| Dependency's consistency file in a format version this loader does not know | Fails, saying to upgrade the loader |
| Dependency rebuilt under a digest version other than the one its dependent recorded | Fails, saying to rebuild the dependent |
| Legacy dependent | Not checked |
| `WARN` mode | Logs and loads |

The footprint check needs instances whose classifier is defined in another module. Realistically that classifier
is an M3 class, and M3 cannot be edited in a test. Instead, simulate the change: a test utility rewrites the
dependency's consistency file, changing its declarations (for example, renaming `Stereotype.value` to `name`) and
its MCD, then loads. That exercises the Annotation-rename case end to end. The same utility changes a dependency's
digest version, for the digest-version scenario.

### Performance

- Fast-path time for the full legend-engine module set.
- Slow-path time when platform mismatches.

## Decisions and open questions

1. **Where the data goes:** one separate file (recommended), two files (a summary and details), or a field in a new
   manifest version. A field would move every new module's manifest to a version that earlier readers reject. Two
   files would add a second kind of file, to spare the fast path from reading the start of a larger one.
2. **Digest size.** Full SHA-256 for MCDs. For ECDs, full SHA-256 or its first 16 bytes; 16 bytes is recommended,
   since collisions are not an adversarial concern here.
3. **An opt-out from build-time insistence for the first release?** Not recommended.
4. **The source-type rule:** keep it conservative, or relax it after confirming that every reader keys values by
   name only.
5. **Function-body coarseness:** accept it for now, and add interface projections of the serializable form if
   false positives are common in practice. A projection needs no new encoding.
6. **The reader is code.** A mix of generated code from one build and PELT files from another goes undetected.
   Code generation could embed the module's MCD for comparison later.
7. **Labels:** the Maven version only, or also a VCS revision.
8. **ECD encoding:** the byte-identity plan's digest encoding (recommended); the element V1 payload with its string
   index version pinned, which takes a second encoding pass and has no room for a new kind of value; or the writing
   extension's own payload, which is simpler but changes every ECD whenever the element format changes.
9. **A dependency whose consistency format is newer than the loader:** fail (recommended), or keep the summary's
   layout fixed across format versions, so that earlier loaders can still read every module's MCDs.
