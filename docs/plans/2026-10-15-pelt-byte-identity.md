# Plan: byte-identical PELT files

Status: proposal, 2026-10-15.

## Goal

PELT serialization is meant to be deterministic: every PELT file is a function of its logical content alone (see
[F0](#f0-the-serializable-form)). Two writers that serialize the same content produce the same bytes, whatever the
OS, JDK, CPU, zlib build, or programming language. That has always been the intent. Where it does not hold today,
the existing system has a bug, and this plan addresses it. Afterward, other work can rely on the result as an
invariant.

One purpose stands out: a Pure compiler written in a language other than Java can be tested by compiling the same
sources and comparing its binaries with those from the Java compiler. So the rules that turn a compiled graph into
PELT content must be specified as precisely as the encoding itself (see
[F3](#f3-graph-to-content-rules-for-a-second-compiler)).

The main violation is compression ([section 1.2](#12-compressed-bytes-break-the-invariant)). There are several
ways to fix it, at different costs. A cheaper alternative, Option 5, keeps today's compression and delivers most
of the benefits through a weaker invariant: files that are identical apart from their compression. It is the
baseline for what byte identity costs, and it could be the outcome if today's compression proves worth more than
byte identity. This plan:

- lays out the goals,
- defines a serializable form of each element, which serializers encode and which comparisons and digests can use
  apart from any encoding,
- describes the ways to restore byte identity, and the alternative,
- compares their costs and benefits against that baseline,
- recommends a staged path: measure the cheapest option at once, build the foundations that every option needs,
  and then decide on the encoder.

"PELT files" means every file the serialization layer writes: element files (`.pelt`), the module files (`.pmf`,
`.psr`, `.pxr`, `.pbr`, `.pfn`), and any kind added later. Every new kind of PELT file must satisfy the invariant
too (see [Phase 7](#phase-7-keeping-it-true)).

### Goals

| # | Goal | Why it matters |
|---|---|---|
| G1 | Load anywhere | PELTs written in one environment or language load in any other. |
| G2 | Test serializers by byte comparison | A new reader or writer, in any language, is checked against an existing implementation's output. |
| G3 | Digests mean the same everywhere | A digest of a PELT file's content has the same value in every environment, so anything that keys on or compares digests (caches, deduplication, change detection) gives the same answer everywhere. |
| G4 | Generic tools work on raw files | `sha256sum`, `diff`, content-addressed build caches, artifact deduplication, and reproducibility checks work without knowing anything about PELT. |
| G5 | No spurious rewrites | A JDK or zlib change alone does not change output, so write-if-modified (`FileWriteTools.writeIfModified`) leaves existing files untouched, and incremental builds do no needless work. Maven's up-to-date checks, IDE incremental compilers, and legend-engine's build go by file timestamps ([ADR-007](../decisions/ADR-007-write-if-modified-for-generated-output.md)). |
| G6 | Size and load speed | As good as today in raw directories and in jars, or within agreed tolerances (see [section 6](#6-recommendation)). Size matters especially: the largest models produce jars of many gigabytes. |
| G7 | Low cost per new language | Adding a writer in Rust, C, or another language should be cheap. |
| G8 | Forward compatibility | Readers in earlier legend-pure versions can still read new files. |
| G9 | Test a non-Java compiler against Java's | A compiler in another language compiles the same sources, and its binaries are compared with the Java compiler's. |

Every option keeps backward compatibility: new readers keep reading every existing binary.

Non-goals:

- **Byte-identical jars or zip containers.** See [Outside this plan](#outside-this-plan).
- **Fixing compiler nondeterminism.** If the compiled graph turns out to depend on processing order or the
  environment anywhere other than in back-reference properties, that is a compiler bug for a separate plan. This
  plan only detects it (Phase 2).

## 1. Where we are

Each file is written by a versioned serializer extension, chosen through the existing extension mechanism.
Compression is internal to the extensions:

- element V2 deflates the V1 payload;
- module metadata V3 deflates the V2 payload.

Nothing above the extensions knows about compression, and this plan keeps it that way.

### 1.1 The uncompressed payloads are nearly canonical already

| Aspect | Where | Status across environments |
|---|---|---|
| Fixed-width integers | `AbstractSimpleBinaryWriter` (big-endian via `ByteBuffer`) | Deterministic. |
| Variable-width integers | `getIntWidth` and similar helpers: always the minimum width | Deterministic. |
| String bytes | UTF-8 via `String.getBytes` (`AbstractBinaryReaderWriter.java:56`, `StringWriterV3.java:134`) | Deterministic for valid Unicode. Java silently writes `?` for an unpaired surrogate, and a Rust `String` cannot hold one. |
| String index order | `StringWriterV3.java:307`: sort by `String.length()`, then `String.compareTo` | Deterministic, but both are defined on UTF-16 code units. Sorting by UTF-8 bytes or code points, the natural order in Rust and C, differs for supplementary characters. Lengths differ too: in UTF-8 bytes for all non-ASCII text, and in code points for supplementary characters. |
| String decomposition | `StringWriterV3.processStrings` splits paths, dotted names, and bracketed IDs | A deterministic function of each string, with ASCII delimiters. |
| Module metadata ordering | Sorted by path, name, or ID with `String.compareTo` (`ModuleManifest.java:479`, `ModuleExternalReferenceMetadata.java:177`, `ElementBackReferenceMetadata.java:290`, `ModuleFunctionNameMetadata.java:175`, `ModuleSourceMetadata.java:380`) | Deterministic, with the same UTF-16 caveat. |
| Node order in an element | Breadth-first from the element, properties sorted by name (`SerializerV1.java:544`), values in graph order | Deterministic for a given graph. Back-reference properties, the only ones whose order depends on processing order, are kept out of the element or written sorted (`SerializerV1.java:180` and `:249`). |
| Numbers | Float and Decimal as digit strings from the instance name (`BaseV1.java:175`); Integer as a minimum-width long, or a digit string for BigInteger (`BaseV1.java:219`) | Deterministic. No binary floating-point formatting is involved. |
| Element file names | Element path, with each segment truncated past 126 UTF-16 units, extension included, and given a base-32 hash suffix (`FilePathTools.java:162`) | Deterministic, but the hash uses Eclipse Collections' `SpreadFunctions.longSpreadOne` (`FilePathTools.java:205`), which a specification must reproduce exactly (see [section 5](#5-other-details-a-specification-has-to-pin-down)). |

### 1.2 Compressed bytes break the invariant

The compressing extensions use `java.util.zip.Deflater` at level 7 in raw mode
(`ConcreteElementSerializerV2.java:35` and `:48`, `ModuleMetadataSerializerV3.java:37` and `:50`). `Deflater`
delegates to native zlib, so the output depends on:

- **the zlib version:** JDKs bundle different versions and update them in patch releases;
- **the zlib build:** many Linux distributions build OpenJDK against the system zlib, and some substitute zlib-ng,
  which produces different output (Fedora made zlib-ng its system zlib in Fedora 40);
- **hardware-accelerated deflate** on some platforms, such as IBM Z.

No standard compressor promises identical output across versions or builds. Formats such as deflate, zstd,
Brotli, LZ4, and xz specify only what a decoder must accept. They leave the encoder free to choose matches, block
boundaries, and Huffman codes, and libraries change those choices between releases. zlib does not promise
identical output across versions, and zstd documents the same for its output.

In one environment, meaning the same JDK and zlib build, the output *is* repeatable. That is all write-if-modified
needs today: `FileSerializer` writes through `FileWriteTools.writeIfModified`, which leaves a file untouched when a
later build produces the same bytes.

### 1.3 What PELT-level compression buys today

Earlier measurements found three things:

- **Load time.** PELT-level compression improves it by a similar amount whether loading from a directory or from
  a jar.
- **Size.** It reduces jar size, even though jars compress their entries again.
- **Cost.** It adds serialization time.

These findings are recollections, and this plan records no figures. Phase 4 measures again, under controlled
conditions.

It is not yet understood why the gain survives inside jars, where entries are already deflated. That matters for
Option 4: if part of the gain comes from the I/O path rather than from compression itself, it might be recovered
in other ways.

## 2. Foundations every option needs

### F0. The serializable form

The *serializable form* of an element is the element as data: what each serializer extension encodes, and what
each deserializer returns. Each property value becomes a primitive value, an internal reference (an index among
the element's own instances), or an external reference (the reference ID of an instance outside the element).
Indexes and IDs are plain data, and they compare by value rather than by identity. So the serializable form can be
compared as well as serialized: between compile states in one JVM, between JVMs, and between languages. Comparing,
digesting, and projecting it are uses of the same form, not other forms. Most of it exists already:

- **Element files.** Every element extension's deserializer returns a `DeserializedConcreteElement`, which Phase 1
  renames `SerializableConcreteElement`: the element's path, its reference ID version, and its instances in order.
  Each instance (`InstanceData`) has its name, classifier, source information, reference ID, compile states, and
  property values, each a primitive value or an internal or external reference. `ElementBuilder` builds instances
  from it (`ElementBuilder.java:39`).
- **Module metadata** has a serializable form already: its extensions serialize `ModuleManifest` and the other
  metadata classes, which hold paths and reference IDs, not instances.

The write side is the gap. `SerializerV1` computes the same form from the graph, but only on its way to bytes: it
keeps a list of instances and their internal IDs, and reads each value from the graph as it writes it
(`SerializerV1.java:63` and `:241`). Phase 2 separates the two. A *generator*, which belongs to no version,
computes an element's serializable form from the compiled graph and a reference ID provider, by F3's rules. Each
element extension then encodes a serializable form and decodes one, as the module metadata extensions already do.
Serializing is generating and encoding, and deserializing is decoding:

- **Every version encodes the same form, by construction.** Element membership, ordering, stub validation, and
  which instances get reference IDs are decided once, by the generator, rather than by each version.
- **One generation serves every use.** An element's file, its content digest, and the consistency checks' ECD,
  footprint, and referenced elements all come from the same form.
- **Encoding needs no graph.** A file can be encoded again in another version, the corpus tests encoders directly
  (Phase 3), and writers in other languages start from the same form.
- **Extensions need less.** They no longer need `ProcessorSupport` or a reference ID provider, and the header's
  reference ID version comes from the form.

**Rules:**

- **Each uncompressed payload is a function of the serializable form it encodes** (F1), and decoding a file gives
  back that serializable form.
- **The serializable form keeps everything that any encoding keeps,** or files that differ would decode to equal
  serializable forms. Section 5 lists the values for which today's classes fall short.
- **An encoder rejects a serializable form that its version cannot represent.** It never drops any part of it.
- **For files written with the same versions, serializable forms are equal exactly when uncompressed counterparts
  are** (F2). Phase 2's round-trip tests check this.

**The serializable form has no version.** It leaves the process only through encodings that carry their own
versions: file headers, the string index version in payloads, digest prefixes, and the corpus release. Decoding
always produces the current shape, as module metadata V1 to V3 all decode to today's `ModuleManifest`. That shape
seldom changes. Metamodel changes change content, not shape, since properties are data, and F3's rules change
content from release to release, which the corpus and the consistency checks already allow for. When the shape
must grow, for a new kind of value or a new attribute of instances, it grows by additions only:

- each addition has an "absent" state, which every existing file means;
- an encoder whose version cannot represent the addition rejects any form that uses it (above);
- the digest encoding gives a new kind of value a new tag, so no existing digest changes.

The versions plan's
[version records](2026-10-16-pelt-path-provider-and-reference-id-versions.md#version-records-in-the-manifest) grow
the manifest's form this way: an empty list is what metadata V1 to V3 mean, and they refuse to write any other.
Versions of the form itself would need parallel classes and converters between them, duplicating what format
versions already do.

**Back references from other elements, alongside the form.** Back references among an element's own instances are
part of its serializable form, since its file holds them (sorted by internal ID, `SerializerV1.java:249`). Back
references from other elements are not: the back-reference files that the referring elements' modules write hold
them, and they are never serialized with the element. Comparisons can pair a serializable form with them:

- Each instance with a reference ID gets its back references from other elements, as sorted `BackReference`
  records. `ElementBuilder` already receives these separately from each instance's data
  (`ElementBuilder.java:41`).
- From files, they come from the back-reference files of every loaded module, merged as `ElementLoader` merges
  them. From a graph, the generator reads them from the instances' back-reference properties.
- The content digest leaves them out.
- In a graph, a back reference can lead to an instance that has no reference ID, such as one whose element was
  removed while the back reference stayed. The generator reports such back references; it neither fails on them
  nor drops them.

**Uses:**

- **Equivalence across format versions.** Two files hold the same content when they decode to equal serializable
  forms, whatever format and string index versions wrote them. Reference IDs are content, so this does not extend
  to reference ID versions (see below).
- **Comparisons without files.** Serializable forms generated from two compiled graphs, or from a graph and a
  file, compare directly, and the structural diff (Phase 1) shows where they differ. With back references from
  other elements, a comparison also covers what other elements contribute.
- **Projections.** A comparison or a digest may set parts of the serializable form aside, such as source
  information. A projection is defined on the serializable form, never on bytes.
- **Content digests,** which agree whatever format versions wrote a file.

**The content digest** of an element file is SHA-256 over the ASCII bytes `pelt-content-1`, a zero byte, and the
element's serializable form in the *digest encoding*. The digest encoding is part of the specification (Phase 0).
It writes each part of the serializable form in a fixed order: strings as UTF-8, each preceded by its length, and
each value with a tag for its kind. It has no string index and no compression, and it is never written to a file.
A digest of a projection, or of a serializable form together with back references from other elements, uses the
same encoding under its own prefix, and so would a digest of module metadata, once something needs one.

**Why not an existing payload,** such as element V1's:

- **It is fixed only in part.** It starts with a string index that records its own version
  (`StringIndexer.java:46`). That version is chosen apart from the element version: versions 0 to 3 are
  registered, and the newest is the default.
- **It takes two passes,** since its string index comes first.
- **It cannot grow.** It has no room for a new kind of value: all eight of its node-type codes are taken
  (`BaseV1.java:45`).

The digest encoding can grow with the serializable form. A new kind of value gets a new tag, which changes no
existing digest. Any other change to the encoding changes the prefix of every digest that uses it. A prefix is
hashed into a digest, not stored with it, so anything that stores digests records which definition produced them,
as the consistency checks' digest version does.

**Reference IDs are names.** An external reference holds its target's reference ID, and each instance with an ID
holds its own. Within one reference ID version, IDs are fixed names: the same ID always names the same instance of
the same content, so comparing IDs as strings is exact, and the comparisons and digests above do so. Across
versions, IDs do not compare as strings. Serializable forms written with different reference ID versions are never
equal, since `SerializableConcreteElement.equals` compares the version, and every ID other than a packageable
element's path differs. Comparisons that cross reference ID versions go by what the IDs name:

- **A graph and a file:** generate the form with the file's reference ID version.
- **Two builds:** load one, and write it again, or generate its forms, with the other's version. Then compare as
  usual.
- **Files alone,** without loading them, for example in a tool written in another language: resolve the IDs. Each
  instance's own ID must resolve back to that instance, and is then set aside. Each external reference compares by
  its target element and the target's index in that element's serializable form. This relies on two rules in the
  [versions plan](2026-10-16-pelt-path-provider-and-reference-id-versions.md#2-rules): a resolver needs only the
  target element's serializable form (R3), and an ID names its target element by itself (R6). Internal indexes are
  names too, given by F3's ordering rules, so this assumes that both sides follow the same release's rules.

Nothing needs the last kind yet. The versions plan tests its first new reference ID version with the second kind
(its Phase 3).

### F1. Canonical uncompressed payloads

Every option needs each extension's uncompressed payload to be a function of the serializable form it encodes (F0)
alone, specified well enough to implement elsewhere. That means:

- the specification (Phase 0);
- the fixes in [section 5](#5-other-details-a-specification-has-to-pin-down);
- the verification in Phase 2, across repeated builds and across environments.

With F1 in place, compression is the only remaining source of difference, and it lives inside the compressing
extensions. Serializable forms do not make F1 unnecessary: generic tools on raw files (G4), builds without
needless rewrites (G5), and byte comparison with other writers (G2) all need the bytes themselves to be canonical.

### F2. An uncompressed counterpart for every compressed file

Compression stays internal to the serializer extensions. Each compressing extension's specification states two
things:

- **The uncompressed counterpart of each of its files.** Element V2 deflates element V1's payload, and metadata V3
  deflates metadata V2's, so the counterpart of an element V2 file is the file that element V1 writes for the same
  serializable form, and likewise for metadata. A compressing version added later, or a new kind of file with
  compressed parts, states its counterpart the same way.
- **Its envelope rule.** For element V2 and metadata V3, the payload must be exactly one raw deflate stream
  (RFC 1951) ending exactly at the end of the file, with no trailing bytes. Readers stop at the end of the deflate
  stream and would not notice trailing bytes otherwise, so the extension's tests check the rule, and so does every
  implementation's run of the corpus (Phase 3).

**No canonical form of its own is needed.** Files are compared in two ways, and neither needs one:

- **By serializable form,** whatever format versions wrote them (F0). For files written with the same versions,
  equal serializable forms mean equal uncompressed counterparts (F1).
- **By bytes,** for files written with the same versions, once byte identity holds.

Where raw bytes must be compared without byte identity, compare uncompressed counterparts, which F1 makes
canonical. Phase 2 checks determinism across environments with builds written with element V1 and metadata V2, and
the corpus's expected bytes are those versions' files (Phase 3). When two files with equal serializable forms
differ in their bytes, decompressing them shows whether the difference lies in compression. That stays a
diagnostic in the compressing extensions' own tooling, not a form with digests and interface methods of its own.

**If the plan settles at Option 5,** files stay compressed with stock deflate, and one addition becomes necessary,
for G5: each compressing extension exposes its decompressed payload, through a default method on each extension
interface that returns the payload unchanged for extensions that do not compress. `FileSerializer` writes through
`FileWriteTools.writeIfModified`, a byte-comparing utility in `legend-pure-m4` that the code generators, `.par`
archives, and PCT reports share. The comparison belongs in `FileSerializer`, since m4 cannot see the serializer
extensions: a `FileWriteTools` overload takes an equivalence test, and `FileSerializer` passes one that compares
bytes first, and decompressed payloads only when the bytes differ. Nothing else needs the method, so it is added
only then.

**Digests of content** are content digests (F0), never computed over a file's bytes, compressed or not. They agree
whatever format versions wrote the files, and even where files are not byte-identical, as with existing binaries.

### F3. Graph-to-content rules, for a second compiler

A serializer that starts from serializable forms (F0) needs only the encoding rules. A second compiler starts from
source, so for its binaries to match Java's, it must generate the same serializable forms: the compiled graph must
match, and every rule between that graph and the serializable forms must be specified.

**The compiled graph.** It must be identical, and that includes source information. Specifically:

- inferred generic types, resolved function applications, and generated properties (for example for
  milestoning), with values in the same order;
- source information: line and column numbering, column units for non-ASCII text (UTF-16 code units or code
  points), tab handling, and exactly which positions start and end columns denote.

Apart from back-reference properties, the compiled graph must be a function of the source alone.

**Generation rules,** from graph to serializable form:

- **Element membership.** `SerializerV1.isExternal` (`SerializerV1.java:565`) treats an instance as part of an
  element when the element's source information subsumes the instance's.
- **Ordering.** Breadth-first node order, property order, and the sort order of internally held back-reference
  values.
- **Back-reference properties.** Which properties are back-references
  (`M3PropertyPaths.BACK_REFERENCE_PROPERTY_PATHS`): they are the only properties whose values depend on processing
  order, which is why serialization keeps them out of element order.
- **Stubs.** How stubs are validated and serialized.
- **Compile states.** The compile-state bitset, which depends on the order of the `CompileState` enum.
- **Reference IDs.** The generation rules (`ReferenceIdGenerator`): which key properties are used, and when
  positional indexes are used instead.
- **Module metadata derivation.** The function-name index, back-reference metadata, external references, and
  source metadata.

**Consequences:**

- **The Java compiler is the reference implementation.** Its output at a given legend-pure release is what a
  second compiler must match. The corpus is versioned by release, since that output changes between releases.
- **Compare strictly.** A second compiler passes only by producing the same bytes. Accepting "benign" differences
  would erode the oracle.
- **Diagnose with a structural diff.** A byte diff says only that two files differ. A tool that decodes both and
  reports the first differences by element, node (graph path), property, and value is what makes the oracle usable
  for compiler work.

F3 does not depend on the compression decision. Option 5 serves G9 almost as well as true byte identity, by
comparing serializable forms or uncompressed counterparts.

**When each part is needed.** The compiler's determinism is checked in Phase 2, with the foundations, since every
digest of content that is compared across builds relies on it. The written rules are needed only once a second
compiler is being built, so they come in Phase 6, off the critical path for the invariant.

## 3. Options for compression

Every option keeps compression inside the serializer extensions. Options 1 to 4 restore byte identity. Option 5
stops at equivalence, and it is the baseline: the difference between it and each other option is the price of byte
identity.

### Option 1: a specified canonical deflate encoder

**How it works.** Specify a deflate encoder completely. The compressing extensions use it in place of `Deflater`,
and each language that writes PELTs implements it. The output is plain raw deflate, so every reader keeps its stock
inflater, and nothing above the extensions changes. With F1, compressed bytes become a function of content, which
gives true byte identity.

**The encoder.** Every choice is fixed. The values below are starting points to tune in the prototype, then freeze.

1. **Input.** The complete uncompressed payload, compressed in one pass. Element files are small, and module
   metadata is written from an object model that is already fully in memory, so buffering its payload as well is
   not a problem.
2. **Matching (LZ77).**
   - 32 KiB window; matches of 3 to 258 bytes.
   - A specified hash of the next three bytes indexes a table of 2^15 chain heads.
   - Candidates are visited from the most recent, at most `maxChain` per position. A candidate replaces the best
     match only if strictly longer, so the nearest wins ties.
   - Length-3 matches more than 4,096 bytes back are dropped.
   - One-step lazy evaluation with specified thresholds. zlib's level 7 settings (good 8, lazy 32, nice 128,
     chain 256) are a reasonable starting point.
3. **Blocks.** A new block after every 16,384 symbols and at the end of input. Each block uses whichever of stored,
   fixed, or dynamic encoding is smallest in bits, with ties broken in a fixed order.
4. **Huffman codes.**
   - Code lengths come from an optimal length-limited algorithm (package-merge), with ties broken by frequency,
     then symbol.
   - Limits are 15 bits for the literal/length and distance alphabets and 7 bits for the code-length alphabet.
   - Codes are canonical per RFC 1951, so lengths determine them.
   - The specification spells out the rules for blocks that use zero or one distance codes.
   - The code-length sequence is run-length encoded (symbols 16, 17, and 18) by a specified greedy rule, and HLIT,
     HDIST, and HCLEN are trimmed to the minimum.
5. **End.** The final block is flagged, and the padding bits are zero.

**Benefits:**

- True byte identity, standard decoding everywhere, and no format change.
- A slower encoder costs only build time, so it can be tuned for a better ratio than level 7 (longer match chains,
  or better parsing) if size matters more than serialization time.

**Costs:**

- **The specification:** a few pages plus test vectors.
- **Java:** an encoder on the order of 1,000 lines, with tests and fuzzing against stock inflaters.
- **Each language that writes PELTs:** one more encoder of similar size. Readers need nothing.
- **Speed:** a pure-Java encoder will compress more slowly than native zlib. Only builds pay this.

**Risks:**

- The compression ratio may fall short of zlib level 7. It can use the same matching parameters, so it should come
  close, but that must be measured.
- An encoder bug could produce valid deflate that differs from the reference. Byte comparison against the corpus
  catches that immediately.

**The encoder must be frozen.** Files carry no marker saying which encoder wrote them. A better encoder later would
therefore come as a new extension version, so that "canonical" stays well defined for each version.

**Provenance.** Each implementation of the encoder is written from RFC 1951 and the specification, which anyone may
implement. Copyright does not cover algorithms or parameter values, including those borrowed from zlib. Code
adapted from an existing library keeps that library's notice; the likely sources (zlib, libdeflate, Go's
`compress/flate`, and miniz_oxide) are all permissive. The PR that adds an encoder says which applies.

### Option 2: pin a zlib version, bit for bit

**How it works.** The compressing extensions' specification says "zlib x.y.z, raw, level 7, memLevel 8, default
strategy, one call with `Z_FINISH`". Every writer must reproduce that zlib's output exactly.

**Benefits:**

- Ratio and speed equal today's level 7.
- A reference implementation already exists: C, C++, Rust, and Python writers can vendor that zlib and call it.

**Costs:**

- **Java** needs one of two things, both expensive:
  - a bit-exact pure-Java port of zlib's `deflate.c` and `trees.c`, about 3,000 lines of heuristics;
  - JNI with a native library for every supported platform.
- **Languages with their own deflate,** such as Go and JavaScript, need ports or FFI.
- **The specification is effectively C code.** Behavior is frozen at that version for good.
- **Licensing.** A port is a derivative work of zlib. The zlib license allows it and is compatible with Apache 2.0,
  but the ported files must keep zlib's notice and be marked as altered, and the upstream PR calls them out as
  third-party code. Together with the Apache header, that notice exceeds the checkstyle header rule's limit of two
  matching lines, so the ported files need a suppression. Writers in other languages that bundle zlib's source
  keep the same notice; native binaries need none.

**Risks:** port fidelity. Using the JDK's own zlib cannot work, as 1.2 shows.

### Option 3: a compact canonical format without general compression

**How it works.** A new element and metadata extension version that is compact without a general-purpose
compressor. For example:

- per-classifier property layouts, so nodes do not repeat property names and source types;
- variable-length integers;
- source information encoded as deltas from the parent node.

The format is canonical by construction.

**Benefits:**

- True byte identity with no compressor at all.
- Possibly faster loading, since nothing is inflated.
- The simplest path for new languages: no compression code.

**Costs:**

- A new format design, a new reader and writer in Java, and a new specification.
- New versions, which earlier readers cannot read.

**Risk: size is unknown.** It will probably be larger than today's deflated files, and then it would need
compression again, which brings back the choice between Options 1 and 5. A cheap experiment can show whether this
is worth designing: measure where V1's bytes go (string index, node headers, property headers, values, and source
information) and compare V1 with V2 sizes. It pays off only if most of deflate's gain comes from repeated structure
that a better layout removes.

### Option 4: uncompressed PELT files, with compression left to the container

**How it works.** The default extension versions become the existing uncompressed ones, element V1 and metadata
V2, through the existing version selection. Jars compress their entries as usual.

**Benefits:**

- With F1, PELT files are byte-identical immediately.
- No new code, and no new versions, so older readers read the files.

**Costs:**

- Earlier measurements (1.3) suggest this gives up PELT-level compression's load-time gain from both directories
  and jars, and its size gain in jars.
- Raw directory output is larger.

**Open questions:**

- How large the losses are, especially for the largest models.
- Why the gains survive inside jars. If part of the load-time gain comes from the I/O path rather than from
  compression itself, for example from the number and size of reads, a reader change might recover it.
- Which jar settings drive the size difference: compression level and entry method.

**Kept on the table, and measured first.** It needs no new code, so Phase 4 measures it before any encoder is
built, and the decision follows the numbers.

### Option 5: stock compression, with equivalence by serializable form

**How it works.** The compressing extensions keep using stock deflate. Files are compared by serializable form,
or, where raw bytes are wanted, by their uncompressed counterparts (F2). Each compressing extension also exposes
its decompressed payload, so that `FileSerializer` can compare payloads before rewriting a file (F2).

**Add-ons:**

- **5a. A module index of content digests.** One uncompressed file per module lists every PELT file with the
  versions that wrote it and the content digest of its serializable form, plus a digest over the list. The index
  is byte-identical across environments, so two builds of a module compare with one digest. It is a new file that
  old loaders ignore, so no format change is needed. Module metadata files then need content digests too, which
  the digest encoding gives them under their own prefix (F0).
- **5b. A content digest in each file header.** That needs new extension versions, and 5a gives the same benefit
  without them, so 5b is not recommended.

**Benefits:**

- The lowest cost: stock deflate in every language.
- No format change.
- Size and speed unchanged.

**Costs:**

- **It gives up byte identity.** Raw files still differ between environments, so generic tools see differences
  that do not matter.
- Every comparison needs a PELT-aware step: decoding files, or decompressing them through their extensions.

## 4. Comparison

### What each option achieves

| Goal | 1. Canonical encoder | 2. Pinned zlib | 3. Compact format | 4. Container compression | 5. Stock + serializable form |
|---|---|---|---|---|---|
| G1 Load anywhere | Yes | Yes | Yes | Yes | Yes |
| G2 Test by byte comparison | Yes | Yes | Yes | Yes | Yes, of uncompressed counterparts |
| G3 Digests mean the same | Yes | Yes | Yes | Yes | Yes |
| G4 Generic tools on raw files | Yes | Yes | Yes | Yes | No; with 5a, one index file per module |
| G5 No spurious rewrites | Yes | Yes | Yes | Yes | If `FileSerializer` compares decompressed payloads before rewriting |
| G6 Size and load speed | Close to today, and can be tuned for a better ratio; measure | Same as today | Unknown: likely larger, faster to decode | Expected losses in directories and jars; measure | Same as today |
| G7 Cost per new language | An encoder of about 1,000 lines | Vendored zlib through FFI, or a port | No compressor, but a new format | None | None |
| G8 Forward compatibility | Yes | Yes | No | Yes | Yes (5b: no) |
| G9 Test a non-Java compiler | Yes | Yes | Yes | Yes | Yes, of uncompressed counterparts |

### What each option costs

| Option | Specification | Java work | Format change | Ongoing burden | Main risk |
|---|---|---|---|---|---|
| 1. Canonical encoder | A few pages, plus vectors | An encoder, tests, and fuzzing | None | A frozen encoder; each writing language implements it | Ratio or build-time shortfall |
| 2. Pinned zlib | The version and settings | A bit-exact port, or JNI with native libraries | None | Port fidelity; frozen at one zlib | Port effort and fidelity |
| 3. Compact format | A new format | A new serializer and deserializer | New versions | Two formats during the transition | May not match deflate's size |
| 4. Container compression | None | Switch the default versions | None (existing versions) | None | Size and load regression |
| 5. Stock + serializable form | None beyond the foundations | A default method on two extension interfaces, and its use in `FileSerializer` | None (5b: new versions) | Comparisons need PELT-aware tools | Generic tools see false differences |

### What true byte identity adds over Option 5

- **Generic tools (G4).** Content-addressed caches, artifact deduplication, reproducibility checks, and plain
  `diff` or `sha256sum` work on PELT files directly.
- **One notion of equality** for files written with the same versions. There is no decompression step in tests,
  and a mismatch is visible with standard tools. Decompression remains only as a diagnostic.
- **No version-dependent churn (G5)** without any change to write-if-modified.

Option 5 meets G1 and G3 equally well, and G2 and G9 by comparing uncompressed counterparts rather than files. For
G9, what matters most is F3, not the compression choice. What Option 5 gives up is byte identity itself, and with
it the three benefits above. Whether they are worth more than today's compression is what the gate in
[section 6](#6-recommendation) decides.

## 5. Other details a specification has to pin down

- **String order and length.** Defined on UTF-16 code units, matching `String.compareTo` and `String.length()`.
  Non-Java implementations must convert or compare accordingly.
- **Unpaired surrogates.** Writers should reject them with an error naming the element and property. Java's
  current behavior writes `?` and loses the value. First, scan existing models to confirm none occur. The scan has
  to look at sources or compiled graphs, since existing binaries already hold `?` in their place.
- **Values in the serializable form.** It keeps everything that an encoding keeps (F0):
  - **Float and Decimal.** V1 writes a sign and digit strings taken from the instance name (`BaseV1.java:175`),
    but `Value` holds a `BigDecimal` (`Value.java:318` and `:338`), which loses the sign of a negative zero and
    any leading zeros. If such names occur, files that differ decode to equal serializable forms. The serializable
    form keeps the sign and the digit strings, and compares by them; the `BigDecimal` stays available.
  - **Integers** are values, whatever Java type holds them. `Value` compares them by Java type, so an `Integer` 5
    differs from a `Long` 5, and V1 chooses between its two integer encodings by Java type too (`BaseV1.java:222`).
    Both are consistent today, because `ModelRepository` and the deserializer choose types the same way. The
    specification states the rules by value, as other languages need, and `Value` and the encoder follow them.
  - **Compile states** are a set of named states. The bitset, which depends on the order of the `CompileState`
    enum, is V1's encoding of that set.
- **File names.** The 126-unit limit applies to each path segment, extension included. A longer segment is cut so
  that the kept part, a 13-character hash suffix, and the extension fill exactly 126 units, except that the cut
  moves back one unit rather than split a surrogate pair. The hash runs over the code points of the part cut off.
  Starting from zero, for each code point, the hash applies Eclipse Collections' `SpreadFunctions.longSpreadOne`
  to the running value and adds the code point. `longSpreadOne` is five operations on a 64-bit value: three
  xor-shifts and two multiplications by constants. Specify it exactly, with test vectors, and credit Eclipse
  Collections (EPL-1.0 and EDL-1.0) as its source; base-32 digits `0-9a-v` (`Long.toUnsignedString(x, 32)`);
  left-padded with `0` to 13 characters.
- **Keeping the file-name hash.** `SpreadFunctions` does not document its output as stable, so two writers with
  different Eclipse Collections versions could name the same file differently. A copy of `longSpreadOne` in
  `FilePathTools`, with tests that pin its output, removes that risk along with the runtime dependency; only the
  credit remains. Specifying a hash of our own was considered instead, and rejected:
  - **Hashed names already exist.** legend-pure's own `platform_dsl_mapping` module has one, for the function
    `propertyMappingByPropertyNameAndTargetIdWithInlineResolution`. A function's element name includes its
    parameter and return types, so any module can produce one.
  - **A new hash would rename those files.** Readers find files by computing their names, and no file records which
    path provider version wrote it, so readers would fail on renamed files partway through loading. With the
    [path provider and reference ID versions plan](2026-10-16-pelt-path-provider-and-reference-id-versions.md),
    new readers would find files on both sides of the change, but every earlier reader would reject each module
    with a hashed name, the mapping DSL among them.
  - **Little would be gained.** `longSpreadOne` is already simple to implement in any language.
- **Case-only differences.** Element paths that differ only in letter case, such as `a::B` and `a::b`, map to
  colliding file names on case-insensitive file systems, the default on Windows and macOS. When serializing to a
  directory, the later write silently replaces the earlier file. A build-time error on every platform is the
  simplest fix. Such models build today on case-sensitive file systems, so first scan existing models for any that
  the error would break.
- **Constants.** File signatures (for example `Long.parseLong("PureManifest", 36)`) and version numbers, listed as
  numbers.
- **Special strings.** The special-string table in `BaseStringIndex` (`SPECIAL_STRING_IDS`), listed in full.
- **Reference IDs.** Both the syntax and the generation rules (`ReferenceIdGenerator`) are normative, because a
  second compiler must generate the same IDs. For a serializer that starts from serializable forms (F0), reference
  IDs are data.
- **Graph-to-content rules and source information.** Normative, as listed in F3.

## 6. Recommendation

**Restore byte identity, after building the foundations that every option needs, through the cheapest option that
measurement confirms. Option 1 is the likely one, but Option 4 is measured first.**

1. **Measure Option 4 at once.** It needs no new code, since its versions already exist. Phase 4 measures it
   against today's level-7 deflate, together with the jar investigation and the byte breakdown that Option 3
   needs. This runs in parallel with the foundations, and if Option 4 is within the tolerances, no encoder is
   built.
2. **Build F0, F1, and F2, and check the compiler's determinism (F3).** They are required whatever the encoder
   decision. Together they already meet G1 and G3, and G2 by comparing uncompressed counterparts. That is Option 5
   without its one addition for G5: the interim state while the encoder is decided, and the baseline that the
   measurements compare against. G9 also needs F3's written rules, which follow in Phase 6.
3. **Prototype Option 1 only if Option 4 falls short,** and measure it the same way.
4. **Decision gate.** Each option is held to agreed tolerances on size (for example 2%), serialization time, and
   load time, measured against Option 5. Size is the most important of the three, given the multi-gigabyte jars of
   the largest models.
   - **Adopt Option 4** if it is within the tolerances, after any reader changes that the jar investigation points
     to. Earlier measurements suggest it will fall short, but it is checked first because it costs the least:
     adopting it only switches the default versions to element V1 and metadata V2.
   - **Otherwise, adopt Option 1** if it is within the tolerances.
   - **If it falls short,** tune the encoder first. A deterministic encoder can trade build time for ratio, for
     example with longer chains or better parsing.
   - **If it still falls short,** consider Option 2, which matches today's ratio exactly at a high porting cost.
   - **Otherwise, settle at Option 5,** if keeping today's compression is worth more than byte identity, and the
     goals byte identity serves can be met another way: 5a for one-digest comparisons, and a `FileSerializer` that
     compares decompressed payloads before rewriting a file. The invariant then becomes identical uncompressed
     counterparts.
5. **On adopting Option 1,** the compressing extensions switch encoders (Phase 5), and every Java-produced PELT
   becomes byte-identical across environments. Whether non-Java writers use the encoder from the start, or stock
   deflate until they publish PELTs, is [decision 4](#9-decisions-to-make).
6. **Option 3** is a separate format project, worth starting only if the measurements show that inflate is a large
   share of load time, or that a structural encoding captures most of deflate's gain.
7. **Option 4 depends on the jar investigation.** Phase 4 investigates why compression helps inside jars. If that
   gain turns out to come mostly from the I/O path, a reader change might recover it, and Option 4 becomes much
   more attractive.

## 7. Phases

### Phase 0: specification of the serializable form and the encoding

Write `docs/reference/pelt-format.md` as a normative description of the current versions, as implemented:

1. the serializable forms (F0): of elements, with the value rules in section 5, and of module metadata; how they
   grow; the digest encoding; and a text encoding of each, which the corpus uses (Phase 3),
2. primitives and the string index (V3),
3. each serializer extension: its payload format; whether and how it compresses; and, if it compresses, its
   uncompressed counterpart and its envelope rule (F2),
4. file layout (path provider V1), including the file-name hash,
5. reference ID syntax (V1).

The rest of F3, including how reference IDs are generated, is what a compiler must follow to generate these
serializable forms. It comes in Phase 6.

Exit: someone can write a decoder, an encoder, and the content digest from the document alone. Phase 6 tests that.

### Phase 1: serializable-form tooling (F0, F2)

Phase 2 compares builds with these tools, so they come first.

- **Rename `DeserializedConcreteElement` to `SerializableConcreteElement`,** first, so that these tools are
  written against the new name (see section 8). Its javadoc says that it is the form an element is serialized in,
  since the name could be read as a kind of element.
- **The value fixes in the serializable form** (section 5): Float and Decimal values keep their sign and digit
  strings, and integers compare by value. Comparisons of serializable forms rely on them, and so does Phase 2's
  encoder.
- **A utility** that dispatches on signature and version to decode files and compute content digests. It needs no
  new dependencies.
- **Envelope validation** for the compressing extensions (F2), in their tests and their own tooling.
- **A comparison tool** that takes two directories or jars and reports PELT files that are missing on one side, or
  whose bytes differ. For those, it compares versions and serializable forms, and reports whether the content
  differs or only its encoding.
- **A structural diff** of serializable forms, optionally together with back references from other elements. For
  two files that differ, the tool decodes both and reports the first differences by element, node (graph path),
  property, and value. It needs only the deserializers, so it works on files from the start, and on compiled
  graphs once the generator exists (Phase 2). This is what makes byte comparison useful for debugging a second
  compiler.

Exit: two builds can be compared by serializable form in any environment, whatever format versions wrote them.

### Phase 2: the generator, and canonical uncompressed payloads in Java (F0, F1)

- **Split `SerializerV1` into a generator and an encoder** (F0).
  - The generator computes an element's serializable form from the compiled graph and a reference ID provider,
    following F3's rules, and, when asked, its back references from other elements alongside it. It is public, and
    it belongs to no element version, so every version encodes the same serializable form.
  - It returns the classes that the deserializer returns (`SerializableConcreteElement` and the others), so one
    form serves both directions. Module metadata already has its serializable form (`ModuleManifest` and the
    others).
  - The encoder is what the specification describes and what other languages implement. The serialize method of
    `ConcreteElementSerializerExtension` takes a serializable form, as its deserialize method returns one. The
    method that takes an element becomes a deprecated default, retained temporarily, that generates the form and
    encodes it. A default for the new method could only throw, so the new method is abstract instead: only
    `ConcreteElementSerializerV1` and `ConcreteElementSerializerV2` implement the interface, and legend-engine
    neither references it nor registers an implementation. `ConcreteElementSerializer` keeps its methods that take
    an element, and gains methods that take a serializable form.
  - The encoder depends on Phase 1's value fixes. `SerializerV1` takes Float and Decimal digit strings from
    instance names (section 5), so an encoder given values without them would change the bytes of any element with
    a negative zero or leading zeros.
  - Measure serialization time before and after the split, on Phase 4's module sets. The write side then holds
    each element's serializable form in memory, as the read side already does.
- **Add round-trip tests.** For every file in builds of a fixed set of modules (platform, one DSL module, and
  relational, as in Phase 3), decode, encode again with the same versions, and compare the result with the file.
  Stock deflate is repeatable in one environment (section 1.2), so this works for every file that a test build
  writes, and it proves that the encoding, envelope included, is a function of the serializable form alone. For
  element files, also check that the serializable form decoded from each file equals the one generated from the
  graph.
- **Check the compiler's determinism.** Compare content digests, which depend only on the compiled graph, and the
  files of builds written with element V1 and metadata V2, which add the encoding (F2), across two kinds of builds
  of the same modules. Content digests can be computed in memory, and where they differ, the structural diff shows
  where. Builds with those versions compare with `sha256sum` and `diff`, and Phase 4 measures the same builds for
  Option 4:
  - *repeated builds in one environment,* which catch run-to-run variation, such as iteration over identity-hashed
    collections;
  - *builds across Linux, Windows, and macOS with JDK 11, 17, 21, and 25,* which catch dependence on the
    environment, such as file listing order.

  Any difference goes to a separate plan for fixing the compiler.
- **Reject unpaired surrogates and case-only path collisions,** each after its scan of existing models (see
  [section 5](#5-other-details-a-specification-has-to-pin-down)).
- **Copy `longSpreadOne` into `FilePathTools`,** crediting Eclipse Collections, with tests that pin today's file
  names (see [section 5](#5-other-details-a-specification-has-to-pin-down)). No file name changes.

Exit: serializable forms, and builds written with element V1 and metadata V2, are identical across repeated builds
and across the matrix, or every difference is recorded for the compiler plan.

### Phase 3: conformance corpus, first version

- **Content:** platform or a slice of it, one DSL module, relational, and synthetic edge cases:
  - non-ASCII and supplementary characters in strings and names,
  - names long enough to trigger file-name hashing,
  - big integers, decimals, and every date type,
  - empty and large collections,
  - string indexes large enough to need 1-, 2-, 3-, and 4-byte IDs,
  - large node counts.
- **Each case provides:**
  - the Pure sources it was compiled from,
  - the compiled files,
  - their uncompressed counterparts, as element V1 and metadata V2 write them (F2),
  - their serializable forms, in the text encoding (Phase 0),
  - their content digests.
- **Packaging:** a versioned zip, published as a Maven artifact (for example `legend-pure-pelt-conformance`) and
  downloadable for non-Maven CI. Versions follow legend-pure releases, since the Java compiler's output changes
  between releases.
- **Licensing:** the corpus redistributes Pure sources and the files compiled from them, so it is licensed under
  Apache 2.0. The zip carries its own LICENSE and NOTICE, since it travels without the POM. It contains only
  Apache-licensed sources; proprietary models never enter it, even when Phase 4 measures them.
- **Tests every implementation runs,** where `uncompressed` is a file's uncompressed counterpart, `dump` its
  serializable form in the text encoding, and `digest` its content digest. `encode` writes with the uncompressed
  versions, and `counterpart` and `validate` belong to the compressing extension under test:

  | Test | Checks |
  |---|---|
  | `decode(file) == dump` | The reader, decompression included |
  | `contentDigest(dump) == digest` | The digest encoding |
  | `counterpart(file) == uncompressed` | Decompression |
  | `encode(dump) == uncompressed` | The writer's uncompressed output |
  | `counterpart(write(dump)) == uncompressed` | The writer, end to end |
  | `validate(write(dump))` | The compressing extension's envelope rule |

  A compiler additionally runs `generate(compile(sources)) == dump` for every file of each case, and, once it has
  an encoder, `compile(sources) == uncompressed` for every file, written with the uncompressed versions. The
  second becomes `compile(sources) == file` once the canonical encoder is adopted. Platform's own sources are the
  largest single test.

### Phase 4: measurements, encoder prototype, and decision gate

The measurements need no new product code, so they start at once, in parallel with Phase 0. Only the encoder
prototype waits, and it is built only if Option 4 falls short.

- **Choose module sets:** platform, the legend-engine module set, and at least one of the largest models.
- **Measure today's variants on those sets, in directories and in jars:**

  | Variant | What it represents |
  |---|---|
  | Uncompressed: element V1 and metadata V2 | Option 4 |
  | Level-7 deflate | Today, and Option 5 (the baseline) |

  For each, record size, serialization time, and load time.
- **Use the jar settings that production builds use** (compression level and entry method), and record them.
- **Attribute the gain inside jars.** Find out why PELT-level compression helps when entries are already deflated:
  from the I/O path (number and size of reads, buffering), or from compression itself.
- **For Option 3,** record a breakdown of V1's bytes by category: string index, node headers, property headers,
  values, and source information.
- **Decide on Option 4** according to [section 6](#6-recommendation).
- **Only if Option 4 falls short, prototype the canonical encoder.** Write its specification and a Java prototype.
  Test it with fixed vectors and fuzzing against stock inflaters (Java, zlib, and one non-Java inflater). Vectors:
  empty input, one byte, long runs of a single byte, random data, input longer than the window, and input longer
  than one block. Measure it on the same sets, at a level-7-like setting and at a higher-ratio setting, and decide
  according to section 6.

### Phase 5: adopting the canonical encoder (if the gate chooses Option 1)

- **The compressing extensions switch encoders.** That covers `ConcreteElementSerializerV2`,
  `ModuleMetadataSerializerV3`, and any other extension that compresses.
- **The corpus's compiled files come from the canonical encoder,** and every implementation's tests add
  `write(dump) == file`.
- **A canonicality check** decodes a file, encodes its serializable form again, and compares the result with the
  file. That checks the envelope rule as well.
- **`FileSerializer` keeps comparing bytes** before rewriting a file. The first build after the switch rewrites
  each compressed file once, so output becomes byte-identical without a clean build.

### Phase 6: rules for compilers, and a second implementation

This phase starts the non-Java work, and can be scheduled with it once Phase 3's corpus exists. It is not on the
critical path for the invariant.

- **Rules for compilers.** Extend Phase 0's document with the rest of F3, all normative:
  - reference IDs: the generation rules;
  - graph-to-content rules: element membership, ordering, back-reference properties, stubs, and compile states;
  - module metadata derivation;
  - source information.

  Exit: the document says exactly what a compiler must produce.
- **A second implementation.** Write a reader and a writer in one non-Java language; Rust is a good first choice.
  It passes the corpus. Under Option 1, [decision 4](#9-decisions-to-make) settles whether it starts with the
  canonical encoder or with stock deflate.
- Cross-check in CI: each implementation loads the other's files.
- Spec gaps go back into Phase 0's document.
- A non-Java compiler, when one exists, builds on this reader and writer, and on the rules above. It is tested by
  compiling the corpus sources and comparing its serializable forms with the corpus's, and its binaries with the
  expected files, using the structural diff to locate differences.

### Phase 7: keeping it true

- **The invariant holds from here on:** byte identity, or identical uncompressed counterparts if the plan settles
  at Option 5. A change that breaks it is a bug, and the checks below catch it.
- **Any format change, new version, or new kind of PELT file** comes with its specification, the serializable form
  it encodes or any additions to it, the uncompressed counterpart of any file it compresses (F2), and its corpus
  entries, in the same PR. Under byte identity, a new compressing extension uses a specified, frozen encoder, as
  in Option 1.
- **The Java build runs the corpus tests,** and the determinism checks keep running.

## 8. Backward compatibility

- **Every option** keeps reading existing binaries. They can be compared with new files by serializable form.
- **Options 1, 2, 4, and 5** change no file format, so earlier readers read new files. Option 5a adds a new file
  that they ignore.
- **Options 3 and 5b** need new extension versions, which earlier readers cannot read.
- **The element extension interface changes shape** (Phase 2). Its serialize method takes a serializable form, and
  the method that takes an element becomes a deprecated default that generates the form and encodes it. That would
  break an implementation outside legend-pure, but there is none: only `ConcreteElementSerializerV1` and
  `ConcreteElementSerializerV2` implement the interface, and legend-engine neither references it nor registers an
  implementation. Callers of `ConcreteElementSerializer` are unaffected.
- **The serializable form** reuses the classes that the deserializers return today, with
  `DeserializedConcreteElement` renamed `SerializableConcreteElement`. legend-engine's sources do not use that
  class. Generated code names it only in an erased `Supplier<? extends DeserializedConcreteElement>` constructor
  parameter, which it passes straight to its superclass (`ClassPeltImplProcessor.java:416`,
  `M3LazyCoreInstanceGenerator.java:481`), so generated code that is already compiled keeps linking.
  `InstanceData` keeps its name, since it appears unerased in generated constructors
  (`ClassPeltImplProcessor.java:438`).
- **The value fixes** keep `Value`'s methods and change its equality only in the cases that section 5 lists.
- **Comparisons by serializable form, and content digests,** change no file.
- **Option 5's method for decompressed payloads,** added only if the plan settles there, has a default, so the
  interfaces stay compatible.
- **The file-name hash does not change,** so no file name changes. The copy of `longSpreadOne` in `FilePathTools`
  is tested to give today's names (see [section 5](#5-other-details-a-specification-has-to-pin-down)).
- **If a Phase 2 fix changes an uncompressed format,** it gets a new version through the existing extension
  mechanism, and readers keep the old version.
- **Builds** place no new requirement on dependencies, under any option.

## 9. Decisions to make

1. **The outcome.** Restore byte identity through the gate in section 6 (recommended): Option 4 if it is within
   the tolerances, otherwise Option 1, with Option 2 if Option 1 falls short. Or settle at Option 5 if byte
   identity costs more than it is worth.
2. **Gate tolerances:** how much size, serialization time, and load time byte identity is worth, measured against
   Option 5. Size is the most important of the three.
3. **Marking canonical files with a new extension version.** Not recommended: canonicality can be checked by
   re-encoding, and avoiding a new version keeps forward compatibility. The price is that the encoder is frozen,
   and any revision comes with a new version.
4. **Non-Java writers under Option 1:** require the canonical encoder from the start, or allow stock deflate until
   they publish PELTs.
5. **The module index of content digests (5a):** needed if the plan settles at Option 5, useful in the interim,
   and unnecessary once byte identity is restored, since raw files then work with generic tools.
6. **Unpaired surrogates:** reject them (recommended), or encode them, for example as WTF-8, which is a format
   change.
7. **Case-only path collisions:** a build-time error (recommended), or file-name escaping in a new path provider
   version. Escaping renames every file it applies to, which depending on the scheme could be nearly all of them,
   and neither earlier nor later readers would find those files across the change: readers compute file names with
   the default path provider version, and nothing records which version wrote a module. The
   [path provider and reference ID versions plan](2026-10-16-pelt-path-provider-and-reference-id-versions.md)
   fixes this for later readers. Earlier readers would then reject each renamed module when they load its
   manifest.
8. **Where the corpus lives,** and its size budget.
9. **Which non-Java language comes first.**
10. **The digest encoding:** a small encoding defined on the serializable form (recommended), or an existing
    payload, such as element V1's with its string index version pinned, which takes two passes and cannot grow
    (F0).
11. **Float and Decimal in the serializable form:** the sign and digit strings as written (recommended), or
    numeric values, with every encoder writing canonical digit strings. The second changes the bytes of any
    element with a negative zero or leading zeros in such a value.

## Outside this plan

- **Fixing compiler nondeterminism.** Phase 2 detects any. Fixes belong in a separate plan.
- **Double compression in jars.** Unless the packaging tool is configured otherwise, PELT entries are deflated a
  second time when packaged, for little gain. Storing them uncompressed (`STORED`) in jars would avoid that work at
  build and load time. Measure before changing.
- **Byte-identical jars across environments** are out of reach from the PELT side alone. Jar bytes also depend on
  the JDK through compiled class files and the jar's own entry compression. PELT-level identity is the realistic
  scope.
- **`.par` archives** hold the older binary format and are not PELT files.
  [ADR-007](../decisions/ADR-007-write-if-modified-for-generated-output.md) made them repeatable in one environment
  by fixing their entry timestamps, but the JDK's zlib still compresses their entries, so
  [section 1.2](#12-compressed-bytes-break-the-invariant) applies to them as well.
- **Other uses of the serializable form.** Tests that compare compiled states could compare serializable forms
  instead, together with back references from other elements. For example, `RuntimeVerifier`'s stability check
  compares instance counts and the length of the serialized repository (`RuntimeVerifier.java:267`). Diffs of
  serializable forms between legend-pure releases could also help review compiler changes, going by what reference
  IDs name if a release changes the reference ID version (F0).
