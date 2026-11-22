# PELT serialization plans: roadmap

Status: 2026-10-16, last updated 2026-11-22.

Three plans change PELT serialization. A fourth document sets out options for handling strings across platforms
and languages: some of its decisions become rules in the byte-identity specification, and the rest reach beyond
PELT into the Pure language and the other Legend repositories. Each stands on its own. This document records only
how they depend on each other, the rules they share, and the order to do them in.

| Plan | What it does | When |
|---|---|---|
| [Byte-identical PELT files](2026-10-15-pelt-byte-identity.md) | Defines a serializable form of each element, makes every PELT file a function of its content alone, and specifies the format | Now |
| [PELT consistency checks](2026-10-15-pelt-consistency-checks.md) | Finds, before any element loads, modules built against other versions of their dependencies | After the byte-identity plan's Phase 3 and the versions plan's Phase 2 |
| [More than one path provider or reference ID version](2026-10-16-pelt-path-provider-and-reference-id-versions.md) (the versions plan) | Lets modules written with different versions of either extension load together | Phase 1 done; Phase 2 early; Phase 3 when a new version is wanted |
| [Strings across platforms and languages](2026-11-06-cross-platform-strings.md) (the strings document) | Sets out options for the string value, source coordinates, the language's string functions, and the format's string rules, and lists hazards to fix whatever is chosen | Hazard fixes now; format decisions with the byte-identity plan's Phase 0, coordinate decisions before its Phase 6, and language decisions separately |

## Dependencies

- **Byte identity depends on neither of the other plans.** It keeps today's file-name hash and recommends a
  build-time error for case-only path collisions. Only the alternative to that error, escaping, would need a new
  path provider version.
- **The consistency checks depend on byte identity through its Phase 3:** the specification, including the
  serializable form and its digest encoding (Phase 0), the serializable-form tooling (Phase 1), the generator,
  the encoders that take serializable forms, and canonical uncompressed payloads from a deterministic compiler
  (Phase 2), and the conformance corpus, which their file joins (Phase 3). ECDs are digests of the generator's
  serializable forms, so they do not depend on compression. Their file's details are compressed as module metadata
  is: with today's deflater until byte identity's Phase 5 adopts the canonical encoder, and with that encoder from
  then on. They also depend on the versions plan's Phase 2 (below).
- **The versions plan:** Phase 1, now done, depended on nothing. Phase 2 adds to byte identity's specification,
  so it waits for that specification to be merged. Phase 3 needs two new metadata versions, an uncompressed one
  and a compressed one, since byte identity keeps both kinds of metadata in use (its F2).
- **The strings document depends on none of the plans, and they wait for it only in text.** It agrees with byte
  identity's decisions 6 and 7, and adds Windows device names and non-ASCII path segments to decision 7's
  build-time error. Byte identity's specification (Phase 0) writes down the format rules it recommends: well-formed
  UTF-8, lengths in bytes, today's UTF-16 orders and reference ID escapes, and ASCII path segments. Byte identity's
  rules for compilers (Phase 6) write down the strings document's coordinate rules. Counting columns in code points
  changes no format, and the consistency checks do not see it, since ECDs leave source information out. Departing
  from what the format does today takes new versions: a new path provider version for path segments outside ASCII
  (the strings document's section 6.5 lists options for it, none chosen), and a new reference ID version for
  another order in reference ID generation (the versions plan's Phase 3). UTF-8 orders elsewhere come only with
  versions made for other reasons.
- **Where the consistency checks and the versions plan meet:**
  - ECDs include reference IDs, so a dependency rebuilt with another reference ID version changes its ECDs. The
    consistency checks report that before any element loads, naming the version change. The versions plan's
    back-reference check is the backstop for modules without consistency data.
  - The consistency checks find the element that each external reference points into through the versions plan's
    R6, which its Phase 2 adds, so they work with any reference ID version.
  - The consistency file is found like the module's other files: under the path provider version the module
    records, once modules can record one.
  - Both rely on every PELT loader going through `ElementLoader`, `PureCompilerLoader`, or `MetadataPelt`. The
    consistency checks plan audits legend-engine's PELT users to confirm it.

## Shared rules

1. **Where new data goes.** Data that earlier readers may safely ignore goes in a file they skip, not in the
   manifest: a manifest that holds it needs a metadata version they reject. Data that earlier readers must not
   ignore goes in the manifest, under a metadata version they reject, and only for the modules that need it, so
   other modules are written as before. Either way, extend an existing kind of file rather than add one where
   possible: serialization already writes a great many files.
2. **Lookups.** On a classpath, a lookup searches the jars in turn, and one that finds nothing searches all of
   them. A new lookup per module on every load needs a reason. The consistency file has one, since every module
   carries it. Version records do not, since most modules have none, so they go in the manifest, which readers
   read anyway.
3. **New versions and new kinds of file** come with their specification, the serializable form they encode or any
   additions to it, the uncompressed counterpart of any file they compress, and their conformance-corpus entries,
   in the same PR, and keep the byte-identity invariant (byte identity's Phase 7).
4. **Phase the code, not the format.** Binaries outlive the release that wrote them, so a format's first release
   writes every field the plan needs, even fields that nothing reads yet. Checks and readers can come in phases.
5. **Fail early, in a fixed order.** When modules load:
   1. at each manifest: an unknown metadata version, or a version record the reader lacks (the versions plan);
   2. before any element loads: the consistency check;
   3. as each element loads, as a backstop: its header against its manifest, and its back-reference files against
      its reference ID version (the versions plan).
6. **Serializable forms, not bytes.** Comparisons and digests of content are defined on serializable forms (byte
   identity's F0), never on a file's bytes, compressed or not. Serializable forms compare across format versions
   and languages, and between compiled graphs and files. Reference IDs are content, so forms written with
   different reference ID versions compare only through what their IDs name (byte identity's F0). Files written
   with the same versions compare by bytes once byte identity holds.

## Order

The versions plan's Phase 1, which fixed latent bugs, is done. So is the buffering that byte identity's Phase 4
measurements recommended ([#1401]), which changes no file. So are two of the strings document's hazard fixes: the
default-locale fixes ([#1403], and [#1404] for its decision 11), and the first step for `StringEscape` ([#1402]),
after which legend-pure's own code writes the reference ID escapes that byte identity's Phase 0 specifies. So is
byte identity's decision gate, at the end of its Phase 4: Option 4 fell short on size in directories, a prototype
of the canonical encoder wrote files smaller than today's at today's speed
([PELT compression measurements](2026-11-18-pelt-compression-measurements.md)), and on 2026-11-20 the gate chose
Option 1, the canonical encoder, with its level-7-like setting. None of the largest models was measured, and the
gate was decided without them.

1. **Now, in parallel:**
   - byte identity's Phase 0, the specification of the serializable form and the encoding, with the strings
     document's decisions on the format, made on 2026-11-20: well-formed UTF-8 in files (its decision 1, for
     serialization), the UTF-16 orders in existing versions (9), and ASCII path segments in path provider V1 (10).
     A draft, `docs/reference/pelt-format.md`, was written the same day, and two reviews, on 2026-11-21, and a
     third, on 2026-11-22, corrected it. Byte identity's decisions 10, 11, and 13, on the digest encoding, on Float
     and Decimal, and on the text encoding, remain, and the draft proposes answers for them;
   - the fixes for the departures that the draft lists (its section 12) and that change no file, and for the bugs
     in the reference ID parser that its first review found (byte identity's Phase 0), one per bug;
   - the strings document's remaining hazard fixes (its section 8), one per bug, beginning with merging the
     default-charset fixes for legend-pure ([#1405]); and its scans of existing models.
2. **Next:** byte identity's Phases 1 to 3, and the versions plan's Phase 2 once the specification is merged.
3. **Then:** the consistency checks, with Phases 1 and 2 in one release, once byte identity's Phase 3 and the
   versions plan's Phase 2 are done.
   Meanwhile, byte identity's Phase 5, which adopts the canonical encoder.
4. **When wanted:** byte identity's Phase 6, rules for compilers and a second implementation, with the non-Java
   work, once the strings document's decisions on coordinates (its decisions 3 to 5) are made; and the versions
   plan's Phase 3, with the first new version of either extension.
5. **Separately:** the strings document's decisions on the Pure language and its string functions, which no PELT
   work waits for. Decision 11, `Locale.ROOT` wherever a locale is required, was made on 2026-11-20, and [#1404]
   carried it out in legend-pure; its fixes in the other repositories join the hazard fixes in item 1.

[#1401]: https://github.com/finos/legend-pure/pull/1401
[#1402]: https://github.com/finos/legend-pure/pull/1402
[#1403]: https://github.com/finos/legend-pure/pull/1403
[#1404]: https://github.com/finos/legend-pure/pull/1404
[#1405]: https://github.com/finos/legend-pure/pull/1405
