# Plan: byte-identical PELT files

Status: proposal, 2026-10-15.

## Goal

PELT serialization is meant to be deterministic: every PELT file is a function of its logical content alone (see
[F0](#f0-the-content-model)). Two writers that serialize the same content produce the same bytes, whatever the OS,
JDK, CPU, zlib build, or programming language. That has always been the intent. Where it does not hold today, the
existing system has a bug, and this plan addresses it. Afterward, other work can rely on the result as an invariant.

One purpose stands out: a Pure compiler written in a language other than Java can be tested by compiling the same
sources and comparing its binaries with those from the Java compiler. So the rules that turn a compiled graph into
PELT content must be specified as precisely as the encoding itself (see
[F3](#f3-graph-to-content-rules-for-a-second-compiler)).

The main violation is compression ([section 1.2](#12-compressed-bytes-break-the-invariant)). There are several ways
to fix it, at different costs. A cheaper alternative, Option 5, keeps today's compression and delivers most of the
benefits through a weaker invariant: identical content, compared by canonical form. It is the baseline for what
byte identity costs, and it could be the outcome if today's compression proves worth more than byte identity. This
plan:

- lays out the goals,
- defines a file's content as a model, which comparisons and digests can use apart from any encoding,
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

### F0. The content model

Each kind of PELT file holds logical content, or simply content, and each version of its extension encodes that
content in its own way. The content model is that content as data, apart from any encoding. Most of it exists
already:

- **Element files.** Every element extension's deserializer returns a `DeserializedConcreteElement`: the element's
  path, its reference ID version, and its nodes in order. Each node (`InstanceData`) has its name, classifier,
  source information, reference ID, compile states, and property values, each a primitive value or an internal or
  external reference. `ElementBuilder` builds instances from it (`ElementBuilder.java:39`).
- **Module metadata.** `ModuleManifest` and the other metadata classes are its object model.

The write side is the gap. `SerializerV1` computes the same content from the graph, but only on its way to bytes:
it keeps a list of nodes and their internal IDs, and reads each value from the graph as it writes it
(`SerializerV1.java:63` and `:241`). Phase 2 splits it into a *generator*, which computes an element's content from
the compiled graph and a reference ID provider by F3's rules, and an *encoder* for each version. Serializing is
then generating and encoding, and deserializing is decoding.

**Rules:**

- **Each uncompressed payload is a function of content** (F1), and decoding a file gives back the content it was
  encoded from.
- **The model keeps everything that any encoding keeps,** or files that differ would decode to equal content.
  Section 5 lists the values for which today's classes fall short.
- **An encoder rejects content that its version cannot represent.** It never drops any.
- **For files written with the same versions, content is equal exactly when canonical forms are** (F2). Phase 2's
  round-trip tests check this.

**Uses:**

- **Content equivalence.** Two files have the same content when they decode to equal content, whatever versions
  wrote them (F2).
- **Comparisons without files.** Content generated from two compiled graphs, or from a graph and a file, can be
  compared directly, and the structural diff (Phase 1) shows where they differ.
- **Projections.** A comparison or a digest may set parts of the content aside, such as source information. A
  projection is defined on the model, never on bytes.
- **Content digests,** which agree whatever versions wrote a file.

**The content digest** of an element file is SHA-256 over the ASCII bytes `pelt-content-1`, a zero byte, and the
element's content in the *digest encoding*. The digest encoding is part of the specification (Phase 0). It writes
each part of the content in a fixed order: strings as UTF-8, each preceded by its length, and each value with a tag
for its kind. It has no string index and no compression, and it is never written to a file. A digest of a
projection uses the same encoding under its own prefix, as would a digest of another kind of file's content, once
something needs one.

**Why not an existing payload,** such as element V1's:

- **It is fixed only in part.** It starts with a string index that records its own version
  (`StringIndexer.java:46`). That version is chosen apart from the element version: versions 0 to 3 are
  registered, and the newest is the default.
- **It takes two passes,** since its string index comes first.
- **It cannot grow.** It has no room for a new kind of value: all eight of its node-type codes are taken
  (`BaseV1.java:45`).

The digest encoding can grow with the model. A new kind of value gets a new tag, which changes no existing digest.
Any other change to the encoding changes the prefix of every digest that uses it.

### F1. Canonical uncompressed payloads

Every option needs each extension's uncompressed payload to be a function of its content (F0) alone, specified well
enough to implement elsewhere. That means:

- the specification (Phase 0);
- the fixes in [section 5](#5-other-details-a-specification-has-to-pin-down);
- the verification in Phase 2, across repeated builds and across environments.

With F1 in place, compression is the only remaining source of difference, and it lives inside the compressing
extensions.

### F2. A canonical form, owned by each serializer extension

Comparing files without true byte identity needs a canonical form. Each extension defines its own, so compression
stays internal:

- **An extension that compresses** defines its canonical form as the payload decompressed. It also states its own
  envelope rules, which a validator checks. For element V2 and metadata V3, the payload must be exactly one raw
  deflate stream (RFC 1951) ending exactly at the end of the file, with no trailing bytes. Readers stop at the end
  of the deflate stream and would not notice trailing bytes otherwise.
- **An extension that does not compress** uses its payload as is.

At the top level:

- **Equivalent files** have the same header (signature, version, and any other header fields) and the same
  canonical form, as defined by the extension for that version.
- **The canonical digest** of a file is SHA-256 over the ASCII bytes `pelt-canonical-1`, a zero byte, the header,
  and the canonical form.

**In code:** a default method on each serializer extension interface (today `ConcreteElementSerializerExtension`
and `ModuleMetadataSerializerExtension`) produces the canonical form, returning the payload unchanged by default.
The default keeps the interfaces compatible. The compressing extensions override it, and any interface added later
for a new kind of PELT file gets the same method. Tools dispatch on signature and version, as reading already does.

**Across versions, compare content.** Files written with different versions are never equivalent, and neither are
files whose payloads record different string index versions. An element V1 file and a V2 file with the same payload
compare as different. Whether such files hold the same content is a question for F0: they do when they decode to
equal content. That needs no rule at the top level, since each extension already decodes its own files.

**F2 is needed even with true byte identity:**

- When two files differ, comparing their canonical forms, and then their content, tells you whether the
  compression, the rest of the encoding, or the content diverged.
- Existing binaries, and any writer not yet using a canonical encoder, can still be compared.

**Digests of content,** as opposed to digests of whole files, are content digests (F0), never computed over a
file's bytes, compressed or not. They then agree whatever versions wrote the files, and even where files are not
byte-identical, as with existing binaries.

### F3. Graph-to-content rules, for a second compiler

A serializer that starts from content (F0) needs only the encoding rules. A second compiler starts from source, so
for its binaries to match Java's, it must generate the same content: the compiled graph must match, and every rule
between that graph and the content must be specified.

**The compiled graph.** It must be identical, and that includes source information. Specifically:

- inferred generic types, resolved function applications, and generated properties (for example for
  milestoning), with values in the same order;
- source information: line and column numbering, column units for non-ASCII text (UTF-16 code units or code
  points), tab handling, and exactly which positions start and end columns denote.

Apart from back-reference properties, the compiled graph must be a function of the source alone.

**Generation rules,** from graph to content:

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
comparing canonical forms or content.

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

### Option 5: stock compression, with equivalence by canonical form

**How it works.** The compressing extensions keep using stock deflate. Equivalence is defined by F2, and tools
compare canonical forms. This is what F2 delivers on its own.

**Add-ons:**

- **5a. A module index of canonical digests.** One uncompressed file per module lists every PELT file with its
  canonical digest, plus a digest over the list. The index is byte-identical across environments, so two builds of
  a module compare with one digest. It is a new file that old loaders ignore, so no format change is needed.
- **5b. A canonical digest in each file header.** That needs new extension versions, and 5a gives the same benefit
  without them, so 5b is not recommended.

**Benefits:**

- The lowest cost: stock deflate in every language.
- No format change.
- Size and speed unchanged.

**Costs:**

- **It gives up byte identity.** Raw files still differ between environments, so generic tools see differences
  that do not matter.
- Every comparison needs a PELT-aware step that asks the extension for the canonical form.

## 4. Comparison

### What each option achieves

| Goal | 1. Canonical encoder | 2. Pinned zlib | 3. Compact format | 4. Container compression | 5. Stock + canonical form |
|---|---|---|---|---|---|
| G1 Load anywhere | Yes | Yes | Yes | Yes | Yes |
| G2 Test by byte comparison | Yes | Yes | Yes | Yes | Yes, of canonical forms |
| G3 Digests mean the same | Yes | Yes | Yes | Yes | Yes |
| G4 Generic tools on raw files | Yes | Yes | Yes | Yes | No; with 5a, one index file per module |
| G5 No spurious rewrites | Yes | Yes | Yes | Yes | If `FileSerializer` compares canonical forms before rewriting |
| G6 Size and load speed | Close to today, and can be tuned for a better ratio; measure | Same as today | Unknown: likely larger, faster to decode | Expected losses in directories and jars; measure | Same as today |
| G7 Cost per new language | An encoder of about 1,000 lines | Vendored zlib through FFI, or a port | No compressor, but a new format | None | None |
| G8 Forward compatibility | Yes | Yes | No | Yes | Yes (5b: no) |
| G9 Test a non-Java compiler | Yes | Yes | Yes | Yes | Yes, of canonical forms |

### What each option costs

| Option | Specification | Java work | Format change | Ongoing burden | Main risk |
|---|---|---|---|---|---|
| 1. Canonical encoder | A few pages, plus vectors | An encoder, tests, and fuzzing | None | A frozen encoder; each writing language implements it | Ratio or build-time shortfall |
| 2. Pinned zlib | The version and settings | A bit-exact port, or JNI with native libraries | None | Port fidelity; frozen at one zlib | Port effort and fidelity |
| 3. Compact format | A new format | A new serializer and deserializer | New versions | Two formats during the transition | May not match deflate's size |
| 4. Container compression | None | Switch the default versions | None (existing versions) | None | Size and load regression |
| 5. Stock + canonical form | F2 only (small) | Default methods on two extension interfaces, and a utility | None (5b: new versions) | Tools must decompress to compare | Generic tools see false differences |

### What true byte identity adds over Option 5

- **Generic tools (G4).** Content-addressed caches, artifact deduplication, reproducibility checks, and plain
  `diff` or `sha256sum` work on PELT files directly.
- **One notion of equality** for files written with the same versions. There is no decompression step in tests,
  and a mismatch is visible with standard tools. Canonical forms remain only as a diagnostic.
- **No version-dependent churn (G5)** without any change to write-if-modified.

Option 5 meets G1 and G3 equally well, and G2 and G9 by comparing canonical forms rather than files. For G9, what
matters most is F3, not the compression choice. What Option 5 gives up is byte identity itself, and with it the
three benefits above. Whether they are worth more than today's compression is what the gate in
[section 6](#6-recommendation) decides.

## 5. Other details a specification has to pin down

- **String order and length.** Defined on UTF-16 code units, matching `String.compareTo` and `String.length()`.
  Non-Java implementations must convert or compare accordingly.
- **Unpaired surrogates.** Writers should reject them with an error naming the element and property. Java's
  current behavior writes `?` and loses the value. First, scan existing models to confirm none occur. The scan has
  to look at sources or compiled graphs, since existing binaries already hold `?` in their place.
- **Values in the content model.** The model keeps everything that an encoding keeps (F0):
  - **Float and Decimal.** V1 writes a sign and digit strings taken from the instance name (`BaseV1.java:175`),
    but `Value` holds a `BigDecimal` (`Value.java:318` and `:338`), which loses the sign of a negative zero and any
    leading zeros. If such names occur, files that differ decode to equal content. The model keeps the sign and
    the digit strings, and compares by them; the `BigDecimal` stays available.
  - **Integers** are content by value. `Value` compares them by Java type, so an `Integer` 5 differs from a
    `Long` 5, and V1 chooses between its two integer encodings by Java type too (`BaseV1.java:222`). Both are
    consistent today, because `ModelRepository` and the deserializer choose types the same way. The specification
    states the rules by value, as other languages need, and `Value` and the encoder follow them.
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
  second compiler must generate the same IDs. For a serializer that starts from content (F0), reference IDs are
  data.
- **Graph-to-content rules and source information.** Normative, as listed in F3.

## 6. Recommendation

**Restore byte identity, after building the foundations that every option needs, through the cheapest option that
measurement confirms. Option 1 is the likely one, but Option 4 is measured first.**

1. **Measure Option 4 at once.** It needs no new code, since its versions already exist. Phase 4 measures it
   against today's level-7 deflate, together with the jar investigation and the byte breakdown that Option 3
   needs. This runs in parallel with the foundations, and if Option 4 is within the tolerances, no encoder is
   built.
2. **Build F0, F1, and F2, and check the compiler's determinism (F3).** They are required whatever the encoder
   decision. Together they already meet G1 and G3, and G2 by comparing canonical forms. Once `FileSerializer`
   compares canonical forms before rewriting a file, they meet G5 too. That is Option 5: the interim state while
   the encoder is decided, and the baseline that the measurements compare against. G9 also needs F3's written
   rules, which follow in Phase 6.
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
     compares canonical forms before rewriting a file. The invariant then becomes identical canonical forms.
5. **On adopting Option 1,** the compressing extensions switch encoders (Phase 5), and every Java-produced PELT
   becomes byte-identical across environments. Whether non-Java writers use the encoder from the start, or stock
   deflate until they publish PELTs, is [decision 4](#9-decisions-to-make).
6. **Option 3** is a separate format project, worth starting only if the measurements show that inflate is a large
   share of load time, or that a structural encoding captures most of deflate's gain.
7. **Option 4 depends on the jar investigation.** Phase 4 investigates why compression helps inside jars. If that
   gain turns out to come mostly from the I/O path, a reader change might recover it, and Option 4 becomes much
   more attractive.

## 7. Phases

### Phase 0: specification of the content and the encoding

Write `docs/reference/pelt-format.md` as a normative description of the current versions, as implemented:

1. the content model of each kind of file (F0), with the value rules in section 5; the digest encoding; and a text
   form of content, which the corpus uses (Phase 3),
2. primitives and the string index (V3),
3. each serializer extension: its payload format; whether and how it compresses; its canonical form; and its
   envelope rules,
4. file layout (path provider V1), including the file-name hash,
5. reference ID syntax (V1).

The rest of F3, including how reference IDs are generated, is what a compiler must follow to generate this
content. It comes in Phase 6.

Exit: someone can write a decoder, an encoder, and the content digest from the document alone. Phase 6 tests that.

### Phase 1: content and canonical-form tooling (F0, F2)

Phase 2 compares builds with these tools, so they come first.

- **Canonical-form methods** on `ConcreteElementSerializerExtension` and `ModuleMetadataSerializerExtension`, with
  defaults for extensions that do not compress, plus the compressing extensions' overrides and envelope
  validation.
- **The value fixes in the content model** (section 5): Float and Decimal values keep their sign and digit strings,
  and integers compare by value. Comparisons of content rely on them.
- **A utility** that dispatches on signature and version to produce canonical forms, canonical digests, and content
  digests. It needs no new dependencies.
- **A comparison tool** that takes two directories or jars and reports PELT files that are missing on one side or
  whose canonical forms differ, or, for files written with different versions, whose content differs.
- **A structural diff** of content. For two files that differ, the tool decodes both and reports the first
  differences by element, node (graph path), property, and value. It needs only the deserializers, so it works on
  files from the start, and on compiled graphs once Phase 2's generator exists. This is what makes byte comparison
  useful for debugging a second compiler.
- **Optional: compare canonical forms before rewriting a file.** `FileSerializer` writes through
  `FileWriteTools.writeIfModified`, a byte-comparing utility in `legend-pure-m4` that the code generators, `.par`
  archives, and PCT reports share. The comparison belongs in `FileSerializer`, since m4 cannot see the serializer
  extensions: a `FileWriteTools` overload takes an equivalence test, and `FileSerializer` passes one that compares
  bytes first and canonical forms only when the bytes differ. If Option 1 is adopted, Phase 5 removes it again.

Exit: two builds can be checked for equivalence in any environment, and builds written with different versions
can be compared by content.

### Phase 2: the generator, and canonical uncompressed payloads in Java (F0, F1)

- **Split `SerializerV1` into a generator and an encoder** (F0).
  - The generator computes an element's content from the compiled graph and a reference ID provider, following
    F3's rules. It is public, and it belongs to no element version, so every version encodes the same content.
  - It returns the classes that the deserializer returns (`DeserializedConcreteElement` and the others), so one
    model serves both directions. Module metadata already has its object model (`ModuleManifest` and the others).
  - The encoder is what the specification describes and what other languages implement.
    `ConcreteElementSerializerExtension` gains a method that encodes content, with a default, so the interface
    stays compatible.
  - Measure serialization time before and after the split, on Phase 4's module sets. The write side then holds
    each element's content in memory, as the read side already does.
- **Add round-trip tests.** For every file in builds of a fixed set of modules (platform, one DSL module, and
  relational, as in Phase 3), decode, re-encode, and compare canonical forms. This proves that the encoding is a
  function of the content alone. For element files, also check that the decoded content equals the content
  generated from the graph, which shows that a file holds exactly that content.
- **Check the compiler's determinism.** Compare content digests, which depend only on the compiled graph, and
  canonical digests, which add the encoding, across two kinds of builds of the same modules. Content digests can
  be computed in memory, and where they differ, the structural diff shows where:
  - *repeated builds in one environment,* which catch run-to-run variation, such as iteration over identity-hashed
    collections;
  - *builds across Linux, Windows, and macOS with JDK 11, 17, 21, and 25,* which catch dependence on the
    environment, such as file listing order.

  Any difference goes to a separate plan for fixing the compiler.
- **Reject unpaired surrogates and case-only path collisions,** each after its scan of existing models (see
  [section 5](#5-other-details-a-specification-has-to-pin-down)).
- **Copy `longSpreadOne` into `FilePathTools`,** crediting Eclipse Collections, with tests that pin today's file
  names (see [section 5](#5-other-details-a-specification-has-to-pin-down)). No file name changes.

Exit: content and canonical forms are identical across repeated builds and across the matrix, or every difference
is recorded for the compiler plan.

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
  - their expected canonical forms,
  - their content, in the text form (Phase 0),
  - their content digests.
- **Packaging:** a versioned zip, published as a Maven artifact (for example `legend-pure-pelt-conformance`) and
  downloadable for non-Maven CI. Versions follow legend-pure releases, since the Java compiler's output changes
  between releases.
- **Licensing:** the corpus redistributes Pure sources and the files compiled from them, so it is licensed under
  Apache 2.0. The zip carries its own LICENSE and NOTICE, since it travels without the POM. It contains only
  Apache-licensed sources; proprietary models never enter it, even when Phase 4 measures them.
- **Tests every implementation runs,** where `expected` is a file's expected canonical form, `dump` its content in
  the text form, and `digest` its content digest:

  | Test | Checks |
  |---|---|
  | `decode(file) == dump` | The reader |
  | `contentDigest(dump) == digest` | The digest encoding |
  | `canonicalForm(file) == expected` | Decompression, and canonical-form construction |
  | `encode(dump) == expected` | The writer's uncompressed output |
  | `canonicalForm(write(dump)) == expected` | The writer, end to end |
  | `validate(write(dump))` | The compressing extension's envelope rules |

  A compiler additionally runs `canonicalForm(compile(sources)) == expected` for every file of each case, and,
  before it has an encoder, `generate(compile(sources)) == dump`. The first becomes `compile(sources) == file` once
  the canonical encoder is adopted. Platform's own sources are the largest single test.

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
- **The corpus gains expected compressed bytes,** and every implementation's tests add `write(dump) == file`.
- **A canonicality check** re-encodes a file's canonical form with the extension's encoder and compares the result
  with the file.
- **`FileSerializer` goes back to comparing bytes** before rewriting a file, if Phase 1 made it compare canonical
  forms. If it kept comparing canonical forms, incremental builds would keep every file whose canonical form is
  unchanged, stock-deflate bytes and all, and output would become byte-identical only after a clean build.
  Comparing bytes instead rewrites each compressed file once.

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
  compiling the corpus sources and comparing its content with the corpus's, and its binaries with the expected
  files, using the structural diff to locate differences.

### Phase 7: keeping it true

- **The invariant holds from here on:** byte identity, or identical canonical forms if the plan settles at
  Option 5. A change that breaks it is a bug, and the checks below catch it.
- **Any format change, new version, or new kind of PELT file** comes with its specification, its content model or
  any additions to it, its canonical-form method, and its corpus entries, in the same PR. Under byte identity, a
  new compressing extension uses a specified, frozen encoder, as in Option 1.
- **The Java build runs the corpus tests,** and the determinism checks keep running.

## 8. Backward compatibility

- **Every option** keeps reading existing binaries. They can be compared with new files of the same version by
  canonical form.
- **Options 1, 2, 4, and 5** change no file format, so earlier readers read new files. Option 5a adds a new file
  that they ignore.
- **Options 3 and 5b** need new extension versions, which earlier readers cannot read.
- **Adding canonical-form methods** to the extension interfaces is compatible, because they have defaults.
- **The content model** reuses the classes that the deserializers return today. The value fixes keep `Value`'s
  methods and change its equality only in the cases that section 5 lists. The method that encodes content has a
  default, like the canonical-form methods.
- **Content equivalence and content digests** change no file.
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
5. **The module index of canonical digests (5a):** needed if the plan settles at Option 5, useful in the interim,
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
10. **The digest encoding:** a small encoding defined on the content model (recommended), or an existing payload,
    such as element V1's with its string index version pinned, which takes two passes and cannot grow (F0).
11. **Float and Decimal content:** the sign and digit strings as written (recommended), or numeric values, with
    every encoder writing canonical digit strings. The second changes the bytes of any element with a negative zero
    or leading zeros in such a value.

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
- **Other uses of the content model.** Tests that compare compiled states could compare content instead. For
  example, `RuntimeVerifier`'s stability check compares instance counts and the length of the serialized
  repository (`RuntimeVerifier.java:267`). A compiled state also includes back references from other elements,
  which module metadata holds, so such a check would compare that metadata too. Content diffs between legend-pure
  releases could also help review compiler changes.
