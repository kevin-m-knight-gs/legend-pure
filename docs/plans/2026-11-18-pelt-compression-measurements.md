# PELT compression measurements

Status: results, 2026-11-18. Directory size counts against the gate, decided 2026-11-19. A prototype of the
canonical encoder was measured on 2026-11-20 ([section 10](#10-the-canonical-encoder)), and the gate chose it, with
its level-7-like setting, the same day. The buffering recommended
here was merged on 2026-11-19 ([#1401]), and [section 9](#9-the-buffering-fix) measures its effect. Elsewhere,
"today's readers" are those of legend-pure 5.112.1, from before that change. It leaves today's files unchanged.

These are the measurements for
[Phase 4 of the byte-identity plan](2026-10-15-pelt-byte-identity.md#phase-4-measurements-encoder-prototype-and-decision-gate).
They compare Option 4 (uncompressed PELT files: element V1 and module metadata V2, with compression left to the
jar) against today's level-7 deflate (element V2 and module metadata V3), which is also Option 5 and the baseline.
Section 10 then compares a prototype of the canonical encoder (Option 1) the same way.

## Summary

- **Size in jars: Option 4 is within 1% of today.** Deflated by the jar, Option 4's PELT entries take 0.02% to
  0.80% more bytes than today's, depending on the module set. That is within the plan's example tolerance of 2%.
- **Size in directories: Option 4's files are 3.1 to 4.0 times as large.** Counting each file's bytes rounded up
  to 4 KiB, they take 1.65 to 2.10 times the space.
- **Load time depends on the readers, not on the compression.** Both formats are read through unbuffered streams
  that cost one native call per primitive read, and that cost dominates decoding.
  - With today's readers, Option 4 loads at the same speed as today from jars, and faster from directories: in
    0.69 to 0.77 of today's time end to end, and in 0.39 to 0.42 of today's time to read and decode.
  - With a buffer after each inflater, a reader change, today's files read and decode as fast as Option 4's, in
    jars and in directories. That makes today's reads 1.8 to 2.7 times as fast.
- **The buffering fix, now merged ([#1401]),** takes 0.57 to 0.76 of the time to load end to end, and 0.54 of the
  time to encode platform with compression, with the output unchanged ([section 9](#9-the-buffering-fix)).
- **Serialization: Option 4 writes faster, and moves compression into packaging.** A clean write takes 0.58 to
  0.64 of today's time. Packaging then spends 3.2 to 4.7 times as much CPU time deflating, but compression as a
  whole takes 0.66 to 0.69 of today's CPU time: one level-6 deflate, instead of a level-7 deflate in serialization
  followed by the jar's.
- **For Option 3:** once readers buffer, inflating is 9% to 16% of the time to read and decode. In element V1's
  bytes, the string index takes a third, and property headers more than a quarter
  ([section 6](#6-element-v1s-bytes-for-option-3)).
- **The earlier findings in [section 1.3](2026-10-15-pelt-byte-identity.md#13-what-pelt-level-compression-buys-today)
  hold only for cost** under these conditions. PELT-level compression does not speed up loading from jars, and it
  slows loading from directories. Its size gain in jars is under 1%. It does add serialization time.
- **Jar settings:** maven-jar-plugin's defaults, which deflate every entry at level 6. Rebuilding the jars with
  those settings reproduced the compressed size of every entry but 3 of legend-engine's 95,217 PELT entries.
- **Decision: Option 4 falls short** ([section 8](#8-the-decision-gate)). It meets the size tolerance in jars, and
  loads at least as fast, but directory size counts against the tolerance too, and its directories are 3.1 to 4.0
  times as large. Under the gate, the canonical encoder (Option 1) was prototyped next.
- **The canonical encoder meets the gate** ([section 10](#10-the-canonical-encoder)). With zlib's level-7
  matching settings, the prototype's files are 0.07% to 0.10% smaller than today's, in jars and in directories,
  and it compresses, serializes, and loads in the same time. With level 9's, its files are 0.21% to 0.48% smaller,
  for 1.8 to 2.8 times the compression time. Every file it wrote inflates correctly with four inflaters, all of
  zlib's lineage. **Decided on 2026-11-20:** Option 1, with the level-7-like setting.
- **Found along the way:**
  - Storing Option 4's entries uncompressed in jars makes reading them 14 to 18 times as slow as deflated entries,
    with today's readers.
  - legend-engine has case-only path collisions, which bear on decision 7.
  - Compiling legend-engine's `core_relational` twice gives different graphs: compiler nondeterminism, which the
    plan's Phase 2 is meant to detect.
  - Write-if-modified reads back each temporary file it has just written, which is slow under real-time scanning.

## 1. Method

### Module sets

| Set | Jars | PELT files | Today's PELT bytes | Notes |
|---|---|---|---|---|
| platform | 1 | 2,638 | 4,410,867 | `legend-pure-m3-core` 5.112.1, as released |
| legend-pure | 9 | 3,450 | 5,093,727 | every legend-pure 5.112.1 jar with PELT files |
| legend-engine | 151 | 95,217 | 187,293,271 | every legend-engine jar with PELT files, 5.3.1-SNAPSHOT built locally against legend-pure 5.112.1, plus the legend-pure jars it uses |

None of the largest models could be measured.

### Option 4's files

Element V2 writes the element header and then the V1 payload through a raw level-7 deflater. Module metadata V3
does the same over V2. So the uncompressed counterpart of each file (F2) is its header with the version lowered,
followed by its inflated payload: what V1 or V2 writes for the same content. The harness made Option 4's files that
way from the jars' own files. Two checks confirm them:

- **Every counterpart decodes to the same form.** For every file in all three sets, the counterpart decodes,
  through the product's own deserializers, to a form equal to the original's, and the decoder consumes it exactly.
  The deflate stream is exactly one complete stream, with nothing after it.
- **Serialization reproduces them.** Compiling platform from source and serializing it with element V1 and
  metadata V2 gives files byte-identical to all 2,638 counterparts. Serializing it with today's versions gives files
  byte-identical to the released ones. The released jar was built with JDK 25, according to its manifest, and these
  were serialized on Windows with JDK 17.

### Jars

Each set's jars were rebuilt four ways: with today's files or Option 4's, and with PELT entries deflated or stored.
Every other entry, and the order of entries, stays as it was. The deflate level was found by reproducing the jars'
own compressed entry sizes ([section 2](#2-jar-settings)).

### Timing

- Windows 11 on an Intel i9-13900H with NVMe SSDs, Microsoft Defender real-time protection on, and the Balanced
  power plan.
- Temurin JDK 17.0.21, with a heap of 8 to 32 GB. Platform's read and decode was also measured on Temurin JDK
  25.0.5.
- A fresh JVM for each configuration and variant, with the order of the variants alternated between repetitions.
- The OS file cache is warm throughout. Figures are medians, in seconds.

### What each measurement covers

- **Read and decode:** every PELT file of a set, read and decoded with the product's deserializers, one file at a
  time. Reads go through a class loader over the jars, as `FileDeserializer` does (`getResource`, then
  `openStream()`), or through `Files.newInputStream` with a `BufferedInputStream` for directories, as
  `FileDeserializer` also does. Each JVM makes several passes, and the first is reported as cold.
- **End to end:** `PureCompilerLoader.loadAll` into a new `PureRuntime`, followed by the realization of every
  element. Elements are lazy and read their files on first use. This runs once per JVM. It covers platform alone,
  and `core_relational` with the 26 modules it depends on (27 jars, 29,562 elements).
- **Serialization:** `PureCompilerSerializer.serializeModules` for a module compiled from source, with today's
  versions or Option 4's. It covers platform (2,000 elements), and `core_relational` (12,260 elements) with its
  dependencies loaded from their PELT files.

## 2. Jar settings

Both repositories package PELT files with maven-jar-plugin's defaults: legend-pure with version 3.5.0, and
legend-engine with version 3.1.2. Every entry is deflated, at level 6.

Rebuilding with `java.util.zip` at level 6 reproduced the compressed size of every entry in the platform and
legend-pure sets. In the legend-engine set, it reproduced all 121,526 entries that are not PELT files, and all but
3 of the 95,217 PELT entries. In a sample of 5,646 entries, level 6 reproduced every one, and the next best
level, 7, reproduced 3,395.

## 3. Size

### In jars

PELT entry bytes, as the jar compresses them:

| Set | Today | Option 4 | Change |
|---|---|---|---|
| platform | 4,409,332 | 4,415,400 | +0.14% |
| legend-pure | 5,096,098 | 5,097,292 | +0.02% |
| legend-engine | 186,518,869 | 188,008,181 | +0.80% |

In the legend-engine set, the whole jars grow by 0.27%, since PELT entries take a third of their bytes. Today's
files gain almost nothing from the jar's second deflate: 187,293,271 bytes as files, 186,518,869 as jar entries.

Storing entries instead of deflating them:

| Set | Today's files, stored | Option 4's files, stored |
|---|---|---|
| platform | +0.03% | +219.80% |
| legend-pure | -0.05% | +214.32% |
| legend-engine | +0.42% | +301.78% |

The changes are measured against today's files, deflated.

### In directories

| Set | Today | Option 4 | Raw ratio | Ratio, each file rounded up to 4 KiB |
|---|---|---|---|---|
| platform | 4,410,867 | 14,101,089 | 3.20 | 1.75 |
| legend-pure | 5,093,727 | 16,018,055 | 3.14 | 1.65 |
| legend-engine | 187,291,645 | 749,399,835 | 4.00 | 2.10 |

Most PELT files are small: 83,896 of legend-engine's 95,213 files are under 4 KiB today, and 70,654 under
Option 4. So on file systems that allocate whole 4 KiB blocks, the growth on disk is smaller than in bytes. The
legend-engine set's directories lack 4 files that collide by case on Windows ([section 7](#7-other-findings)).

By kind of file in the legend-engine set, element files grow the most (4.26 times as large, 157.8 MB to 672.6 MB),
and back-reference files the least (2.45 times, 24.9 MB to 61.0 MB).

## 4. Load time

### Read and decode

Warm medians, in seconds. The ratio is Option 4's time divided by today's.

| Set | Source | Reader | Today | Option 4 | Ratio |
|---|---|---|---|---|---|
| platform | directory | today's | 0.896 | 0.351 | 0.39 |
| platform | directory | buffer after the inflater | 0.337 | 0.283 | 0.84 |
| platform | jar | today's | 0.703 | 0.667 | 0.95 |
| platform | jar | buffer after the inflater | 0.284 | 0.283 | 1.00 |
| legend-pure | directory | today's | 0.892 | 0.354 | 0.40 |
| legend-pure | jar | today's | 0.762 | 0.801 | 1.05 |
| legend-engine | directory | today's | 44.353 | 18.553 | 0.42 |
| legend-engine | directory | buffer after the inflater | 20.853 | 19.000 | 0.91 |
| legend-engine | jar | today's | 35.126 | 41.260 | 1.17 |
| legend-engine | jar | buffer after the inflater | 18.993 | 19.878 | 1.05 |

"Buffer after the inflater" means an 8 KiB `BufferedInputStream` between each inflater and the decoder. For
today's files, that is the inflater in element V2 and metadata V3. For Option 4's files in jars, it is the jar
entry's own inflater. Option 4's files in directories are already buffered, so for them that row adds a second
buffer.

The platform and legend-pure figures are medians of 12 warm passes, and the legend-engine figures of 2. In
legend-engine's jars, the first pass took 38.6 seconds for today's files and 38.1 for Option 4's.

### Why: one native call per read

The decoder reads its input one primitive, or one string's bytes, at a time. When it reads straight from an
`InflaterInputStream`, each of those reads is a call into zlib through JNI. Inflating itself is cheap:

- Reading platform's files and inflating them, without decoding, takes 0.136 seconds for today's files in
  directories, against 0.896 to read and decode them.
- For legend-engine, it takes 7.3 seconds, against 44.4.

The two formats take the same slow path in different places:

| | Today's files | Option 4's files |
|---|---|---|
| Directory | Unbuffered: element V2's or metadata V3's own inflater | Buffered: `FileDeserializer`'s `BufferedInputStream` |
| Jar | Unbuffered: element V2's or metadata V3's own inflater, which reads the jar entry 512 bytes at a time | Unbuffered: the jar entry's inflater |

That accounts for every result above:

- **Directories.** Option 4 is faster, because only today's files pay the per-read cost.
- **Jars.** The two are even, because both pay it.
- **Buffers.**
  - A buffer around the jar entry's stream speeds up Option 4: 0.308 seconds for platform, and 17.3 for
    legend-engine.
  - It does not speed up today's files, whose slow reads come from their own inflater: 0.755 seconds, and 41.5.
  - Reading the whole file into memory first gives the same results.
  - A buffer after the inflater speeds up both.

### End to end

`loadAll`, then the realization of every element. Medians in seconds; the ratio compares the totals.

| Set | Source | Runs | Today | Option 4 | Ratio |
|---|---|---|---|---|---|
| platform | jar | 6 | 0.29 + 1.22 = 1.50 | 0.27 + 1.20 = 1.47 | 0.98 |
| platform | directory | 6 | 0.30 + 1.37 = 1.69 | 0.27 + 0.89 = 1.16 | 0.69 |
| `core_relational` and its dependencies | jar | 3 | 1.57 + 31.79 = 33.36 | 1.36 + 31.78 = 33.14 | 0.99 |
| `core_relational` and its dependencies | directory | 3 | 1.47 + 31.64 = 33.15 | 1.30 + 24.08 = 25.42 | 0.77 |

Here too, the one configuration where every read is buffered (Option 4 in directories) is the fastest. In all four
`core_relational` configurations, the third repetition was the fastest, by up to 31%.

### Stored entries

With PELT entries stored rather than deflated, today's reader reads Option 4's files straight from the jar file:
each primitive read becomes a native file read. Reading and decoding take 12.264 seconds for platform, against
0.667 deflated, and 584 seconds for legend-engine, against 41. Today's files are not affected that way, since
their own inflater reads 512 bytes at a time, but stored entries still read slightly slower than deflated ones:
0.825 seconds against 0.703 for platform, and 41.6 against 35.1 for legend-engine.

So under Option 4, PELT entries must not be stored in jars until the readers buffer. With today's files, storing
saves almost no space and reads slower. That answers the plan's open item on double compression in jars
([Outside this plan](2026-10-15-pelt-byte-identity.md#outside-this-plan)) for today's readers.

## 5. Serialization time

Medians in seconds; the ratio is Option 4's time divided by today's.

| Module | Step | Today | Option 4 | Ratio |
|---|---|---|---|---|
| platform | clean write | 3.35 | 2.15 | 0.64 |
| platform | encoding only | 1.77 | 0.59 | 0.33 |
| `core_relational` | clean write | 48.77 | 28.43 | 0.58 |
| `core_relational` | encoding only | 27.78 | 8.82 | 0.32 |

"Clean write" is `serializeModules` into an empty directory. "Encoding only" encodes every element into a stream
that discards its bytes. Platform has 4 runs and `core_relational` 3.

Under Option 4, the jar does the compressing. The CPU time of deflating every PELT file whole, single-threaded
and from memory, as the jar does for each entry:

| Set | Today: level 7, as in serialization | Today: the jar's level 6 | Today: total | Option 4: the jar's level 6 | Option 4 against today's total |
|---|---|---|---|---|---|
| platform | 0.333 | 0.085 | 0.418 | 0.276 | 0.66 |
| `core_relational` | 5.633 | 0.961 | 6.594 | 4.517 | 0.69 |
| legend-engine | 17.511 | 3.382 | 20.893 | 14.055 | 0.67 |

So Option 4 adds work to packaging, 3.2 to 4.7 times as much, and takes less in all.

Today's serialization also pays for writing through an unbuffered `DeflaterOutputStream`, the counterpart of the
readers' cost. For `core_relational`, encoding with compression takes 19.0 seconds more than without it. Deflating
the module's files whole takes 5.6 seconds. A buffer before the deflater would not change the output: deflating
each file's payload in one call reproduces today's files byte for byte, all 97,851 of them in the platform and
legend-engine sets.

## 6. Element V1's bytes, for Option 3

The plan makes Option 3 worth starting only if inflating is a large share of load time, or if a structural
encoding captures most of deflate's gain.

**Inflating's share of load time.** With a buffer after the inflater, today's files read and decode from
directories in 0.337 seconds for platform and 20.853 for legend-engine. Option 4's files, which need no inflating,
take 0.283 and 19.000. Inflating accounts for the difference: 16% and 9%.

**Where the bytes go.** The element files of the legend-engine set hold 672,554,310 bytes in element V1: 44,055
files, with 9,469,387 instances, 24,606,576 properties, and 25,837,827 values. "Deflated alone" deflates each
file's bytes of one category on their own, at level 7, and sums over the files.

| Category | Bytes | Share | Deflated alone | Platform's share |
|---|---|---|---|---|
| String index | 229,942,184 | 34.2% | 58,959,642 | 34.5% |
| Instance headers: name, classifier, reference ID, compile states, and property count | 95,895,602 | 14.3% | 23,568,734 | 15.2% |
| Source information | 79,800,655 | 11.9% | 15,365,417 | 10.0% |
| Property headers: name, source type, and value count | 193,101,252 | 28.7% | 15,540,838 | 29.2% |
| Values | 72,761,068 | 10.8% | 31,576,653 | 10.7% |
| File header and element preamble | 1,053,549 | 0.2% | 369,478 | 0.4% |
| All | 672,554,310 | 100% | 145,380,762 | 100% |

Within those:

- **Counts are 4 bytes each.** Each property's value count takes 98,426,304 bytes, 14.6% of the total, for an
  average of 1.05 values per property. Deflated alone, they take 2,048,896. Each instance's property count takes
  37,877,548 bytes.
- **Property names and source types** are string-index references, 47,337,474 bytes each.
- **Values:** internal references take 34,975,598 bytes, and deflate the least, to 20,737,594. External references
  take 22,184,326, strings 10,918,372, and numbers 4,377,516.
- **Source information** is on 6,686,815 of the 9,469,387 instances.

Deflating each file's payload whole, as element V2 does, gives 157,142,376 bytes. Deflating each category apart
gives 7.5% less. No structural encoding was measured.

## 7. Other findings

- **Case-only path collisions in legend-engine.** The module `core_analytics_quality` has elements
  `meta::analytics::quality::model::domain::tests::Invalid` and `...::INVALID`, and `...::Invalid1` and
  `...::invalid1`. Their `.pelt` and `.pbr` files collide on case-insensitive file systems. The legend-engine build
  kept both only because this machine's build tree has NTFS case sensitivity turned on. Extracting the jar to an
  ordinary directory loses 4 files. Decision 7's recommended build-time error would break this module, so the
  scan of existing models it calls for has found a case already.
- **Compiler nondeterminism.** Two compilations of `core_relational` from the same jars, in fresh JVMs on the
  same machine and JDK, differ in 10 files, 7 of them element files. Each also differs from the jar that
  legend-engine's build wrote, in 15 and 17 element files. Three patterns account for the element files:
  - in 12 mappings with `distinct`, the order of a class mapping's `primaryKey`;
  - in four functions over relations, the number of instances, mostly generic types, which differs by 1 to 29;
  - in two mappings, the order of a class mapping's `stores`, which differs only from the build.

  The plan treats such differences as compiler bugs for a separate plan, and its Phase 2 detects them. These are
  cases for it to find. They also show that the counterparts compared here are equal only where the compiler is
  deterministic: every other file of `core_relational` matched.
- **Write-if-modified reads back what it has just written.** `FileWriteTools.writeIfModified` writes a temporary
  file, then reads it back to compare it with the existing file. Here, reading a file just written takes about
  10 ms, against 0.2 ms for the existing file. That is consistent with real-time scanning of new files. As a result,
  serializing platform again over unchanged files takes 26 to 54 seconds, against 2.2 to 3.4 seconds for a clean
  write. Comparing the new bytes in memory with the existing file, before writing anything, would avoid the cost.
  It bears on G5, and on the overload F2 adds to `FileWriteTools` if the plan settles at Option 5.
- **Class-path lookups across many directories are slow.** Looking up all of legend-engine's PELT files through
  one class loader over its 151 directories takes 73 to 76 seconds, against 0.4 seconds over its 151 jars. It costs
  the same for either format, and it dominates reading from such a class path: 86 to 107 seconds in all.

## 8. The decision gate

The gate in [section 6](2026-10-15-pelt-byte-identity.md#6-recommendation) holds Option 4 to tolerances on size,
serialization time, and load time, measured against Option 5. The tolerances themselves are decision 2. Part of
it is decided: size counts in directories as well as in jars. In some larger projects, directory size is itself a
problem, and PELT files are used outside jars too.

| Measure | Option 4 against today | Within the example tolerance? |
|---|---|---|
| Size in jars | +0.02% to +0.80% of PELT entry bytes | Yes |
| Size in directories | 3.1 to 4.0 times the bytes, or 1.65 to 2.10 times in 4 KiB blocks | No |
| Serialization time | 0.58 to 0.64 for a clean write; 0.66 to 0.69 of the CPU time spent compressing, packaging included | Yes |
| Load time, readers before [#1401] | Equal in jars, and 0.69 to 0.77 end to end in directories | Yes |
| Load time, readers since [#1401] | Equal in jars, and 0.84 to 0.91 of today's time to read and decode in directories | Yes |

Recommendations:

- **Option 4 falls short on size in directories.** Under the gate, the next step is Phase 4's prototype of the
  canonical encoder (Option 1), measured the same way. [Section 10](#10-the-canonical-encoder) has the results.
- **Buffer after each inflater, whatever the encoder.** Done in [#1401]. Element V2 and metadata V3 decoded
  straight from an `InflaterInputStream`. A `BufferedInputStream` after it makes reading today's files 1.8 to 2.7
  times as fast, from class loaders and directories alike. [Section 9](#9-the-buffering-fix) measures the
  end-to-end effect.
- **Buffer the class loader's stream, too.** Done in [#1401]. `FileDeserializer` read files in directories
  through a `BufferedInputStream`, but class-loader resources through `url.openStream()` alone. With today's files
  that costs nothing measurable, since their inflater reads the stream 512 bytes at a time. Uncompressed files,
  such as those that builds wrote before element V2 and metadata V3, pay a native call per read: an inflate call
  from deflated entries, and a file read from stored ones.
- **Buffer before each deflater.** Done in [#1401]. It leaves the output unchanged
  ([section 5](#5-serialization-time)), and today's serialization spends most of its compression time on writes
  into an unbuffered deflater.
- **Don't store PELT entries in jars.** Today's files gain nothing from it. Uncompressed files read 14 to 18 times
  as slowly with readers from before [#1401], which buffers the class loader's stream. Stored entries have not been
  measured with readers since then.

## 9. The buffering fix

[#1401] made the three buffering changes that [section 8](#8-the-decision-gate) recommends: a
`BufferedInputStream` after each `InflaterInputStream` and a `BufferedOutputStream` before each
`DeflaterOutputStream`, in element V2 and metadata V3, and a `BufferedInputStream` around `FileDeserializer`'s
class-loader streams. It was measured before it merged, against master, under the conditions of
[section 1](#1-method). Both builds' versions of the three classes it changes were compiled with the same javac and
put first on the class path, and the builds alternated.

**Output.** Unchanged. Serializing platform with the fix gives files byte-identical to all 2,638 released ones,
with today's versions, and to their uncompressed counterparts, with Option 4's. The PR's tests check that each V2
and V3 payload equals a one-pass deflate of its V1 or V2 payload.

**End to end.** `loadAll`, then the realization of every element, as in [section 4](#end-to-end). Medians in
seconds; the ratio compares the totals.

| Set | Files | Source | Runs | Before | With the fix | Ratio |
|---|---|---|---|---|---|---|
| platform | today's | jar | 6 | 0.28 + 1.25 = 1.53 | 0.28 + 0.88 = 1.16 | 0.76 |
| platform | today's | directory | 6 | 0.29 + 1.43 = 1.71 | 0.28 + 1.03 = 1.30 | 0.76 |
| platform | Option 4's | jar | 6 | 0.29 + 1.25 = 1.53 | 0.29 + 0.87 = 1.16 | 0.76 |
| `core_relational` and its dependencies | today's | jar | 3 | 1.42 + 22.69 = 24.10 | 1.27 + 12.41 = 13.69 | 0.57 |
| `core_relational` and its dependencies | today's | directory | 3 | 1.34 + 29.43 = 30.76 | 1.21 + 18.59 = 19.82 | 0.64 |

- Today's files gain from the buffers after their inflaters, and Option 4's from the buffer around the class
  loader's stream. With the fix, the two load platform from jars in the same time.
- These runs were a separate session, and their times before the fix differ from section 4's: 24.10 seconds
  against 33.36 for `core_relational` from jars, for example. Compare ratios only within a table.

**Serialization.** Encoding platform with compression, into a stream that discards its bytes, takes 0.97 seconds
against 1.79, or 0.54 of the time, over 4 runs. Encoding without compression, which the fix does not touch, takes
0.60 against 0.59. A clean write takes 3.56 against 4.21, or 0.85.

For `core_relational`, every run that encoded with compression was faster with the fix than every run without it,
over two sessions and 6 runs of each build. Its timings support no ratio, though: encoding without compression took
9.1 to 16.3 seconds across JVMs, and clean writes were slower with the fix in one session and faster in the other.

**For the gate.** The fix narrows Option 4's advantage in directories to the time spent inflating, 9% to 16% of the
time to read and decode ([section 6](#6-element-v1s-bytes-for-option-3)). It leaves the decision unchanged, since
Option 4 falls short on size.

## 10. The canonical encoder

Option 4 fell short, so Phase 4's prototype of the canonical encoder (Option 1) followed: a draft specification,
[Canonical deflate encoder](2026-11-20-pelt-canonical-deflate.md), and a Java prototype of it in the measurement
harness, 705 lines of code. It was measured under the conditions of [section 1](#1-method), at two settings:

- **Level-7-like:** zlib's matching settings for level 7, today's level.
- **Level-9-like:** zlib's matching settings for level 9, for a better ratio.

Each setting's files are today's files with every payload deflated by the prototype in place of `Deflater`: the
same headers, and the same uncompressed payloads. The harness made them from the uncompressed counterparts of
section 1.

### Correctness

- **Every file inflates to its payload, with four inflaters.** At both settings, each of the 101,301 files of the
  three sets inflates to the payload of its uncompressed counterpart, with nothing after the deflate stream,
  through:
  - Java's `Inflater`, on JDK 17.0.21;
  - Python 3.12's `zlib`, with zlib 1.2.13;
  - Node 20.10's `zlib`, with Chromium's zlib 1.2.13.1;
  - .NET 11's `DeflateStream`, with zlib-ng 2.2.5.
- **So do the specification's vectors and 2,000 generated inputs,** at both settings, with the same four
  inflaters. The generated inputs mix random bytes, runs, small alphabets, and copies from any distance, and run up
  to 300,000 bytes. The prototype's unit tests inflate 1,500 more at each of four settings, and check that its code
  lengths are optimal and complete.
- **Serialization reproduces the files.** Compiling platform from source and serializing it, with element V2 and
  metadata V3 encoding through the prototype, gives files byte-identical to all 2,638 of each setting's files. For
  `core_relational`, 8 to 22 of its 21,703 files differ from the expected ones in each run, whichever the encoder.
  That is compiler nondeterminism ([section 7](#7-other-findings)): in each run, as many uncompressed counterparts
  differ, and where the lists were compared, they are the same files.
- **No file needs a stored block.**

All four inflaters are zlib or derived from it. No inflater of another lineage, such as Go's `compress/flate` or
Rust's `miniz_oxide`, was available to test.

### Size

PELT bytes in directories, and as jar entries deflated by the jar at level 6:

| Set | Where | Today | Level-7-like | Change | Level-9-like | Change |
|---|---|---|---|---|---|---|
| platform | directory | 4,410,867 | 4,407,473 | -0.08% | 4,401,194 | -0.22% |
| platform | jar | 4,409,332 | 4,406,143 | -0.07% | 4,400,165 | -0.21% |
| legend-pure | directory | 5,093,727 | 5,089,272 | -0.09% | 5,082,276 | -0.22% |
| legend-pure | jar | 5,096,098 | 5,091,864 | -0.08% | 5,085,178 | -0.21% |
| legend-engine | directory | 187,291,645 | 187,117,888 | -0.09% | 186,430,673 | -0.46% |
| legend-engine | jar | 186,518,869 | 186,341,196 | -0.10% | 185,629,863 | -0.48% |

- **Both settings are smaller than today everywhere.** Counting each file rounded up to 4 KiB, the level-7-like
  setting saves nothing to 0.02%, and the level-9-like setting 0.03% to 0.15%. Whole jars shrink by 0.02% to
  0.03%, and by 0.05% to 0.16%.
- **Every kind of file shrinks,** summed over each set, except two of platform's at the level-7-like setting: its
  manifest grows by 3 bytes, and its back-reference index keeps its size.
- **At the level-7-like setting, the gain comes from the encoder's own choices,** since the matching settings are
  zlib's. The specification lists how it differs from zlib.

### Compression time

The CPU time of deflating every payload, single-threaded and from memory, as in
[section 5](#5-serialization-time). One JVM per set alternated the encoders between passes. Medians of 5 passes
for platform and legend-pure, and of 3 for legend-engine, in seconds:

| Set | Today: zlib level 7 | Level-7-like | Ratio | Level-9-like | Ratio |
|---|---|---|---|---|---|
| platform | 0.336 | 0.347 | 1.04 | 0.603 | 1.79 |
| legend-pure | 0.373 | 0.395 | 1.06 | 0.674 | 1.81 |
| legend-engine | 17.210 | 16.850 | 0.98 | 48.121 | 2.80 |

At the level-7-like setting, the prototype, in Java, takes as long as zlib through `Deflater`.

### Serialization

`serializeModules`, as in [section 5](#5-serialization-time), with element V2 and metadata V3 as master has them
since [#1401], against the same two classes deflating each payload whole with the prototype. Platform has 4 runs
per build, and `core_relational` 2. Medians for platform, and the range of the runs for `core_relational`, in
seconds:

| Module | Step | Today: zlib level 7 | Level-7-like | Ratio | Level-9-like | Ratio |
|---|---|---|---|---|---|---|
| platform | clean write | 4.28 | 4.40 | 1.03 | 4.74 | 1.11 |
| platform | encoding only | 1.35 | 1.36 | 1.01 | 1.62 | 1.20 |
| `core_relational` | clean write | 52.41 to 72.85 | 60.52 to 62.41 | | 70.12 to 75.62 | |
| `core_relational` | encoding only | 15.52 to 25.10 | 21.97 to 23.20 | | 33.15 to 34.13 | |

- **At the level-7-like setting, serialization takes as long as today.** For `core_relational`, its runs fall
  within today's. As in [section 9](#9-the-buffering-fix), `core_relational`'s timings vary too much across JVMs
  for a ratio.
- **The level-9-like setting adds about 11 seconds** to each step for `core_relational`, against the
  level-7-like setting.

### Load time

Every PELT file read and decoded, with a buffer after each inflater as the readers since [#1401] have, measured
as in [section 4](#read-and-decode). Warm medians in seconds:

| Set | Source | Today | Level-7-like | Ratio | Level-9-like | Ratio |
|---|---|---|---|---|---|---|
| platform | jar | 0.386 | 0.387 | 1.00 | 0.402 | 1.04 |
| platform | directory | 0.363 | 0.337 | 0.93 | 0.337 | 0.93 |
| legend-engine | jar | 15.034 | 15.185 | 1.01 | 15.497 | 1.03 |
| legend-engine | directory | 14.479 | 14.301 | 0.99 | 14.426 | 1.00 |

- **The prototype's files load in the same time as today's.** The differences are within the noise. The
  directories were measured in a separate run that also read a copy of today's directories, made just before it.
  The copies took 0.341 and 13.964 seconds: 0.94 and 0.96 of the time for the same files in the originals.
- **Platform's figures are medians of 12 warm passes.** legend-engine's are medians of 2 in jars, and of 4 in
  directories. In jars, legend-engine took 18.2 to 19.0 seconds in the first repetition and 11.8 to 12.0 in the
  second, and within each the three were within 4% of one another.

### For the gate

| Measure | Level-7-like against today | Level-9-like against today |
|---|---|---|
| Size in jars | -0.07% to -0.10% of PELT entry bytes | -0.21% to -0.48% |
| Size in directories | -0.08% to -0.09%, or nothing to -0.02% in 4 KiB blocks | -0.22% to -0.46%, or -0.03% to -0.15% in 4 KiB blocks |
| Compression time | 0.98 to 1.06 | 1.79 to 2.80 |
| Serialization time | The same: 1.01 to 1.03 for platform, and within today's runs for `core_relational` | 1.11 to 1.20 for platform, and about 11 seconds more for `core_relational` |
| Load time, readers since [#1401] | The same: 0.93 to 1.01 | The same: 0.93 to 1.04 |

Recommendations:

- **Option 1 meets the gate at the level-7-like setting.** Its files are smaller than today's, in jars and in
  directories, and it serializes and loads in the same time. It costs nothing that these measurements can detect,
  so it is within any tolerances that decision 2 sets, and under the gate, Option 1 is the outcome.
- **Choose the level-7-like setting** (the plan's decision 12). The level-9-like setting saves another 0.13 to
  0.38 percentage points, for 1.8 to 2.8 times the compression time, and about a fifth more time for a clean write
  of `core_relational`. Settings between the two were not measured.
- **Before freezing the encoder, check it with an inflater of another lineage,** such as Rust's `miniz_oxide`,
  which a second implementation (the plan's Phase 6) would likely use.

Decided on 2026-11-20: Option 1, with the level-7-like setting.

## 11. Not measured

- One of the largest models.
- A cold OS file cache, slow disks, and network file systems. In those settings, reading 3 to 4 times as many bytes
  may matter more, and the earlier findings may come from such a setting.
- Linux and macOS. JDKs other than 17, except platform's read and decode on JDK 25, which shows the same
  pattern: in directories, 0.965 seconds for today's files and 0.293 for Option 4's; in jars, 0.887 and 0.874; with
  a buffer after the inflater, 0.265 and 0.263 in jars.
- Stored PELT entries, and Option 4's files in directories, with the buffering fix.
- The wall time of packaging. maven-jar-plugin compresses entries on several threads, and the figures in
  [section 5](#5-serialization-time) are single-threaded CPU time.
- For the canonical encoder:
  - an inflater outside zlib's lineage;
  - settings between the level-7-like and level-9-like ones;
  - end-to-end load times, for which read and decode stand in.

[#1401]: https://github.com/finos/legend-pure/pull/1401
