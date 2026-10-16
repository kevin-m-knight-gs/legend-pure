# Plan: more than one reference ID version

Status: proposal, 2026-10-16.

## Goal

A reference ID names an instance so that other elements, other modules, and generated code can refer to it. IDs
come from a versioned `ReferenceIdExtension`, whose provider generates them and whose resolver turns them back into
instances. Only version 1 exists today. Most files that hold IDs record which version produced them, but readers
use the recorded version in only one place, and several paths assume version 1 or the default. If a second
version shipped, some back references would be resolved with the wrong version, and others would be dropped
without any error.

This plan sets the rules that make several versions safe, finds where today's code breaks them, and fixes those
places before any second version exists.

Two facts shape it:

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
| G2 | Modules written with different reference ID versions load together, and every ID resolves to the instance its writer meant. |
| G3 | A version mismatch fails with an error. It never drops a back reference silently or resolves an ID to the wrong instance. |
| G4 | A reader that lacks a version fails when it loads the module's manifest, not partway through loading its elements. |
| G5 | No new files. |
| G6 | Output does not change until a second version exists. |
| G7 | Every loader gets this right without having to opt in. |

G4 matters because elements load lazily: a reader that lacks a version would otherwise fail only when it first
initializes an element written with it, possibly long after startup.

## 1. Where we are

### Where reference IDs live

| Where | What the IDs name | Version recorded | How readers use it |
|---|---|---|---|
| Element files (`.pelt`) | Instances outside the element that it refers to, and each of its own instances | In the header (`ConcreteElementSerializer.java:59`) | Picks the element's resolver (`AbstractLazyConcreteElement.java:117`) |
| Per-element back-reference files (`.pbr`) | Keys: instances of the target element. Values: instances in the writing module's elements, such as function applications, reference usages, and specializations | In the file | Ignored. `ElementLoader` merges every module's file for an element into one map and drops the versions (`ElementLoader.java:218`) |
| External-reference files (`.pxr`) | Instances in other modules that the module's elements refer to | In the file | No loader reads these files |
| Generated Java (compiled mode) | Classes, enumerations, and other instances that generated code looks up, as string literals, for example `getMetadataAccessor().getClass("...")` (`GenericTypeSerializationInCode.java:59`); lambdas, keyed by ID | Not recorded: `IdBuilder` always uses V1 (`ReferenceIdV1IdBuilder.java:59`) | `MetadataPelt` pins version 1 (`MetadataPelt.java:267`); `MetadataEager` indexes instances by V1 IDs computed at run time (`MetadataEager.java:125`) |

Manifests, source metadata, function-name metadata, and the back-reference index hold element paths, not reference
IDs.

### What else is wrong or missing

- **Back-reference values use the wrong resolver.** Generated lazy classes resolve an element's back references
  with the element's own resolver, and virtual packages, which have no element file, use the default
  (`LazyCoreInstanceUtilities.java:555`, from `M3LazyCoreInstanceGenerator.java:1765`, and in compiled mode from
  `ClassPeltImplProcessor.java:1149`). Neither is the version of the file the values came from.
- **The writer picks a version twice.** The element serializer and the module-metadata generator each default to
  the newest loaded version, independently (`FileSerializer.java:59`, `ModuleMetadataGenerator.java:208`). Nothing
  ties a module's element files and its metadata to the same version.
- **`MetadataPelt`'s pin does two jobs.** It sets the default for the whole `ElementLoader`, not only for the IDs in
  generated code that `MetadataPelt.getInstance` resolves (`MetadataPelt.java:66` and `:150`).
- **`ReferenceIds` picks the wrong extension manager.** It uses the array-backed manager when the number of
  versions equals `max - min` (`ReferenceIds.java:292`), which should be `max - min + 1`. A contiguous set of
  versions falls through to the general manager, which works. A set with one gap, such as {1, 3}, throws
  `ArrayIndexOutOfBoundsException` while building.
- **No test loads more than one version.** No test-only `ReferenceIdExtension` exists.
- **A packageable element's V1 ID is its path** (`ReferenceIdGenerator.java:171`).

## 2. Rules

- **R1. Resolve an ID with the version recorded with it.** Never with the default, and never with the version of
  some other file.
- **R2. Compare IDs as strings only within one version.** Where IDs from one module are matched with IDs from
  another, both sides use the same version.
- **R3. Resolution walks the graph.** A resolver computes its answer from the graph, never from IDs stored in
  files. Then an ID of any version resolves against elements written with any version. V1 already works this way;
  every later version must too.
- **R4. No ID means different things in different versions.** If two versions both accept an ID, they resolve it to
  the same instance. The simplest way to keep this rule is to give each later version a syntax that earlier
  versions reject, except for packageable elements (R5). A misrouted ID then fails as invalid instead of resolving
  to another instance, as G3 requires.
- **R5. A packageable element's ID is its path, in every version.** Element roots, including virtual packages,
  then match across versions, and so do generated-code lookups of classes and other types.

## 3. Matching back references

Module M writes back references for element E, which belongs to another module. Their keys name E's instances and
are computed by M's provider. E's file stores IDs computed by E's writer. When the two versions differ, the keys do
not match.

Translating the keys at load time does not work. Resolving a key means walking into E, which needs E initialized,
and initializing E needs its back references.

### Option A: a back-reference file uses its target's version

M writes its back-reference file for E entirely in E's version: keys and values.

- **No format change.** A back-reference file already records one version.
- **The values still resolve.** They name instances in M's elements, which were written with M's version, but
  under R3 any version's IDs resolve against any element.
- **The writer needs E's version.** For a module loaded from binaries, it comes from that module's manifest
  ([section 4](#4-recording-a-modules-version)). For a module compiled in the same run, it is the writer's own
  version.
- **The writer needs E's provider,** which it has, since it could not have loaded E without that version's
  resolver.
- **Today's resolution becomes right for elements:** they resolve back references with their own resolver, which
  is then the file's version. Virtual packages still need R1.

### Option B: two versions per back-reference file

Keys in E's version, values in M's. It needs a new metadata version for back-reference files, and gains nothing
over Option A under R3.

### Option C: one version for a whole dependency closure

A module must use its dependencies' version. That is simple, but it forces every published jar to change version
in lockstep. Option A is its targeted form: only the files where one module's IDs meet another's follow the other
module.

### Option D: version-independent keys

For example, an instance's position in its element's file. That ties back references to the element serializer's
node order, which belongs to a different extension.

### Recommendation

**Option A.** The loader also checks that every back-reference file for E records E's version, and rejects a
mismatch with an error naming both modules (G3).

## 4. Recording a module's version

A module records its reference ID version in its manifest: the version of its element files and its
external-reference file. This uses the mechanism of the
[path provider versions plan](2026-10-15-pelt-path-provider-versions.md). A missing value means version 1, and the
manifest moves to a new metadata version only when the value is not 1. Both fields share that new metadata
version, under that plan's rule that a writer uses the lowest metadata version that can hold the manifest.

- **One version per module.** A writer uses one provider for all of a module's element files and its
  external-reference metadata. Back-reference files follow their targets (section 3).
- **Readers check at the manifest:** a module whose version the reader lacks is rejected when its manifest loads
  (G4).
- **Element headers are checked against the manifest** as each element loads, which catches a module whose files
  disagree.
- **Writers look up a dependency's version in its manifest,** which loaders already hold in `MetadataIndex`.

**Alternative: no manifest field.** Writers could take each dependency element's version from its header as it
loads, and keep a map of them. That works, but it adds state to every loader that feeds a writer, and it gives up
G4.

## 5. Generated Java code

IDs in generated code carry no version.

### Option 1: freeze generated code at V1

`IdBuilder` keeps using V1 for good, and every `Metadata` implementation resolves generated-code IDs with V1.

- No change to generated code, or to the runtime calls it makes, which legend-engine's generated code also uses.
- Generated code from every release keeps working. Under R3 and R5, V1 IDs resolve against elements written with
  any version.
- V1 must stay available for good, which existing binaries require anyway.
- Whatever a later version improves does not reach compiled-mode lookups.

### Option 2: record the version in generated code

Generated calls pass a version, or each generated class declares one, and the current calls keep meaning V1. This
changes the contract between generated code and the runtime.

### Option 3: rely on self-describing IDs

Under R4, a resolver that dispatches on syntax could serve generated code from any version. This depends on R4
holding for every version.

### Recommendation

**Option 1.** R4 keeps Option 3 open if a later version ever has to reach generated code.

## 6. Phases

### Phase 1: fix what is wrong regardless of versions

- Fix the extension-manager selection in `ReferenceIds`, with tests for {1}, {1, 2}, {1, 3}, and {1, 2, 4}.
- Add a test-only V2 that keeps R3 to R5: V1's rules, with a syntax for non-root IDs that V1 rejects.
- Use one reference ID version per module on the write side, for element files and module metadata alike.
- Apply R1 in the loader. `ElementLoader` keeps each back-reference file's version, and virtual packages resolve
  their back references with it. `MetadataPelt` resolves generated-code IDs with V1 explicitly, instead of pinning
  the `ElementLoader` default.

Output does not change.

### Phase 2: back references in the target's version, and the manifest field

- Writers write each back-reference file in its target's version.
- `ModuleManifest` gains the reference ID version, sharing the new metadata version with the path provider field.
  `ModuleBackReferenceMetadata` holds one version for all of a module's back-reference files
  (`ModuleBackReferenceMetadata.java:185`). It gains a version per target, keeping the module-level version as the
  default.
- Loaders check that the reader has the module's version, when the manifest loads; that element headers match the
  manifest; and that each back-reference file records its target's version.
- Tests, with the test-only V2:
  - V1 and V2 modules load together, and back references across the boundary attach in both directions;
  - a back-reference file in the wrong version is rejected;
  - a reader without V2 rejects V2 modules at their manifests and loads the rest;
  - generated code with V1 IDs resolves against elements written with V2.

Output does not change, since only V1 ships.

Exit: a second reference ID version can ship.

### Phase 3: rules for every later version

- The PELT specification ([byte-identity plan](2026-10-15-pelt-byte-identity.md), Phase 0) states R3 to R5, and
  which version each kind of file uses.
- Each new version comes with tests showing that it rejects the other versions' non-root IDs, accepts the same
  root IDs, and resolves against elements written with every other version.

## 7. Compatibility

- **Existing binaries** are all V1 and consistent, and are read as today.
- **Until a second version ships,** output does not change.
- **After it ships,** V1 modules are written as today. A module written with V2 has a manifest in the new metadata
  version, and earlier readers reject it at the manifest. They lack V2's resolver, so they could not read it
  anyway.
- **A V1 module that depends on a V2 module** has V2 back-reference files for that module's elements. A reader
  that lacks V2 rejects the V2 module at its manifest, before it needs those files.
- **External-reference files** keep the version of their module's element files. Anything that reads them later
  must compare their IDs with others by resolving them (R3), not as strings (R2).
- **API changes are additive:** a field on `ModuleManifest`, and per-target versions on
  `ModuleBackReferenceMetadata`, with its module-level version kept as the default.

## 8. Decisions to make

1. **Back-reference files:** in their target's version (recommended), with two versions per file, or with one
   version per dependency closure.
2. **A module's version:** in the manifest (recommended), or taken from element headers, without the check at the
   manifest.
3. **Generated code:** frozen at V1 (recommended), with the version recorded, or relying on self-describing IDs.
4. **R3 to R5:** binding on every later version (recommended), or decided version by version.
