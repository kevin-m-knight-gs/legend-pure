# Plan: byte-identical PELT files

Status: proposal, 2026-10-15.

## Goal

PELT serialization is meant to be deterministic: every PELT file is a function of its logical content alone. Two
writers that serialize the same content produce the same bytes, whatever the OS, JDK, CPU, zlib build, or
programming language. That has always been the intent. Where it does not hold today, the existing system has a bug,
and this plan addresses it. Afterward, other work can rely on the result as an invariant.

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
- describes the ways to restore byte identity, and the alternative,
- compares their costs and benefits against that baseline,
- recommends a staged path: foundations that every option needs first, then a measured decision on the encoder.

"PELT files" means every file the serialization layer writes: element files (`.pelt`), the module files (`.pmf`,
`.psr`, `.pxr`, `.pbr`, `.pfn`), and any kind added later. Every new kind of PELT file must satisfy the invariant
too (see [Phase 7](#phase-7-keeping-it-true)).

### Goals

| # | Goal | Why it matters |
|---|---|---|
| G1 | Load anywhere | PELTs written in one environment or language load in any other. |
| G2 | Test serializers by byte comparison | A new reader or writer, in any language, is checked against an existing implementation's output. |
| G3 | Digests mean the same everywhere | A digest of a PELT file, or of its content, has the same value in every environment, so anything that keys on or compares digests (caches, deduplication, change detection) gives the same answer everywhere. |
| G4 | Generic tools work on raw files | `sha256sum`, `diff`, content-addressed build caches, artifact deduplication, and reproducibility checks work without knowing anything about PELT. |
| G5 | No spurious rewrites | A JDK or zlib change alone does not change output, so incremental builds and `FileSerializer.writeIfModified` do no needless work. |
| G6 | Size and load speed | At least as good as today, in raw directories and in jars. Size matters especially: the largest models produce jars of many gigabytes. |
| G7 | Low cost per new language | Adding a writer in Rust, C, or another language should be cheap. |
| G8 | Forward compatibility | Readers in earlier legend-pure versions can still read new files. |
| G9 | Test a non-Java compiler against Java's | A compiler in another language compiles the same sources, and its binaries are compared with the Java compiler's. |

Every option keeps backward compatibility: new readers keep reading every existing binary.

Non-goals:

- **Byte-identical jars or zip containers.** See [Outside this plan](#outside-this-plan).
- **Fixing compiler nondeterminism.** If the compiled graph turns out to depend on processing order or the
  environment anywhere other than in back-reference properties, that is a compiler bug for a separate plan. This
  plan only detects it (Phase 1).

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
| String bytes | UTF-8 via `String.getBytes` (`AbstractBinaryReaderWriter.java:56`, `StringWriterV3.java:134`) | Deterministic for valid Unicode. Java silently writes `?` for an unpaired surrogate, and Rust and C strings cannot hold one. |
| String index order | `StringWriterV3.java:307`: sort by `String.length()`, then `String.compareTo` | Deterministic, but both are defined on UTF-16 code units. Sorting by UTF-8 bytes or code points, the natural order in Rust and C, differs for supplementary characters, and lengths differ for all non-ASCII text. |
| String decomposition | `StringWriterV3.processStrings` splits paths, dotted names, and bracketed IDs | A deterministic function of each string, with ASCII delimiters. |
| Module metadata ordering | Sorted by path, name, or ID with `String.compareTo` (`ModuleManifest.java:552`, `ModuleExternalReferenceMetadata.java:177`, `ElementBackReferenceMetadata.java:290`, `ModuleFunctionNameMetadata.java:175`, `ModuleSourceMetadata.java:380`) | Deterministic, with the same UTF-16 caveat. |
| Node order in an element | Breadth-first from the element, properties sorted by name (`SerializerV1.java:544`), values in graph order | Deterministic for a given graph. Back-reference properties, the only ones whose order depends on processing order, are kept out of the element or written sorted (`SerializerV1.java:180` and `:249`). |
| Numbers | Float and Decimal as digit strings from the instance name (`BaseV1.java:175`); Integer as a minimum-width long, or a digit string for BigInteger (`BaseV1.java:219`) | Deterministic. No binary floating-point formatting is involved. |
| Element file names | Element path, truncated past 126 UTF-16 units with a base-32 hash suffix (`FilePathTools.java:162`) | Deterministic, but the hash uses Eclipse Collections' `SpreadFunctions.longSpreadOne` (`FilePathTools.java:205`), which a specification must reproduce exactly. |

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

Within one JVM, the output *is* repeatable, which is all `FileSerializer.writeIfModified` needs today.

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

### F1. Canonical uncompressed payloads

Every option needs each extension's uncompressed payload to be a function of logical content alone, specified well
enough to implement elsewhere. That means:

- the specification (Phase 0);
- the fixes in [section 5](#5-other-details-a-specification-has-to-pin-down);
- the verification in Phase 1, across repeated builds and across environments.

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

**What this gives up:** files written with different versions are never equivalent. An element V1 file and a V2
file with the same payload compare as different. If that is ever needed, an extension that changes only
compression can define its canonical form to match its predecessor's. That stays a decision local to the
extension.

**F2 is needed even with true byte identity:**

- When two files differ, comparing canonical forms tells you whether the content or the encoding diverged.
- Existing binaries, and any writer not yet using a canonical encoder, can still be compared.

**Digests of content,** as opposed to digests of whole files, should be computed over canonical forms. They then
agree even where files are not byte-identical, as with existing binaries.

### F3. Graph-to-content rules, for a second compiler

A serializer that starts from decoded content needs only the encoding rules. A second compiler starts from source,
so for its binaries to match Java's, the compiled graph must match, and every rule between that graph and the
bytes must be specified.

**The compiled graph.** It must be identical, and that includes source information. Specifically:

- inferred generic types, resolved function applications, and generated properties (for example for
  milestoning), with values in the same order;
- source information: line and column numbering, column units for non-ASCII text (UTF-16 code units or code
  points), tab handling, and exactly which positions start and end columns denote.

Apart from back-reference properties, the compiled graph must be a function of the source alone.

**Serialization rules:**

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
comparing canonical forms.

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

1. **Input.** The complete uncompressed payload, compressed in one pass. Element files are small, and module files
   are at most a few megabytes, so buffering is not a problem.
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

**Kept on the table.** Phase 4 measures it alongside Option 1, and the decision follows the numbers.

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
| G4 Generic tools on raw files | Yes | Yes | Yes | Yes, for PELT files | No; with 5a, one index file per module |
| G5 No spurious rewrites | Yes | Yes | Yes | Yes | If `writeIfModified` compares canonical forms |
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
- **One notion of equality.** There is no decompression step in tests, and a mismatch is visible with standard
  tools. Canonical forms remain only as a diagnostic.
- **No version-dependent churn (G5)** without any change to `writeIfModified`.

Option 5 meets G1 to G3 and G9 equally well, and for G9 what matters most is F3, not the compression choice. What
it gives up is byte identity itself, and with it the three benefits above. Whether they are worth more than
today's compression is what the gate in [section 6](#6-recommendation) decides.

## 5. Other details a specification has to pin down

- **String order and length.** Defined on UTF-16 code units, matching `String.compareTo` and `String.length()`.
  Non-Java implementations must convert or compare accordingly.
- **Unpaired surrogates.** Writers should reject them with an error naming the element and property. Java's
  current behavior writes `?` and loses the value. First, scan the existing corpus to confirm none occur.
- **File-name hash.** Specify `SpreadFunctions.longSpreadOne` exactly, with test vectors; base-32 digits `0-9a-v`
  (`Long.toUnsignedString(x, 32)`); left-padded with `0` to 13 characters.
- **Case-only differences.** Element paths that differ only in letter case, such as `a::B` and `a::b`, map to
  colliding file names on case-insensitive file systems, the default on Windows and macOS. When serializing to a
  directory, the later write silently replaces the earlier file. A build-time error is the simplest fix.
- **Constants.** File signatures (for example `Long.parseLong("PureManifest", 36)`) and version numbers, listed as
  numbers.
- **Special strings.** The special-string table in `BaseStringIndex` (`SPECIAL_STRING_IDS`), listed in full.
- **Reference IDs.** Both the syntax and the generation rules (`ReferenceIdGenerator`) are normative, because a
  second compiler must generate the same IDs. For a serializer that starts from decoded content, reference IDs are
  data.
- **Graph-to-content rules and source information.** Normative, as listed in F3.

## 6. Recommendation

**Restore byte identity through Option 1, confirmed by measurement, after building the foundations that every
option needs.**

1. **Build F1, F2, and F3 first.** They are required whatever the encoder decision. Together they already meet G1
   to G3 and G9, and with a `writeIfModified` that compares canonical forms, G5 too. That is Option 5: the interim
   state while the encoder is decided, and the baseline that the measurements compare against.
2. **Prototype Option 1 in Java and measure it, together with Option 4,** against today's level-7 deflate. Phase 4
   lists the measurements. The same run measures what Option 3 needs.
3. **Decision gate.** Size is the most important criterion, given the multi-gigabyte jars of the largest models.
   - **Adopt Option 1** if size is within an agreed tolerance (for example 2%), serialization slows by no more than
     an agreed amount, and load time is unchanged.
   - **If it falls short,** tune the encoder first. A deterministic encoder can trade build time for ratio, for
     example with longer chains or better parsing.
   - **If it still falls short,** consider Option 2, which matches today's ratio exactly at a high porting cost, or
     Option 4, if the measurements and the jar investigation show that its losses are acceptable.
   - **Otherwise, settle at Option 5,** if keeping today's compression is worth more than byte identity, and the
     goals byte identity serves can be met another way: 5a for one-digest comparisons, and a `writeIfModified` that
     compares canonical forms. The invariant then becomes identical canonical forms.
4. **On adoption,** the compressing extensions switch encoders, and every Java-produced PELT becomes
   byte-identical across environments. Non-Java writers implement the encoder when they start publishing PELTs.
   Until then, their output is equivalent by canonical form.
5. **Option 3** is a separate format project, worth starting only if the measurements show that inflate is a large
   share of load time, or that a structural encoding captures most of deflate's gain.
6. **Option 4 stays on the table.** It is the cheapest route to true byte identity, but earlier measurements suggest
   it costs size and load time. Phase 4 quantifies both and investigates why compression helps inside jars. If
   that gain turns out to come mostly from the I/O path, Option 4 becomes much more attractive.

## 7. Phases

### Phase 0: specification

Write `docs/reference/pelt-format.md` as a normative description of the current versions, as implemented:

1. primitives and the string index (V3),
2. each serializer extension: its payload format; whether and how it compresses; its canonical form; and its
   envelope rules,
3. file layout (path provider V1),
4. reference IDs v1: syntax and generation rules, both normative,
5. the rest of F3: graph-to-content rules, module metadata derivation, and source information.

Exit: someone can write a decoder from the document alone, and the document says exactly what a compiler must
produce. Phase 6 tests the first; a second compiler tests the second.

### Phase 1: canonical uncompressed payloads in Java (F1)

- **Split `SerializerV1` into two steps.** First, graph to logical content: node list, property values, and
  reference IDs. Second, logical content to bytes.
  - The deserializer already produces logical content (`InstanceData`), and module metadata already has an object
    model (`ModuleManifest` and the others).
  - The second step is what the specification describes and what other languages implement.
- **Add a round-trip test.** For every file in the corpus, decode, re-encode, and compare canonical forms. This
  proves that the encoding is a function of the content alone.
- **Check the compiler's determinism.** Compare canonical digests across two kinds of builds of a fixed set of
  modules:
  - *repeated builds in one environment,* which catch run-to-run variation, such as iteration over identity-hashed
    collections;
  - *builds across Linux, Windows, and macOS with JDK 11, 17, 21, and 25,* which catch dependence on the
    environment, such as file listing order.

  Any difference goes to a separate plan for fixing the compiler.
- **Reject unpaired surrogates.**

Exit: canonical forms are identical across repeated builds and across the matrix, or every difference is recorded
for the compiler plan.

### Phase 2: canonical-form tooling (F2)

- **Canonical-form methods** on `ConcreteElementSerializerExtension` and `ModuleMetadataSerializerExtension`, with
  defaults for extensions that do not compress, plus the compressing extensions' overrides and envelope
  validation.
- **A utility** that dispatches on signature and version to produce canonical forms and digests. It needs no new
  dependencies.
- **A comparison tool** that takes two directories or jars and reports PELT files that are missing on one side or
  whose canonical forms differ.
- **A structural diff.** For two files that differ, the tool decodes both and reports the first differences by
  element, node (graph path), property, and value. This is what makes byte comparison useful for debugging a second
  compiler.
- **Optional:** switch `writeIfModified` to compare canonical forms.

Exit: two builds can be checked for equivalence in any environment.

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
  - a canonical text dump of their logical content (JSON with a fixed key order, or a line format).
- **Packaging:** a versioned zip, published as a Maven artifact (for example `legend-pure-pelt-conformance`) and
  downloadable for non-Maven CI. Versions follow legend-pure releases, since the Java compiler's output changes
  between releases.
- **Tests every implementation runs:**

  | Test | Checks |
  |---|---|
  | `decode(file) == dump` | The reader |
  | `canonicalForm(file) == expected` | Decompression, and canonical-form construction |
  | `encode(dump) == expected payload` | The writer's uncompressed output |
  | `canonicalForm(write(dump)) == expected` | The writer, end to end |
  | `validate(write(dump))` | The compressing extension's envelope rules |

  A compiler additionally runs `canonicalForm(compile(sources)) == expected` for every file of each case. That
  becomes `compile(sources) == file` once the canonical encoder is adopted. Platform's own sources are the largest
  single test.

### Phase 4: canonical encoder prototype, measurements, and decision gate

- **Write the encoder specification and a Java prototype.** Test it with fixed vectors and fuzzing against stock
  inflaters (Java, zlib, and one non-Java inflater). Vectors: empty input, one byte, long runs of a single byte,
  random data, input longer than the window, and input longer than one block.
- **Choose module sets:** platform, the legend-engine module set, and at least one of the largest models.
- **Measure each option on those sets, in directories and in jars:**

  | Variant | What it represents |
  |---|---|
  | Uncompressed V1 | Option 4 |
  | Level-7 deflate | Today, and Option 5 (the baseline) |
  | The canonical encoder, at a level-7-like setting and at a higher-ratio setting | Option 1 |

  For each, record size, serialization time, and load time.
- **Use the jar settings that production builds use** (compression level and entry method), and record them.
- **Attribute the gain inside jars.** Find out why PELT-level compression helps when entries are already deflated:
  from the I/O path (number and size of reads, buffering), or from compression itself.
- **For Option 3,** record a breakdown of V1's bytes by category: string index, node headers, property headers,
  values, and source information.
- **Decide** according to [section 6](#6-recommendation).

### Phase 5: adopting the canonical encoder (if the gate passes)

- **The compressing extensions switch encoders.** That covers `ConcreteElementSerializerV2`,
  `ModuleMetadataSerializerV3`, and any other extension that compresses.
- **The corpus gains expected compressed bytes,** and every implementation's tests add `write(dump) == file`.
- **A canonicality check** re-encodes a file's canonical form with the extension's encoder and compares the result
  with the file.

### Phase 6: a second implementation

- Write a reader and a writer in one non-Java language; Rust is a good first choice. It passes the corpus, using
  the canonical encoder if Phase 5 happened and stock deflate otherwise.
- Cross-check in CI: each implementation loads the other's files.
- Spec gaps go back into Phase 0's document.
- A non-Java compiler, when one exists, builds on this reader and writer. It is tested by compiling the corpus
  sources and comparing its binaries with the expected files, using the structural diff to locate differences.

### Phase 7: keeping it true

- **The invariant holds from here on:** byte identity, or identical canonical forms if the plan settles at
  Option 5. A change that breaks it is a bug, and the checks below catch it.
- **Any format change, new version, or new kind of PELT file** comes with its specification, its canonical-form
  method, and its corpus entries, in the same PR. A new compressing extension compresses the same way as the
  existing ones.
- **The Java build runs the corpus tests,** and the determinism checks keep running.

## 8. Backward compatibility

- **Every option** keeps reading existing binaries. They can be compared with new files of the same version by
  canonical form.
- **Options 1, 2, 4, and 5** change no file format, so earlier readers read new files. Option 5a adds a new file
  that they ignore.
- **Options 3 and 5b** need new extension versions, which earlier readers cannot read.
- **Adding canonical-form methods** to the extension interfaces is compatible, because they have defaults.
- **If a Phase 1 fix changes an uncompressed format,** it gets a new version through the existing extension
  mechanism, and readers keep the old version.
- **Builds** place no new requirement on dependencies, under any option.

## 9. Decisions to make

1. **The outcome.** Restore byte identity through Option 1, subject to the gate (recommended), with Option 2 or
   Option 4 if Option 1 falls short; or settle at Option 5 if byte identity costs more than it is worth.
2. **Gate tolerances:** how much size, serialization time, and load time byte identity is worth, measured against
   Option 5. Size is the most important of the three.
3. **Marking canonical files with a new extension version.** Not recommended: canonicality can be checked by
   re-encoding, and avoiding a new version keeps forward compatibility. The price is that the encoder is frozen,
   and any revision comes with a new version.
4. **Non-Java writers:** require the canonical encoder from the start, or allow stock deflate until they publish
   PELTs.
5. **The module index of canonical digests (5a):** needed if the plan settles at Option 5, useful in the interim,
   and unnecessary once byte identity is restored, since raw files then work with generic tools.
6. **Unpaired surrogates:** reject them (recommended), or encode them, for example as WTF-8, which is a format
   change.
7. **Case-only path collisions:** a build-time error (recommended), or file-name escaping in a new path provider
   version.
8. **Where the corpus lives,** and its size budget.
9. **Which non-Java language comes first.**

## Outside this plan

- **Fixing compiler nondeterminism.** Phase 1 detects any. Fixes belong in a separate plan.
- **Double compression in jars.** Unless the packaging tool is configured otherwise, PELT entries are deflated a
  second time when packaged, for little gain. Storing them uncompressed (`STORED`) in jars would avoid that work at
  build and load time. Measure before changing.
- **Byte-identical jars across environments** are out of reach from the PELT side alone. Jar bytes also depend on
  the JDK through compiled class files and the jar's own entry compression. PELT-level identity is the realistic
  scope.
