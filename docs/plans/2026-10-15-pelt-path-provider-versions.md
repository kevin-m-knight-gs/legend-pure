# Plan: modules written with different path provider versions

Status: proposal, 2026-10-15.

## Goal

A path provider maps an element or a module to the names of its PELT files. Today, legend-pure can use only one
path provider version at a time. Readers and writers both compute file names with the default version, and
nothing records which version wrote a module. So a second path provider version that names any file differently
would leave readers unable to find those files across the change, in either direction.

Two decisions in the [byte-identity plan](2026-10-15-pelt-byte-identity.md) need such a version: escaping
case-only path collisions (decision 7) and replacing the file-name hash (decision 8). This plan lets modules
written with different path provider versions be read side by side, so that either change, or any later one, can
ship.

The plan has to solve a bootstrapping problem: a reader needs a module's path provider version to find the
module's files, including any file that would record that version.

### Goals

| # | Goal |
|---|---|
| G1 | New readers read every existing binary. |
| G2 | Modules written with different path provider versions load together, from one classpath or one directory. |
| G3 | Earlier readers keep reading every module whose file names did not change. On a module whose names did change, they fail as soon as they load its manifest, not partway through loading its elements. |
| G4 | No new files. PELT serialization already writes a great many files, and this plan should not add to them. |
| G5 | No extra lookups when loading. |
| G6 | Every loader gets the right version without having to opt in. |
| G7 | Every module whose file names do not change is written byte for byte as today. |

G3 matters because elements load lazily. `ElementLoader` reads an element's file when the element is first
requested (`ElementLoader.java:119`), so a missing file can surface long after startup, partway through execution.

## 1. Where we are

- **Every name comes from the default version.** Like every extensible serializer, `FilePathProvider` defaults to
  its newest loaded extension (`ExtensibleSerializer.java:142`), and `FileSerializer` and `FileDeserializer` use
  that default. `FileDeserializer` has overloads that take a version (for example, `FileDeserializer.java:99`), but
  no loader passes one.
- **Only path provider V1 exists,** so every existing binary was written with it.
- **Readers start from the manifest.** Loaders know module names, compute each manifest's location, and read the
  manifest before any other file of the module: `PureCompilerLoader` (`PureCompilerLoader.java:472`) and
  `MetadataPelt` (`MetadataPelt.java:297`). legend-engine loads through `MetadataPelt`. `ElementLoader` finds an
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

## 2. Breaking the cycle

One rule is never versioned: where a module's manifest lives. It stays `org/finos/legend/pure/module/<module>.pmf`,
as path provider V1 computes it today (`FilePathProviderExtensionV1.java:50`), for every future version.
Everything else about a module's layout follows the version recorded at the place that rule leads to. File formats
usually start this way, from something at a known place that never changes, such as a PELT file's signature and
version at the start of the file.

Loaders already treat the manifest's location this way, so freezing it changes no behavior. It brings one
restriction: the rule must not depend on the file-name hash, since the hash may change. So a module name must be
short enough that its manifest's file name is never hashed: at most 122 UTF-16 units, given the 126-unit limit
and the four-character extension. A build-time check enforces this, after a scan confirms that no existing module
name is that long.

A missing record means version 1. That handles existing binaries: none of them record a version, and all of them
were written with V1.

## 3. Where to record the version

### Option A: a field in the manifest

**How it works:**

- `ModuleManifest` gains a path provider version, 1 by default.
- A new metadata serializer version writes it as part of the manifest. Metadata V1 to V3 cannot hold it: they
  write a manifest only if its version is 1, and reading any of them gives 1. The
  [reference ID versions plan](2026-10-16-pelt-reference-id-versions.md) records the module's reference ID version
  in the same new metadata version, so a manifest moves to it when either value is not 1.
- A module records the lowest path provider version that names all of its files the same way as the version it
  was written with ([section 4](#4-recording-the-lowest-version-that-works)). Its manifest uses the new metadata
  version only when the recorded version is not 1. Otherwise, the manifest is written exactly as today.
- Readers read the manifest at the frozen location, then use its version for the module's other files.

**Benefits:**

- No new files, and no extra lookups, since readers read the manifest first anyway.
- The record cannot be separated from the module. The manifest is the module's entry point, so nothing can drop
  the record without dropping the module.
- Earlier readers fail at the manifest, with an error naming the module, the file, and the unknown version. They
  fail only on modules whose file names changed, which they could not read correctly anyway.
- Every module whose names did not change is written byte for byte as today, so earlier readers read it, and byte
  identity holds.

**Costs:**

- **A metadata version that manifests use only sometimes.** The usual rule, that writers use the newest version,
  no longer holds for manifests: a writer picks the lowest metadata version that can hold the manifest. A caller
  that asks for an older version than the manifest needs gets an error.
- **Possibly two new versions, not one.** If the byte-identity plan keeps both uncompressed and compressed
  metadata in use, the field goes into a new uncompressed version, and a new compressed version deflates it, as V3
  deflates V2. For the other kinds of metadata file, the new versions are the same as their predecessors, and
  writers keep using the predecessors.
- **Coupling.** The metadata format now carries a value that belongs to the path provider. It holds the value as
  an opaque number, so the coupling is small, but every later metadata version must carry the field.

### Option B: a separate file next to the manifest

**How it works.** A small file at the manifest's location, under a new extension, holds a signature and the path
provider version. It is written only when the version is not 1, under the same lowest-version rule as Option A,
and a missing file means version 1.

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

### Option C: probing

**How it works.** For each file, a reader computes the file's name under every version it knows and reads
whichever exists. No version is recorded.

**Benefits:**

- No new files, and no format change.
- No extra lookups for names that every version computes the same way. For a hash change, that is nearly every
  name.

**Costs:**

- **Ambiguity.** An incremental build that switches versions leaves files with the old names behind, and a reader
  may read them.
- **No detection of unknown versions.** A reader cannot tell a missing file from one written with a version it
  does not know.
- **Earlier readers** behave as with Option B.

### Option D: the version in the manifest's file name

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

### Comparison

| | A. Manifest field | B. Separate file | C. Probing | D. Manifest name |
|---|---|---|---|---|
| New files | None | One per module that needs it | None | None |
| Format change | A new metadata version, used only when needed | A new kind of file | None | None |
| Extra lookups | None | One per module | Only for names that differ | Up to one per known version, per module |
| Record can go missing | No | Yes | Nothing is recorded | No |
| Earlier readers, on a renamed module | Fail at the manifest | Fail at the first renamed file | Fail at the first renamed file | See the module as missing |
| Unknown version detected | Yes | Yes | No | No |
| Stale files from a previous version | Ignored | Ignored | May be read | Stale manifests may be read |

### Recommendation

**Option A.** It is the only option that adds no files and no lookups and keeps the record inseparable from the
module. Its cost, a metadata version that manifests use only when a module's file names change, falls exactly
where earlier readers would fail anyway. It also turns a failure partway through lazy loading into a clear one at
the manifest. Option B is the fallback if a change to the manifest format proves unacceptable.

## 4. Recording the lowest version that works

A writer could simply record the version it wrote with. Recording the lowest version that names all of the
module's files the same way is better:

- **Modules whose names do not change stay untouched:** the same manifest, the same metadata version, and the
  same bytes. Earlier readers read them.
- **Only modules with renamed files pay the compatibility cost.** For a hash change, those are the modules with
  path segments long enough to be hashed, which may be none.
- **The record depends only on the file names,** not on the writer's configuration. Two writers with different
  default versions write the same manifest whenever they write the same file names, so byte identity holds across
  them.

The writer computes the module's file names under each version, from version 1 up, and records the first version
whose names all match: the element files, the module metadata files, and the per-element back-reference files.
That takes string computation only.

## 5. Reading

- **Manifests by the frozen rule.** `FilePathProvider` computes manifest locations itself, never through an
  extension. Its manifest methods that take a version are deprecated, and they ignore the version.
- **The rest by the manifest's version.** `FileDeserializer` gains module-scoped reading: given a manifest, it
  reads that module's files with the version the manifest records. The loaders use it:
  - `PureCompilerLoader` for source metadata, function names, and the back-reference index;
  - `MetadataPelt` for the back-reference index;
  - `ElementLoader` for element files and per-element back-reference files, finding each element's manifest
    through `MetadataIndex`.

  Every PELT loader in legend-pure and legend-engine goes through these three classes, so no other loader has to
  opt in.
- **Callers that still use the default version** are right for every module that records version 1. Under
  section 4, that is every module whose names never changed.
- **Unknown versions.** A reader that finds a recorded version it does not know rejects the module, with an error
  naming the module and the version.
- **Stale files.** An incremental build that changes a module's recorded version leaves files with the old names
  behind. That is not new: an incremental build already leaves the file of a removed element behind. Readers never
  look for such files, since they compute names only for the elements a manifest lists, with the version it
  records.

## 6. Writing

- `FileSerializer` computes the recorded version (section 4) when it writes a module's manifest, and writes the
  manifest at the lowest metadata version that can hold it.
- `ModuleMetadataGenerator` builds manifests with version 1, as now. `FileSerializer` sets the recorded version as
  it writes.

Nothing changes until a second path provider version exists. Until then, every module records version 1, and
every manifest is written as today.

## 7. Phases

### Phase 1: freeze the manifest's location

- `FilePathProvider` computes manifest locations by the frozen rule.
- Scan existing module names, then add the build-time check on their length.

Output does not change.

### Phase 2: record the version, and honor it

- `ModuleManifest` gains the field. The new metadata version or versions carry it, following the byte-identity
  plan's outcome on compression. Metadata V1 to V3 refuse to write a manifest whose version is not 1.
- `FileSerializer` computes the recorded version and picks the manifest's metadata version.
- `FileDeserializer` gains module-scoped reading, and `PureCompilerLoader`, `MetadataPelt`, and `ElementLoader`
  use it.
- Tests use a test-only path provider V2 that renames some files:
  - modules written with V1 and V2 load together, from a classpath and from a directory;
  - a module whose names V2 does not change records version 1, and its files are byte-identical to V1's output;
  - a reader limited to metadata V3 rejects each renamed module at its manifest, and reads the rest;
  - a reader rejects a recorded version it does not know;
  - files left behind by an earlier version are ignored.

Output does not change, since only V1 ships.

Exit: a second path provider version can ship.

### Afterward

The byte-identity plan's decisions 7 and 8 can then ship a path provider V2. Under this plan, new readers read
modules from both sides of the change, and earlier readers read every module whose names did not change.

## 8. Compatibility

- **New readers** read every existing binary: no record means version 1.
- **Until a second path provider version ships,** output does not change.
- **After it ships,** every module whose names do not change is written as today, and earlier readers read it.
  Every module whose names change has a manifest in the new metadata version, and earlier readers reject it at the
  manifest.
- **The API changes are additive:** a field on `ModuleManifest` and module-scoped methods on `FileDeserializer`.
  No interface gains a method.

## 9. Decisions to make

1. **Where the version lives:** a field in the manifest (recommended), a separate file, or probing.
2. **What a module records:** the lowest version that gives the same names (recommended), or the version it was
   written with.
3. **Long module names:** a build-time error (recommended), or keeping today's file-name hash in the frozen rule
   forever, specified even if the byte-identity plan replaces it everywhere else.
4. **New metadata versions:** one compressed version, or an uncompressed and a compressed one, following the
   byte-identity plan's outcome.
