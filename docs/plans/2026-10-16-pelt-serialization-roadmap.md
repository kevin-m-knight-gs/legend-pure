# PELT serialization plans: roadmap

Status: 2026-10-16.

Three plans change PELT serialization. Each stands on its own. This document records only how they depend on each
other, the rules they share, and the order to do them in.

| Plan | What it does | When |
|---|---|---|
| [Byte-identical PELT files](2026-10-15-pelt-byte-identity.md) | Makes every PELT file a function of its logical content alone, and specifies the format | Now |
| [PELT consistency checks](2026-10-15-pelt-consistency-checks.md) | Finds, before any element loads, modules built against other versions of their dependencies | After the byte-identity plan's Phase 2 |
| [More than one path provider or reference ID version](2026-10-16-pelt-path-provider-and-reference-id-versions.md) (the versions plan) | Lets modules written with different versions of either extension load together | Phases 1 and 2 early; Phase 3 when a new version is wanted |

## Dependencies

- **Byte identity depends on neither of the others.** It keeps today's file-name hash and recommends a build-time
  error for case-only path collisions. Only the alternative to that error, escaping, would need a new path provider
  version.
- **The consistency checks depend on byte identity through its Phase 2:** the specification (Phase 0), the
  canonical-form tooling (Phase 1), and canonical uncompressed payloads from a deterministic compiler (Phase 2),
  including the split of `SerializerV1` that ECDs are computed with. They do not depend on byte identity's
  decision about compression: their file's details are compressed however module metadata ends up compressed.
- **The versions plan:** Phase 1 depends on nothing. Phase 2 needs byte identity's specification. Phase 3's new
  metadata version follows byte identity's decision about compression.
- **Where the consistency checks and the versions plan meet:**
  - ECDs include reference IDs, so a dependency rebuilt with another reference ID version changes its ECDs. The
    consistency checks report that before any element loads, naming the version change. The versions plan's
    back-reference check is the backstop for modules without consistency data.
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
3. **New versions and new kinds of file** come with their specification, their canonical-form method, and their
   conformance-corpus entries, in the same PR, and keep the byte-identity invariant (byte identity's Phase 7).
4. **Phase the code, not the format.** Binaries outlive the release that wrote them, so a format's first release
   writes every field the plan needs, even fields that nothing reads yet. Checks and readers can come in phases.
5. **Fail early, in a fixed order.** When modules load:
   1. at each manifest: an unknown metadata version, or a version record the reader lacks (the versions plan);
   2. before any element loads: the consistency check;
   3. as each element loads, as a backstop: its header against its manifest, and its back-reference files against
      its reference ID version (the versions plan).

## Order

1. **Now, in parallel:**
   - byte identity's Phase 4 measurements of Option 4 against today's compression, which need no new product
     code;
   - the versions plan's Phase 1, which fixes latent bugs;
   - byte identity's Phase 0, the specification of the encoding.
2. **Next:** byte identity's Phases 1 to 3, and the versions plan's Phase 2 once the specification exists.
3. **Then:** the consistency checks, with Phases 1 and 2 in one release, once byte identity's Phase 2 is done.
   Meanwhile, byte identity's decision gate, and its Phase 5 if the gate chooses Option 1.
4. **When wanted:** byte identity's Phase 6, rules for compilers and a second implementation, with the non-Java
   work; and the versions plan's Phase 3, with the first new version of either extension.
