# Plan: more than one path provider or reference ID version

Status: proposal, 2026-10-16. This plan merges two earlier ones, on path provider versions (2026-10-15) and on
reference ID versions (2026-10-16), which shared their mechanism. Three of Phase 1's four fixes are merged
([#1371], [#1372], and [#1373]), and the fourth remains. Phase 2 can be done once the PELT specification exists.
Phase 3 waits until a second version of either extension is wanted.
Related: [byte-identical PELT files](2026-10-15-pelt-byte-identity.md),
[PELT consistency checks](2026-10-15-pelt-consistency-checks.md), and the
[roadmap](2026-10-16-pelt-serialization-roadmap.md) for how the plans fit together.

## Goal

Two serializer extensions decide things that a reader must know before it reads a module's files. Only version 1
of each exists, and the code assumes it in places:

- A **path provider** maps an element or a module to the names of its PELT files. Readers and writers both compute
  file names with the default version, and nothing records which version wrote a module. So a second version that
  names any file differently would leave readers unable to find those files across the change, in either
  direction.
- A **reference ID** names an instance so that other elements, other modules, and generated code can refer to it.
  IDs come from a versioned `ReferenceIdExtension`, whose provider generates them and whose resolver turns them
  back into instances. Most files that hold IDs record which version produced them, but readers use the recorded
  version in only one place, and several paths assume version 1 or the default. If a second version shipped, some
  back references would be resolved with the wrong version, and others would be dropped without any error.

This plan makes several versions of each safe. It records each module's versions in its manifest, sets the rules
that every later version must keep, and fixes the places where today's code would break them.

**Nothing needs a second version yet.** The [byte-identity plan](2026-10-15-pelt-byte-identity.md) keeps today's
file-name hash, and recommends a build-time error for case-only path collisions, so neither needs a new path
provider version. No second reference ID version is proposed either. One could bring IDs that depend less on
position, since a positional ID can resolve to a different instance without error when the element it points into
changes, or IDs that are simpler for a second compiler to generate. So the plan is phased:

- **Phase 1** fixes what is wrong whatever happens. Three of its four fixes are merged, and the fourth remains.
- **Phase 2** writes the rules that every later version must keep into the PELT specification. It needs only the
  specification.
- **Phase 3** records versions and honors them. It ships with the first new version of either extension.

Waiting costs no compatibility. Earlier readers already reject a manifest written with a metadata version they do
not know, cleanly and at the manifest (`ExtensibleSerializer.java:58`, `FileDeserializer.java:275`). Shipping
Phase 3's reading side ahead of a new version would only improve that error message.

Two facts about reference IDs shape the design:

- **Resolution walks the graph.** The V1 resolver parses an ID as a graph path and follows it from the named
  element (`ReferenceIdResolverV1.java:55` and `:64`). So whether an ID resolves depends only on having the right
  resolver, not on which version wrote the target element's file.
- **Back references match IDs as strings.** An element's instances are given their back references by looking up
  each instance's stored ID among the keys of the back-reference files (`AbstractLazyConcreteElement.java:122` and
  `:184`). Those files are written by other modules. If they used a different version than the element's file, the
  keys would not match, and the back references would be dropped without any error.

### Goals

| # | Goal |
|---|---|
| G1 | New readers read every existing binary. |
| G2 | Modules written with different versions load together, from one classpath or one directory, and every ID resolves to the instance its writer meant. |
| G3 | A version mismatch fails with an error. It never drops a back reference silently or resolves an ID to the wrong instance. |
| G4 | Earlier readers keep reading every module that needs no new version. A reader that lacks a module's version fails as soon as it loads the module's manifest, not partway through loading its elements. |
| G5 | No new files. PELT serialization already writes a great many files, and this plan should not add to them. |
| G6 | No extra lookups when loading. |
| G7 | Every module that needs no new version is written byte for byte as today. |
| G8 | Every loader gets this right without having to opt in. |

G4 matters because elements load lazily. `ElementLoader` reads an element's file when the element is first
requested (`ElementLoader.java:119`), so a missing file, or a version the reader lacks, could otherwise surface
long after startup, partway through execution.

## 1. Where we are

### Path providers

- **Every name comes from the default version.** Like every extensible serializer, `FilePathProvider` defaults to
  its newest loaded extension (`ExtensibleSerializer.java:142`), and `FileSerializer` and `FileDeserializer` use
  that default. `FileDeserializer` has overloads that take a version (for example, `FileDeserializer.java:99`), but
  no loader passes one.
- **Only path provider V1 exists,** so every existing binary was written with it.
- **Readers start from the manifest.** Loaders know module names, compute each manifest's location, and read the
  manifest before any other file of the module: `PureCompilerLoader` (`PureCompilerLoader.java:472`) and
  `MetadataPelt` (`MetadataPelt.java:299`). legend-engine loads through `MetadataPelt`. `ElementLoader` finds an
  element's module through `MetadataIndex.getElementModuleName` (`MetadataIndex.java:238`), and back-reference
  lookups already take a module name.
- **The manifest has no room for another field.** Metadata V2's manifest holds a string index, the module name,
  the dependencies, and the elements (`ModuleMetadataSerializerV2.java:68`), and V3 deflates it. Each metadata
  file's header carries its own version, so a writer can choose the version file by file.
- **An unknown metadata version fails cleanly.** `ExtensibleSerializer.getExtension` rejects it
  (`ExtensibleSerializer.java:58`), and `FileDeserializer` reports the module and the file
  (`FileDeserializer.java:275`).
- **A manifest's presence means "already compiled".** `PureCompilerBinaryGenerator` skips any repository whose
  manifest is on the classpath (`PureCompilerBinaryGenerator.java:143`).

### Reference IDs

| Where | What the IDs name | Version recorded | How readers use it |
|---|---|---|---|
| Element files (`.pelt`) | Instances outside the element that it refers to, and each of its own instances | In the header (`ConcreteElementSerializer.java:59`) | Picks the element's resolver (`AbstractLazyConcreteElement.java:117`) |
| Per-element back-reference files (`.pbr`) | Keys: instances of the target element. Values: instances in the writing module's elements, such as function applications, reference usages, and specializations | In the file | Ignored. `ElementLoader` merges every module's file for an element into one map and drops the versions (`ElementLoader.java:218`) |
| External-reference files (`.pxr`) | Instances in other modules that the module's elements refer to | In the file | No loader reads these files |
| Generated Java (compiled mode) | Classes, enumerations, and other instances that generated code looks up, as string literals, for example `getMetadataAccessor().getClass("...")` (`GenericTypeSerializationInCode.java:59`); lambdas, keyed by ID | Not recorded: `IdBuilder` always uses V1 (`ReferenceIdV1IdBuilder.java:59`) | `MetadataPelt` resolves them with version 1 (`MetadataPelt.java:69`); `MetadataEager` indexes instances by V1 IDs computed at run time (`MetadataEager.java:125`) |

Manifests, source metadata, function-name metadata, and the back-reference index hold element paths, not reference
IDs.

**What is wrong or missing:**

- **Back-reference values use the wrong resolver.** Generated lazy classes resolve an element's back references
  with the element's own resolver, and virtual packages, which have no element file, use the default
  (`LazyCoreInstanceUtilities.java:555`, from `M3LazyCoreInstanceGenerator.java:1765`, and in compiled mode from
  `ClassPeltImplProcessor.java:1149`). Neither is the version of the file the values came from. Phase 1's
  remaining fix covers virtual packages. Elements get the right resolver once back-reference files follow their
  targets ([section 5](#5-matching-back-references)).
- **No test loads modules written with different versions together.** A test-only reference ID version now exists
  ([#1373]), and Phase 1's remaining fix adds the first such tests.

**Fixed by Phase 1** ([section 9](#9-phases)):

- **The writer picked a version twice.** The element serializer and the module-metadata generator each defaulted
  to the newest loaded version, independently, so nothing tied a module's element files and its metadata to the
  same version. `PureCompilerSerializer` now writes element files with the module-metadata generator's version
  ([#1373]).
- **`MetadataPelt`'s pin did two jobs.** It set the default for the whole `ElementLoader`, not only for the IDs in
  generated code that `MetadataPelt.getInstance` resolves. It now resolves those IDs with version 1 explicitly,
  and leaves the loader's default alone ([#1372]).
- **`ReferenceIds` picked the wrong extension manager.** It compared the number of versions with `max - min`
  instead of `max - min + 1`. A contiguous set of versions fell through to the general manager, which works. A set
  with one gap, such as {1, 3}, threw `ArrayIndexOutOfBoundsException` while building ([#1371]).

## 2. Rules

### Finding a module's files

One rule is never versioned: where a module's manifest lives. It stays `org/finos/legend/pure/module/<module>.pmf`,
as path provider V1 computes it today (`FilePathProviderExtensionV1.java:50`), for every future version. That
includes V1's file-name hash, for a module name too long to fit. The hash is safe to freeze: the byte-identity plan
specifies it, and copies it into `FilePathTools` so that it cannot change with Eclipse Collections.

Everything else about a module's layout follows the path provider version recorded at the place that rule leads
to. File formats usually start this way, from something at a known place that never changes, such as a PELT
file's signature and version at the start of the file. Loaders already treat the manifest's location this way, so
freezing it changes no behavior.

A missing record means version 1. That handles existing binaries: none of them record a version, and all of them
were written with V1.

### Reference IDs

- **R1. Resolve an ID with the version recorded with it.** Never with the default, and never with the version of
  some other file.
- **R2. Compare IDs as strings only within one version.** Where IDs from one module are matched with IDs from
  another, both sides use the same version.
- **R3. Resolution walks the graph.** A resolver computes its answer from the graph, never from IDs stored in
  files. Then an ID of any version resolves against elements written with any version. V1 already works this way;
  every later version must too.
- **R4. No ID means different things in different versions.** If two versions both accept an ID, they resolve it
  to the same instance. The simplest way to keep this rule is to give each later version a syntax that earlier
  versions reject, except for packageable elements (R5). A misrouted ID then fails as invalid instead of resolving
  to another instance, as G3 requires.
- **R5. A packageable element's ID is its path, in every version.** V1 already does this
  (`ReferenceIdGenerator.java:171`). Element roots, including virtual packages, then match across versions, and so
  do generated-code lookups of classes and other types.

## 3. Recording versions in the manifest

### Version records in the manifest

**How it works:**

- `ModuleManifest` gains a list of version records, each an extension name and a version, sorted by name. Only
  versions other than 1 are listed, so for most modules the list is empty. Two names are defined: one for the path
  provider, and one for reference IDs.
- A new metadata serializer version writes the list as part of the manifest. Metadata V1 to V3 cannot hold it:
  they write a manifest only if its list is empty, and reading any of them gives an empty list.
- A manifest uses the new metadata version only when its list is not empty. Otherwise, it is written exactly as
  today.
- Readers read the manifest at the frozen location, then follow its records: the path provider version for the
  module's other files, and the reference ID version for its IDs ([section 7](#7-reading)).
- A reader that finds a name or a version it does not know rejects the module, with an error naming the module,
  the extension, and the version. Every record is one that readers must not ignore. Data that readers may safely
  ignore goes elsewhere (see the [roadmap](2026-10-16-pelt-serialization-roadmap.md)).

A list, rather than a field per extension, lets a later extension whose version readers must know up front add a
name instead of another metadata version.

**Benefits:**

- No new files, and no extra lookups, since readers read the manifest first anyway.
- The records cannot be separated from the module. The manifest is the module's entry point, so nothing can drop
  the records without dropping the module.
- Earlier readers fail at the manifest, with an error naming the module, the file, and the unknown metadata
  version. They fail only on modules that need a new version, which they could not read correctly anyway.
- Every module that needs no new version is written byte for byte as today, so earlier readers read it, and byte
  identity holds.

**Costs:**

- **A metadata version that manifests use only sometimes.** The usual rule, that writers use the newest version,
  no longer holds for manifests: a writer picks the lowest metadata version that can hold the manifest. A caller
  that asks for an older version than the manifest needs gets an error.
- **Possibly two new metadata versions, not one.** If the byte-identity plan keeps both uncompressed and
  compressed metadata in use, the list goes into a new uncompressed version, and a new compressed version deflates
  it, as V3 deflates V2. For the other kinds of metadata file, the new versions are the same as their
  predecessors, and writers keep using the predecessors.
- **Coupling.** The metadata format now carries values that belong to other extensions. It holds them as opaque
  names and numbers, so the coupling is small.

**If another plan needs a new metadata version for manifests first,** that version carries the list from the
start, empty for every module, and its readers reject any record they do not know. Then Phase 3 needs no further
version.

### A separate file next to the manifest

**How it works.** A small file at the manifest's location, under a new extension, holds a signature and the path
provider version. It is written only when the version is not 1, under the same lowest-version rule
([section 4](#4-what-a-module-records)), and a missing file means version 1.

**Benefits:**

- No change to any existing format.
- Earlier readers ignore the file.

**Costs:**

- **A new kind of PELT file.** It is written only for modules that need it, but like any new kind, it needs a
  signature, a specification, a canonical form, and conformance-corpus entries.
- **One more lookup per module,** which in the common case finds nothing. On a classpath, a lookup that finds
  nothing has to check every jar. `FileDeserializer` already makes such lookups for optional back-reference files
  (`FileDeserializer.java:840`), so the added cost is modest, but it is not zero.
- **The record can be separated from the module.** Packaging that selects files by extension, or a partial copy,
  can drop it without notice. A reader then assumes version 1 and fails to find the renamed files.
- **Earlier readers do not fail at load.** They read the module's manifest and every file whose name did not
  change, and fail only when they reach a renamed file. With lazy loading, that can be long after startup.

### Probing

**How it works.** For each file, a reader computes the file's name under every version it knows and reads
whichever exists. No version is recorded.

**Benefits:**

- No new files, and no format change.
- No extra lookups for names that every version computes the same way.

**Costs:**

- **Ambiguity.** An incremental build that switches versions leaves files with the old names behind, and a reader
  may read them.
- **No detection of unknown versions.** A reader cannot tell a missing file from one written with a version it
  does not know.
- **Earlier readers** behave as with a separate file.

### The version in the manifest's file name

**How it works.** Each version names the manifest differently, for example with a version suffix, and readers
try each name they know.

**Costs:**

- **It versions the one rule that must stay fixed.** Every check for a manifest has to try every name, including
  `PureCompilerBinaryGenerator`'s check for repositories that are already compiled.
- **Earlier readers see a renamed module as missing.** An earlier generator would compile the module again
  instead of reporting an error.

### Not pursued: manifests that list every file name

Readers would no longer compute names at all. But every manifest would grow by one name per file, to solve a
problem that one number solves, and writers would still compute the names.

### Not pursued: reference ID versions taken from element headers

Writers could take each dependency element's version from its header as it loads, and keep a map of them. That
works, but it adds state to every loader that feeds a writer, and it gives up G4.

### Comparison

For the path provider version:

| | Manifest records | Separate file | Probing | Manifest name |
|---|---|---|---|---|
| New files | None | One per module that needs it | None | None |
| Format change | A new metadata version, used only when needed | A new kind of file | None | None |
| Extra lookups | None | One per module | Only for names that differ | Up to one per known version, per module |
| Record can go missing | No | Yes | Nothing is recorded | No |
| Earlier readers, on a renamed module | Fail at the manifest | Fail at the first renamed file | Fail at the first renamed file | See the module as missing |
| Unknown version detected | Yes | Yes | No | No |
| Stale files from a previous version | Ignored | Ignored | May be read | Stale manifests may be read |

### Recommendation

**Version records in the manifest,** for both extensions. They are the only option that adds no files and no
lookups and keeps the records inseparable from the module. Their cost, a metadata version that manifests use only
when a module needs a new version, falls exactly where earlier readers would fail anyway. They also turn a failure
partway through lazy loading into a clear one at the manifest. A separate file is the fallback for the path
provider version, if a change to the manifest format proves unacceptable.

## 4. What a module records

### The lowest path provider version that works

A writer could simply record the version it wrote with. Recording the lowest version that names all of the
module's files the same way is better:

- **Modules whose names do not change stay untouched:** the same manifest, the same metadata version, and the
  same bytes. Earlier readers read them.
- **Only modules with renamed files pay the compatibility cost.**
- **The record depends only on the file names,** not on the writer's configuration. Two writers with different
  default versions write the same manifest whenever they write the same file names, so byte identity holds across
  them.

The writer computes the module's file names under each version, from version 1 up, and records the first version
whose names all match. That covers every file the module writes: element files, module metadata files,
per-element back-reference files, and any kind added later, such as the consistency checks plan's file. It takes
string computation only.

### One reference ID version per module

A module's reference ID version is the version of its element files and its external-reference file. A writer
uses one provider for all of them. Back-reference files follow their targets instead
([section 5](#5-matching-back-references)).

A lowest-version rule would rarely help here: under R4 and R5, a later version gives different IDs to every
instance except packageable elements.

## 5. Matching back references

Module M writes back references for element E, which belongs to another module. Their keys name E's instances and
are computed by M's provider. E's file stores IDs computed by E's writer. When the two versions differ, the keys
do not match.

Translating the keys at load time does not work. Resolving a key means walking into E, which needs E initialized,
and initializing E needs its back references.

### A back-reference file uses its target's version

M writes its back-reference file for E entirely in E's version: keys and values.

- **No format change.** A back-reference file already records one version.
- **The values still resolve.** They name instances in M's elements, which were written with M's version, but
  under R3 any version's IDs resolve against any element.
- **The writer needs E's version.** For a module loaded from binaries, it comes from that module's manifest
  ([section 8](#8-writing)). For a module compiled in the same run, it is the writer's own version.
- **The writer needs E's provider,** which it has: a `ReferenceIdExtension` supplies both a provider and a
  resolver, and the writer could not have loaded E without that version's resolver.
- **Today's resolution becomes right for elements:** they resolve back references with their own resolver, which
  is then the file's version. Virtual packages still need R1, which Phase 1's remaining fix gives them.

### Two versions per back-reference file

Keys in E's version, values in M's. It needs a new metadata version for back-reference files, and under R3 it
gains nothing over using the target's version.

### One version for a whole dependency closure

A module must use its dependencies' version. That is simple, but it forces every published jar to change version
in lockstep. Using the target's version is its targeted form: only the files where one module's IDs meet another's
follow the other module.

### Version-independent keys

For example, an instance's position in its element's content. Every back-reference file would then depend on the
order of its target's nodes, which shifts whenever the target changes, even where the instances that its back
references name do not. It would also need a new format for back-reference files, which using the target's version
does not.

### Recommendation

**A back-reference file uses its target's version.** The loader also checks that every back-reference file for E
records E's version, and rejects a mismatch with an error naming both modules (G3).

**When that check runs.** The back-reference index holds only element paths (`ModuleBackReferenceIndex`), so the
check runs when an element's back-reference files are read, as the element loads. That is later than G4 asks of
other failures. But a mismatch arises only when a dependency was rebuilt with another version after its
dependents were built. That is version skew, which the
[consistency checks](2026-10-15-pelt-consistency-checks.md) find before any element loads, and name as a version
change, since they record each dependency's reference ID version. This check is the backstop for modules built
without consistency data. Recording each target's version in the back-reference index would move the check to
load time, at the cost of a new metadata version for the index.

## 6. Generated Java code

IDs in generated code carry no version.

### Freeze generated code at V1

`IdBuilder` keeps using V1 for good, and every `Metadata` implementation resolves generated-code IDs with V1.

- No change to generated code, or to the runtime calls it makes, which legend-engine's generated code also uses.
- Generated code from every release keeps working. Under R3 and R5, V1 IDs resolve against elements written with
  any version.
- V1 must stay available for good, which existing binaries require anyway.
- Whatever a later version improves does not reach compiled-mode lookups.

### Record the version in generated code

Generated calls pass a version, or each generated class declares one, and the current calls keep meaning V1. This
changes the contract between generated code and the runtime.

### Rely on self-describing IDs

Under R4, a resolver that dispatches on syntax could serve generated code from any version. This depends on R4
holding for every version.

### Recommendation

**Freeze generated code at V1.** `MetadataPelt` already resolves generated-code IDs with V1 explicitly ([#1372]).
R4 keeps self-describing IDs open, if a later version ever has to reach generated code.

## 7. Reading

- **Manifests by the frozen rule.** `FilePathProvider` computes manifest locations itself, never through an
  extension. Its manifest methods that take a version are deprecated, and they ignore the version.
- **The rest by the manifest's records.** `FileDeserializer` gains module-scoped reading: given a manifest, it
  reads that module's files with the path provider version the manifest records. The loaders use it:
  - `PureCompilerLoader` for source metadata, function names, and the back-reference index;
  - `MetadataPelt` for the back-reference index;
  - `ElementLoader` for element files and per-element back-reference files, finding each element's manifest
    through `MetadataIndex`.

  Every PELT loader in legend-pure goes through these three classes, so no other loader has to opt in. The
  [consistency checks plan](2026-10-15-pelt-consistency-checks.md) audits legend-engine's PELT users to confirm
  that they do too.
- **Reference ID checks:**
  - when the manifest loads, the reader must have the module's reference ID version (G4);
  - as each element loads, its header must record the manifest's version, which catches a module whose files
    disagree;
  - each back-reference file must record its target's version ([section 5](#5-matching-back-references)).
- **Callers that still use the default path provider version** are right for every module that records none.
  Under section 4, that is every module whose names never changed.
- **Stale files.** An incremental build that changes a module's recorded path provider version leaves files with
  the old names behind. That is not new: an incremental build already leaves the file of a removed element behind.
  Readers never look for such files, since they compute names only for the elements a manifest lists, with the
  version it records.

## 8. Writing

- `FileSerializer` computes the recorded path provider version ([section 4](#4-what-a-module-records)) when it
  writes a module's manifest, and writes the manifest at the lowest metadata version that can hold it.
- `ModuleMetadataGenerator` builds manifests without records, as now. `FileSerializer` adds them as it writes.
- A writer uses one reference ID version for a module's element files and its module metadata, as
  `PureCompilerSerializer` has done since [#1373], and writes each back-reference file in its target's version.
- **A dependency's reference ID version** comes from its manifest, which loaders already hold in `MetadataIndex`.
  For a module compiled in the same run, it is the writer's own version.

Nothing changes until a new version exists. Until then, every module records nothing, and every manifest is
written as today.

## 9. Phases

### Phase 1: fix what is wrong regardless of versions

Three of the four fixes are merged, along with a test-only reference ID version:

- **`ReferenceIds` picks its extension manager correctly** ([#1371]). The tests cover {1}, contiguous sets, sets
  missing one version or several, and explicit and unknown default versions.
- **One reference ID version per module on the write side** ([#1373]). `PureCompilerSerializer` writes element
  files with the module-metadata generator's version.
- **`MetadataPelt` resolves generated-code IDs with V1 explicitly** ([#1372]), instead of pinning the
  `ElementLoader` default.
- **A test-only reference ID version that keeps R3 to R5** ([#1373]). `PrefixedReferenceIdExtension`, in m3-core's
  test sources, gives each packageable element its path, as V1 does, and every other instance its V1 ID with a
  prefix that V1 rejects. Its version number is -1: real versions count up from 1, so it can never be mistaken for
  one or collide with one. It is not registered as a service.

The fourth fix remains:

- **Apply R1 to virtual packages' back references.** `ElementLoader` keeps each back-reference file's version, and
  virtual packages resolve their back references with it. `BackReferenceProvider` gains
  `getBackReferencesByReferenceIdVersion`, and the `initialize` method of generated virtual-package classes takes
  the back references grouped by version ([section 10](#10-compatibility)).

Output does not change.

### Phase 2: rules for every later version

This needs the PELT specification, from the byte-identity plan's Phase 0, and nothing else.

- The specification states the frozen manifest location ([section 2](#2-rules)), and R3 to R5.
- It states which reference ID version each kind of file uses: the module's own version for element files and
  external-reference metadata, and the target's version for back-reference files.
- Each new reference ID version comes with tests showing that it rejects the other versions' non-root IDs, accepts
  the same root IDs, and resolves against elements written with every other version.
  `TestPrefixedReferenceIdExtension` checks the first two for the test version.

Output does not change.

### Phase 3: record versions, and honor them

This starts when a new version of either extension is wanted, and ships with that version, or before it.

- `ModuleManifest` gains the list of version records. The new metadata version or versions carry it, following
  the byte-identity plan's outcome on compression, and come with their specification, canonical-form methods, and
  conformance-corpus entries, as that plan requires of every new version. Metadata V1 to V3 refuse to write a
  manifest with records.
- `FilePathProvider` computes manifest locations by the frozen rule. `FileSerializer` computes the recorded path
  provider version and picks the manifest's metadata version.
- `FileDeserializer` gains module-scoped reading, and `PureCompilerLoader`, `MetadataPelt`, and `ElementLoader` use
  it.
- Writers write each back-reference file in its target's version. `ModuleBackReferenceMetadata` holds one version
  for all of a module's back-reference files (`ModuleBackReferenceMetadata.java:185`). It gains a version per
  target, keeping the module-level version as the default.
- Loaders check, when the manifest loads, that the reader has the module's versions; and, as elements load, that
  element headers match the manifest and that each back-reference file records its target's version.
- Tests, with the test-only reference ID version from Phase 1, and a test-only path provider version that renames
  some files. Each test-only version is numbered outside the range of real versions. The path provider's must be
  above that range, such as `Integer.MAX_VALUE`, since the lowest-version rule
  ([section 4](#4-what-a-module-records)) tries versions from 1 up. The tests check that:
  - modules written with V1 and with the test version of each extension load together, from a classpath and from
    a directory, and back references across a reference ID boundary attach in both directions;
  - a module whose names the test path provider does not change records nothing, and its files are byte-identical
    to V1's output;
  - a reader limited to metadata V3 rejects each module that records a version, at its manifest, and reads the
    rest;
  - a reader rejects a record whose name or version it does not know;
  - a back-reference file in the wrong version is rejected;
  - generated code with V1 IDs resolves against elements written with the test reference ID version;
  - files left behind by an earlier path provider version are ignored;
  - once the consistency checks exist, a dependency rebuilt with the test reference ID version fails its
    dependents before any element loads, with a message naming the version change.

Exit: a new version of either extension can ship.

## 10. Compatibility

- **New readers** read every existing binary. Existing binaries record nothing, which means version 1 of both
  extensions, and all of them were written with V1 of each.
- **Until a new version ships,** output does not change.
- **After it ships,** every module that needs no new version is written as today, and earlier readers read it.
  Every module that records a version has a manifest in the new metadata version, and earlier readers reject it at
  the manifest. They could not read it anyway: they would not find its renamed files, or would lack its resolver.
- **A V1 module that depends on a V2 module** has V2 back-reference files for that module's elements. A reader that
  lacks reference ID V2 rejects the V2 module at its manifest, before it needs those files.
- **External-reference files** keep the version of their module's element files. Anything that reads them later
  must compare their IDs with others by resolving them (R3), not as strings (R2).
- **Phase 1's API changes.** The merged fixes add two `FileSerializer.serializeElement` overloads that take a
  reference ID version, and make `ModuleMetadataGenerator.getReferenceIdVersion` public ([#1373]). The remaining
  fix adds `getBackReferencesByReferenceIdVersion` to the `BackReferenceProvider` interface, which only
  `ElementLoader` implements, and changes the `initialize` method of generated virtual-package classes. Only
  `Package` has virtual instances, so only legend-pure's own build generates those classes. legend-engine uses
  neither.
- **Phase 3's API changes are additive:** the list of version records on `ModuleManifest`, module-scoped methods on
  `FileDeserializer`, and per-target versions on `ModuleBackReferenceMetadata`, with its module-level version kept
  as the default. No interface gains a method.

## 11. Decisions to make

1. **Where versions live:** records in the manifest (recommended); for the path provider, a separate file or
   probing; for reference IDs, taken from element headers, without the check at the manifest.
2. **The records:** a list of named records (recommended), or one field per extension.
3. **What a module records for the path provider:** the lowest version that gives the same names (recommended), or
   the version it was written with.
4. **New metadata versions:** one compressed version, or an uncompressed and a compressed one, following the
   byte-identity plan's outcome.
5. **Back-reference files:** in their target's version (recommended), with two versions per file, or with one
   version per dependency closure.
6. **When the back-reference check runs:** as elements load, with the consistency checks finding version skew
   before then (recommended), or when modules load, with each target's version in the back-reference index.
7. **Generated code:** frozen at V1 (recommended), with the version recorded, or relying on self-describing IDs.
8. **R3 to R5:** binding on every later version (recommended), or decided version by version.
9. **When Phase 3 ships:** with the first new version (recommended), or ahead of it.

[#1371]: https://github.com/finos/legend-pure/pull/1371
[#1372]: https://github.com/finos/legend-pure/pull/1372
[#1373]: https://github.com/finos/legend-pure/pull/1373
