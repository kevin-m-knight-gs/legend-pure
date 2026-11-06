# Strings across platforms and languages: options

Status: options for decision, 2026-11-06.
Related: [byte-identical PELT files](2026-10-15-pelt-byte-identity.md),
[PELT consistency checks](2026-10-15-pelt-consistency-checks.md),
[path provider and reference ID versions](2026-10-16-pelt-path-provider-and-reference-id-versions.md), and the
[roadmap](2026-10-16-pelt-serialization-roadmap.md) for how they fit together.

## Purpose

The [byte-identity plan](2026-10-15-pelt-byte-identity.md) defines string order and length in UTF-16 code units
(its [section 5](2026-10-15-pelt-byte-identity.md#5-other-details-a-specification-has-to-pin-down)), and leaves
three string questions open: unpaired surrogates (its
[decision 6](2026-10-15-pelt-byte-identity.md#9-decisions-to-make)), case-only path collisions (its decision 7),
and the column units of source information (its
[F3](2026-10-15-pelt-byte-identity.md#f3-graph-to-content-rules-for-a-second-compiler)). None of them can be
settled for PELT alone. Column units belong to every parser and to every tool that shows a position. String lengths
belong to the Pure language, which runs in two Java engines, in generated Java, and in databases, and may one day
run in other languages. This document:

- describes how Legend handles strings today, in terms that do not depend on Java (section 1);
- sets out options for the string value, for source coordinates, for the language's string functions, and for
  serialization, and assesses a mixed approach (sections 3 to 7);
- lists hazards that do not depend on those choices, such as platform-default charsets and locales (section 8);
- says what the recommended combination changes in legend-shared, legend-pure, legend-engine, legend-sdlc, and the
  front ends (section 9);
- recommends, and proposes an order of work (sections 10 to 12).

Nothing in the byte-identity plan waits for these decisions except the text of its specification (its Phase 0) and
its rules for compilers (its Phase 6).

Statements marked *verified* were checked on 2026-11-06 at legend-pure `234ae818c`, legend-engine `c6aa1602956`,
legend-sdlc `ebf1f204`, and legend-shared `f9b8089`, by reading the code or by running small programs against
Temurin JDK 11.0.32, 17.0.21, 21.0.12, and 25.0.5 on Windows 11, legend-pure's generated M3 lexer, H2 2.1.214, and
DuckDB 1.4.5.

## Terms

| Term | Meaning |
|---|---|
| Code point | A number from U+0000 to U+10FFFF. |
| Surrogate | A code point from U+D800 to U+DFFF. Reserved for UTF-16; never a character. |
| Unicode scalar value | A code point that is not a surrogate. What UTF-8 can encode, and what Rust's `char` holds. |
| Supplementary character | A scalar value from U+10000 up: most emoji, many CJK ideographs, and historic scripts. |
| UTF-16 code unit | A 16-bit value. A scalar value below U+10000 takes one, and a supplementary character takes two, a surrogate pair. Java's `char` is one, and `String.length()` counts them. |
| Unpaired surrogate | A code unit from U+D800 to U+DFFF that is not part of a pair. Java and JavaScript strings may hold one; UTF-8 cannot encode it. |
| UTF-8 byte | A scalar value takes one byte (ASCII), two (up to U+07FF), three (up to U+FFFF), or four (supplementary). |
| Grapheme cluster | What a reader sees as one character, such as a letter with combining marks, or a flag. Defined by Unicode Standard Annex 29, whose rules change between Unicode versions. |

| | U+0061 (a) | U+00E9 (e acute) | U+4E2D (a CJK ideograph) | U+1F600 (an emoji) |
|---|---|---|---|---|
| Code points | 1 | 1 | 1 | 1 |
| UTF-16 code units | 1 | 1 | 1 | 2 |
| UTF-8 bytes | 1 | 2 | 3 | 4 |

Three facts carry most of what follows:

1. UTF-16 code units and code points differ only for supplementary characters.
2. UTF-8 bytes and code points differ for every character outside ASCII.
3. Comparing UTF-8 bytes gives code point order. Comparing UTF-16 code units gives the same order except in one
   case: a supplementary character sorts before U+E000 to U+FFFF, since its first unit is a surrogate (U+D800 to
   U+DBFF).

How other environments count:

| Environment | Native unit |
|---|---|
| Java, JavaScript, C# | UTF-16 code units; unpaired surrogates allowed |
| Python | Code points; unpaired surrogates allowed |
| Rust | `str::len` counts UTF-8 bytes, and `chars` yields scalar values; a `String` cannot hold an unpaired surrogate |
| Go | `len` counts bytes; a `rune` is a code point |
| C | `char` strings are bytes; `wchar_t` is 16 bits (UTF-16) on Windows, and 32 bits (UTF-32) on Linux and macOS |
| Swift | `String.count` counts grapheme clusters |
| Monaco and VS Code | Columns in UTF-16 code units |
| Language Server Protocol (LSP) | UTF-16 by default; since 3.17, UTF-8 or UTF-32 if client and server agree |
| ANTLR | `ANTLRInputStream`, deprecated since 4.7, counts UTF-16 code units; `CharStreams` counts code points |

## 1. Where we are

In platform-independent terms, Legend today:

- treats a string as a sequence of UTF-16 code units, which may be ill-formed (1.1);
- counts, indexes, slices, and orders strings in UTF-16 code units (1.4 and 1.6), except where it relies on ANTLR's
  code point streams (all of legend-engine's parsers and three of legend-pure's, 1.3), on databases, most of which
  count code points (1.5), or on Java helpers that work in code points (`reverseString` and `split`, 1.4);
- reads source text as UTF-8, leniently, and stores strings as UTF-8 framed by byte counts, silently replacing what
  UTF-8 cannot hold (1.2 and 1.6);
- takes case mapping, and some conversions between text and bytes, from the JVM's default locale and charset, and
  character classes from the JDK's Unicode tables (1.7).

### 1.1 The string value

A Pure `String` is a Java `String`: a sequence of UTF-16 code units, which may include unpaired surrogates. Several
paths create them:

- **Literals.** The lexers accept a backslash-u escape with up to four hex digits (`M4Fragment.g4:50`), and
  `StringEscape` decodes each escape to a single code unit (`StringEscape.java:40`), so `'\uD800'` holds an
  unpaired surrogate. A supplementary character can only be escaped as a pair, such as `\uD83D\uDE00` for U+1F600.
- **Functions.** `substring` can cut a pair in half (verified: the first unit of U+1F600 is a lone high surrogate).
  legend-engine's `char` casts its argument to a 16-bit `char` (`FunctionsHelper.java:290`), so 0xD800 gives an
  unpaired surrogate, and 0x1F600 gives U+F600 (verified).
- **Stores.** H2 slices in code units: `SUBSTRING(s, 3, 1)` of `a`, U+1F600, `b` returns the low surrogate U+DE00
  alone (verified).

Strings are never normalized (to NFC or otherwise), and two strings are equal when their code units are.

### 1.2 Source text

| Aspect | Today |
|---|---|
| Encoding | UTF-8, named explicitly (`FSCodeStorage.java:168`, `ClassLoaderFileNode.java:40`). Decoding is lenient: malformed bytes become U+FFFD without an error. |
| Byte order mark | Kept. The M3 lexer rejects U+FEFF with a token recognition error at 1:1 (verified), so a file saved with a BOM, as some Windows editors do, fails to parse. |
| Line terminators | The lexers start a new line at LF only, as ANTLR does; CR is whitespace and takes a column. Multi-line literals and documentation treat LF, CRLF, and CR as terminators and normalize them to LF (`MultilineTextLayout.java:50`), and single-line literals cannot contain CR or LF (`M4Fragment.g4:7`). So a CRLF checkout gives the same lines, token columns, and string values as an LF checkout (token positions verified). A lone CR ends a line in a multi-line literal and in editors, but not in line numbers. |
| Tabs | One column. |
| Identifiers | legend-pure: ASCII letters, digits, `_`, and `$` (`M4Fragment.g4:27`), except that property and column names may be quoted strings (`M3CoreParser.g4:104` and `:495`). legend-engine: any identifier may be a quoted string (`M3ParserGrammar.g4:8`), including package and element names (`CoreParserGrammar.g4:3`), so element names can hold any text. legend-sdlc: entity paths allow only ASCII letters, digits, `_`, and `$` (`EntityPaths.java`). |
| Composed text | legend-engine's grammar composer escapes string literals with `escapeJava` (`PureGrammarComposerUtility.java:106`), which writes each non-ASCII character as backslash-u escapes of its UTF-16 code units. Text it composes, including legend-sdlc's `.pure` entity files, is therefore ASCII. |
| In practice | 162 `.pure` files in legend-pure, and 233 in legend-engine, contain non-ASCII text, mostly punctuation in documentation. No `.pure` file in the four repositories contains a supplementary character, written literally or escaped (verified). |
| Line endings in git | None of the four repositories has a `.gitattributes` file, so Windows checkouts with `core.autocrlf=true` get CRLF. |

### 1.3 Source coordinates

m4's `SourceInformation` holds a source ID and 1-based start, main, and end positions. An end column is inclusive:
it is the column of the last character. Columns come from ANTLR's `charPositionInLine`, whose unit is the char
stream's:

| Parsers | Char stream | Start columns | End columns |
|---|---|---|---|
| legend-pure: M3 (`M3AntlrParser.java:654`), M4, diagram, graph, path, relational, aggregation-aware, enumeration mapping, and relation mapping | `ANTLRInputStream` | UTF-16 code units | Start plus the token text's UTF-16 length (`AntlrSourceInformation.java:86`): consistent |
| legend-pure: sections (`TopParser.java:57`), mapping (`MappingParser.java:191`), and operation (`OperationParser.java:158`) | `CharStreams.fromString` | Code points | Start plus the token text's UTF-16 length: mixed |
| legend-engine: every grammar parser, in 48 files (for example `PureGrammarParser.java:115`) | `CharStreams.fromString` | Code points | Start plus the token text's UTF-16 length (`ParseTreeWalkerSourceInformation.java:111`): mixed |

The section parser passes only line offsets to the parsers it calls (`TopGraphBuilder.java:91`), so its unit does
not reach the graph. Where the units are mixed, a token that contains a supplementary character gets an end column
past its last character. Lexing `let x = '<U+10B00>'; let y = 1;` with legend-pure's M3 lexer, where `<U+10B00>`
stands for that one character (verified):

| Token | Columns with `ANTLRInputStream` | Columns with `CharStreams` |
|---|---|---|
| The string literal | 9 to 12 | 9 to 12, though it ends at 11 |
| The first `;` | 13 | 12 |
| `y` | 19 | 18 |

So the same source gets different columns from legend-pure and from legend-engine whenever a supplementary
character precedes a position on its line, and legend-engine's end columns can overlap the next token.

The columns reach:

- error messages, and the many tests that expect them;
- Pure code, through `meta::pure::functions::meta::sourceInformation`, which returns lines and columns;
- PELT, which stores six integers per node and records no unit (`SerializerV1.java:313`), and element membership,
  which compares positions (`SerializerV1.isExternal`);
- legend-pure's language server, which turns them into LSP positions by subtracting one (`SourceInfoUtil.java:30`)
  without negotiating a position encoding, and so relies on UTF-16 columns, LSP's default. Twelve of its files use
  columns, including semantic tokens, whose lengths are in the same encoding, and the debug adapter; the MCP server
  reports columns too (`PureTools.java`);
- Studio and the Pure IDE, whose Monaco editors count UTF-16 code units. Unless they convert, which this review did
  not check since legend-studio is outside it, their markers already start one unit early for each supplementary
  character earlier on the line, because legend-engine's columns count code points.

### 1.4 The language's string functions

The functions' documentation speaks of characters without defining them. In both Java engines:

| Function | Unit or rule | Code |
|---|---|---|
| `length` | UTF-16 code units | `CompiledSupport.java:1569`; interpreted `Length.java:49` |
| `substring`, `substr` | UTF-16 indexes; can split a pair | `CompiledSupport.java:1546`; `SubString.java:56` |
| `indexOf`, `lastIndexOf`, `idxOf` | UTF-16 indexes | `CompiledSupport.java:1531` |
| `reverseString` | Code points: `StringBuilder.reverse` keeps pairs together (verified) | `CoreHelper.java:1219`; `ReverseString.java:51` |
| `toUpper`, `toLower` | Java's full case mapping in the JVM's default locale; the length can change (U+00DF uppercases to `SS`) | `CoreHelper.java:1224`; `ToUpper.java:51` |
| `trim` | Removes code points up to U+0020 (`String.trim`) | `CoreHelper.java:1234`; `Trim.java:51` |
| `ltrim`, `rtrim` | Remove Java whitespace (`StringUtils.stripStart` and `stripEnd`), so they disagree with `trim`: only `trim` removes U+0001, and only `ltrim` and `rtrim` remove U+2003 (verified) | `CoreHelper.java:1239`; `LTrim.java:52` |
| `split` | `StringTokenizer`: the separator is a set of code points, and empty pieces are dropped, though the documentation says the separator is matched literally. `'ab'->split('ba')` returns an empty collection (verified). | `CompiledSupport.java:207`; `Split.java:54` |
| `compare`, `sort`, `<` | UTF-16 code unit order (`String.compareTo`) | `CompiledSupport.java:583` |
| `ascii` (legend-engine) | The first UTF-16 code unit | `FunctionsHelper.java:285` |
| `char` (legend-engine) | The argument cut to 16 bits | `FunctionsHelper.java:290` |
| `encodeBase64`, `decodeBase64`, `encrypt`, `decrypt` (legend-engine) | UTF-8 in compiled mode; the platform default charset in interpreted mode | `FunctionsHelper.java:249`; `EncodeBase64.java:52`; `Cipher.java:56` |

The Java platform binding generates the same Java: `length` becomes `(long) s.length()`, and `substring` becomes
`s.substring(...)` (`stringLibraryTests.pure:98` and `:61`).

### 1.5 Pushed-down execution

Relational dialects map `length` to their own functions. For the string `a`, U+1F600, `b`:

| Where | Function | Result |
|---|---|---|
| Java engines, and the Java platform binding | `String.length()` | 4 |
| H2 2.1.214, a default PCT store (`h2Extension2_1_214.pure:237`) | `char_length` | 4 (verified); `SUBSTRING(s, 3, 1)` returns an unpaired surrogate |
| DuckDB 1.4.5, a default PCT store (`duckdbExtension.pure:254`) | `length` | 3 (verified); `substring(s, 3, 1)` returns `b` |
| DB2 (`db2Extension.pure:165`) | `CHARACTER_LENGTH(s, CODEUNITS32)`: code points, by request | 3 |
| Snowflake, BigQuery, Databricks, and Trino | Functions their vendors document as counting characters | 3 expected; not verified |
| SQL Server (`sqlServerExtension.pure:141`) | `len`, documented to count UTF-16 code units unless the collation supports supplementary characters, and to ignore trailing spaces | 4 expected; not verified |
| ClickHouse (`clickHouseExtension.pure:118`) | `length`, documented to count bytes (`lengthUTF8` counts code points) | 6 expected; not verified |

The two default PCT stores disagree with each other, and Java agrees with H2. No PCT test uses a supplementary
character, so nothing reports it.

### 1.6 Serialization

| Aspect | Today |
|---|---|
| String bytes | UTF-8 (`StringWriterV3.java:134`, `AbstractBinaryReaderWriter.java:56`). An unpaired surrogate is written as `?` (0x3F) without an error (verified). Readers decode leniently: malformed UTF-8 becomes U+FFFD. |
| Length prefixes | UTF-8 byte counts, in the string index and in m4's `writeString`. |
| String index order | UTF-16 length, then UTF-16 code unit order (`StringWriterV3.java:307`). |
| Other orders | Module metadata (`String.compareTo`, at the five sites that the byte-identity plan lists in its section 1.1), node properties by name (`SerializerV1.java:544`), and candidate paths in reference ID generation, by UTF-16 length and then code unit order (`ReferenceIdGenerator.java:412`). The last is a generation rule: it decides content, not only its encoding. |
| Reference ID text | Keys are escaped by `StringEscape.escape` (`GraphPath.java:871`), which writes each code unit outside U+0020 to U+007F as a backslash-u escape (`StringEscape.java:36`). IDs are ASCII, with UTF-16 escapes. |
| Source information | Six integers per node, in the unit of whichever parser produced them. No unit is recorded. |
| File names | Each path segment, extension included, is limited to 126 UTF-16 code units, to fit 255 bytes of UTF-16 (`FilePathTools.java:24`). The overflow hash runs over code points (`FilePathTools.java:205`). Linux file systems such as ext4 limit a name to 255 bytes, which 126 characters from U+0800 to U+FFFF exceed in UTF-8 (378 bytes). This cannot happen today, since legend-pure's grammar allows only ASCII element names. |

### 1.7 Platform dependencies outside Unicode

- **Default charset.** On the Windows 11 machine used for this review, JDK 17's default charset is windows-1252,
  and JDK 25's is UTF-8 (verified); JDK 18 made UTF-8 the default everywhere. Before JDK 18, Linux takes the
  default from the locale, which in minimal containers is often the C locale, and so ASCII. legend-engine builds on
  JDK 17, and legend-pure supports JDK 11 to 25. Code that uses the default charset therefore behaves differently
  with the OS, the locale, and the JDK:
  - legend-pure: writing and reading the `.par` indexes (`PureRepositoryJarBuilder.java:126` and `:155`,
    `PureRepositoryJarMetadata.java:96` and `:117`), and `CsvReader.java:110`;
  - legend-engine: the interpreted `encodeBase64`, `encrypt`, and `decrypt` (`EncodeBase64.java:52`,
    `Cipher.java:56`, and `Decipher.java:56` and `:63`), whose compiled counterparts use UTF-8, so the two modes
    disagree for non-ASCII text on Windows with JDK 17; JSON property encryption
    (`JsonPropertySerialization.java:105` and `:121`); and the Pure IDE server's responses (`PureSession.java:185`,
    `Suggestion.java`);
  - legend-shared: the basic authentication header (`BasicAuthenticationProvider.java:36`), and the static server's
    copy of its configuration (`Server.java:55` to `:64`).
- **Default locale.** `String.toLowerCase()` and `toUpperCase()` use the JVM's default locale. In Turkish and
  Azerbaijani, `I` lowercases to U+0131 (dotless i), and `i` uppercases to U+0130; Lithuanian has rules of its own.
  - The M3 parser derives function names from operator enum names this way
    (`AntlrContextToM3CoreInstance.java:641`, `:1796`, and `:1911`). With a `tr-TR` default locale, `MINUS`,
    `TIMES`, and `DIVIDE` become `m\u0131nus`, `t\u0131mes`, and `d\u0131v\u0131de` (verified), so any source that
    uses `-`, `*`, or `/` would fail to compile.
  - Pure's `toUpper` and `toLower` follow the server's locale.
  - legend-sdlc derives Maven artifact IDs from enum names the same way (`MavenProjectStructure.java:387`,
    `MultiModuleMavenProjectStructure.java:519`).
  - So do build-time generators (`M3ToJavaGenerator.java:1070` and `:2367`, and
    `M3LazyCoreInstanceGenerator.java:877` through the default-locale overloads in `TextTools.java:335`).
- **Unicode tables.** Character properties come from the JDK. The compiler uses them in the multi-line layout
  (`Character.isWhitespace`) and in graph path validation (`Character.isLetterOrDigit`, `GraphPath.java:1126`).
  Java's whitespace set is the same on JDK 11, 17, 21, and 25 (verified): U+0009 to U+000D, U+001C to U+0020,
  U+1680, U+2000 to U+2006, U+2008 to U+200A, U+2028, U+2029, U+205F, and U+3000. It is not Unicode's `White_Space`
  property, which Rust's `char::is_whitespace` follows: that adds U+0085, U+00A0, U+2007, and U+202F, and leaves
  out U+001C to U+001F.
- **File systems.** Windows and macOS are case-insensitive by default, both for PELT directories (the byte-identity
  plan's decision 7) and for legend-sdlc's entity files. Windows also reserves device names. On the Windows 11
  machine used for this review, `aux`, `con`, and `lpt1` work as directory names, but `nul` does not: creating it
  does nothing, and writing `nul/x.pelt` fails (verified). Earlier Windows versions reserve `CON`, `PRN`, `AUX`,
  `NUL`, `COM1` to `COM9`, and `LPT1` to `LPT9`, with or without an extension.
- **Numbers as text.** Pure renders Floats with `DecimalFormat` and English symbols (`CompiledSupport.java:134`),
  which does not depend on the locale, and gave the same output on JDK 17 and 25 for 200,008 doubles (verified).
  `Double.toString` changed in JDK 19: 2,695 of 1,000,008 doubles print differently on JDK 17 and 25, such as
  `2.0E23`, which JDK 17 prints as `1.9999999999999998E23` (verified). Anything that renders Floats with
  `Double.toString` depends on the JDK.

## 2. Requirements

| # | Requirement |
|---|---|
| R1 | The same sources compile to the same graph and the same PELT files on Linux, Windows, and macOS, with any supported JDK, default locale, default charset, and line-ending convention. |
| R2 | A non-Java implementation can follow every rule with ordinary library support. |
| R3 | Positions shown in editors (Studio, the Pure IDE, and LSP clients) point at the intended characters. |
| R4 | A Pure function gives the same answer in Java as in every store it is pushed down to, or the difference is documented for that store. |
| R5 | No string changes silently: encoding, decoding, and slicing never replace or drop characters. |
| R6 | Existing binaries keep loading, and format changes follow the roadmap's shared rules. |

Today, R1 fails on the default locale, R3 and R4 fail after supplementary characters, and R5 fails for unpaired
surrogates and malformed input.

## 3. The string value

- **V1. Sequences of UTF-16 code units** (today). Unpaired surrogates exist in memory, but PELT turns them into
  `?`, and UTF-8 text such as JSON, Rust strings, and most databases cannot hold them. A second implementation
  would need an encoding such as WTF-8 only to represent them.
- **V2. Sequences of Unicode scalar values,** that is, well-formed text. This is what UTF-8, Rust, protobuf, and
  I-JSON (RFC 7493) require. Java keeps using `String`, and the rule is enforced where strings are created or
  enter.
- **V3. Sequences of code points, surrogates included** (Python's model). The same storage problem as V1.

**Recommendation: V2.** It removes the one kind of string that cannot cross a language or format boundary, and with
it the question of how to store one. Enforcement points:

- **Literals:** an unpaired surrogate escape is a parse error, with its position (legend-pure's `StringEscape`, and
  legend-engine's `PureGrammarParserUtility.fromGrammarString`). A pair of escapes stays valid. A code point
  escape, such as `\u{1F600}`, would let a supplementary character be written as one escape (decision 12).
- **Functions that create strings:** `substring` and its relatives under UTF-16 indexes, which cannot split a
  character under code point indexes (section 5); legend-engine's `char`, which should reject surrogates and values
  above U+10FFFF; and decoding functions such as `decodeBase64`, which should decode strictly.
- **Data from outside** (stores, JSON, and files): reject it, or replace with U+FFFD and say so (decision 2).
- **PELT writers:** reject, as the byte-identity plan's decision 6 recommends. Under V2 this is a backstop.

The byte-identity plan already scans existing models for unpaired surrogates before writers reject them. The same
scan informs the rule for literals.

## 4. Source coordinates

| | C1. UTF-16 code units | C2. Code points | C3. UTF-8 bytes |
|---|---|---|---|
| Used today by | legend-pure's M3 parser and most of its DSL parsers | legend-engine's start columns, and three legend-pure parsers | Nothing |
| Matches | Monaco, VS Code, LSP's default, Java, JavaScript, and C# | Python, Rust's `chars`, LSP's `utf-32`, ANTLR's `CharStreams`, and the language under L2 | Byte offsets in Rust, C, and Go; tree-sitter; LSP's `utf-8` |
| Columns it changes in these repositories | legend-engine's, after a supplementary character: none in practice, since their sources have none | legend-engine's end columns, where they are wrong today | Every column after a non-ASCII character on its line, which documentation makes common |
| ANTLR support | `ANTLRInputStream`, deprecated since ANTLR 4.7 | `CharStreams`, the supported API | A custom char stream |
| What an emoji counts as | Two | One | Four |
| Conversion for UTF-16 editors | None | Per line, from the line's text | Per line, from the line's text |

Two more options are rejected. Grapheme clusters and display widths depend on the Unicode version or the font, so
they cannot be reproduced. Offsets from the start of the file, in place of lines and columns, would change m4's
`SourceInformation`, the metamodel, PELT, and every consumer for no gain over C2.

Every option is cheap in any language, since a lexer can count all three units at once. The choice is about
meaning, alignment, and conversions.

**Rules that hold whichever unit is chosen,** to be written into the byte-identity plan's rules for compilers (its
Phase 6):

- Lines end at LF, CRLF, or CR, as LSP and the Java Language Specification define them, or at LF only, as today
  (decision 4). Terminators never take a column, so a CRLF file and an LF file have the same coordinates. A lexer
  can get this by normalizing terminators in the char stream it reads, so that token text and indexes stay
  consistent.
- A leading U+FEFF is not part of the text. It is dropped before lexing and takes no column (decision 5).
- A tab is one unit.
- Columns are 1-based. An end column is the column of the last character, in the same unit as the start column,
  computed from the token's stop index rather than from the length of its Java text.
- A position inside an island, such as a `#...#` block or a section, adds the island's offset in the same unit.

**Recommendation: C2.**

- legend-engine already counts start columns in code points. C1 would change how every legend-engine parser
  computes columns, either by moving its 48 files to a deprecated stream type or by converting every position; C2
  moves nine legend-pure parser setups to the supported stream type.
- It changes no column in legend-pure's sources or legend-engine's, or in any source without supplementary
  characters, so test expectations and PELT bytes stay as they are. Only positions after a supplementary character
  on the same line change.
- It counts what a reader counts, apart from combining sequences.
- It agrees with the language under L2 (section 5), so Pure code that combines `sourceInformation` columns with
  `substring` stays consistent.
- Code that takes text through the char stream (`getText(Interval)`), as nearly all the grammar code in both
  repositories does, is unaffected. Code that indexes the Java string with token indexes must change; the one case
  found is `FunctionDescriptor.java:275`, which already mixes the two, since it uses a code point stream.
- The cost is conversion where an editor counts UTF-16 code units: the language server converts with the document's
  text, or uses `utf-32` where a client offers it, and Studio and the Pure IDE convert in the browser. Studio needs
  that conversion for legend-engine's columns already.

**PELT.** No format change: files record no unit, and the change alters only positions after a supplementary
character on the same line. Files built before the change keep UTF-16 columns on such lines, and nothing needs to
detect that: the consistency checks ignore source information, and the byte-identity plan's corpus is versioned by
release. The release notes say from which release columns count code points.

## 5. Counting in the language

- **L1. UTF-16 code units** (today, but written down). Nothing changes, and `length` stays O(1) in Java. But
  `substring` can split a pair, which V2 rules out, so slices would have to reject cuts inside pairs. An emoji
  counts as two, and most stores disagree.
- **L2. Code points.** Agrees with V2, since no slice can split a character; with most stores; with Python and
  Rust; with `reverseString` already; and with C2. The costs:
  - Results change for strings with supplementary characters: rare in models, but possible in data, such as emoji
    in free text.
  - Speed in Java: `codePointCount` returns at once for Latin-1 strings and scans the others, and
    `offsetByCodePoints` always scans (verified in JDK 17's sources). A helper that uses UTF-16 indexes directly
    when `codePointCount` equals `length` keeps ASCII and Latin-1 text as fast as today.
  - Stores: H2 needs a code point formulation or a PCT exclusion; ClickHouse needs its UTF-8 functions
    (`lengthUTF8`, `substringUTF8`, and `positionUTF8`); and SQL Server needs a formulation that counts a pair once
    and keeps trailing spaces.
  - The Java platform binding's generated code changes, and so do the tests that expect its code.
- **L3. UTF-8 bytes.** Matches only byte-counting functions, such as ClickHouse's `length`, and surprises users.
  Rejected.
- **L4. Grapheme clusters.** Depend on the Unicode version and cost a segmentation per call, and few stores can
  compute them. Rejected as the default, though a separate function could offer them.

Functions that do not count, such as `contains`, `startsWith`, `endsWith`, `replace`, and equality, give the same
results under L1 and L2 for well-formed strings.

**Recommendation: L2, as a language decision** (decision 6). It changes what Pure functions return, so it needs its
own decision and release note. PELT content does not depend on it, so the byte-identity work does not wait for it.
legend-pure and legend-engine change together, with PCT tests that use supplementary characters on every store, and
the functions' documentation says that a character is a Unicode scalar value. `trim` and `split` are settled at the
same time (decisions 7 and 8).

## 6. Serialization

### 6.1 Length prefixes

UTF-8 byte counts, as today. A reader can skip or allocate without decoding, and every language has the count at
hand. The specification says "the length in bytes of the UTF-8 encoding", and so do the byte-identity plan's digest
encoding and the consistency checks' encoding of paths.

### 6.2 Well-formed UTF-8

Files hold well-formed UTF-8, and writers reject unpaired surrogates (section 3). Loaders may keep decoding
leniently, for speed. The conformance tools and the corpus tests decode strictly, so a malformed file fails where
it is checked.

### 6.3 Orders

| | O1. UTF-16 (today) | O2. Code points | O3. UTF-8 bytes |
|---|---|---|---|
| Sort key | UTF-16 length, then code units | Code point count, then code points | Byte length, then bytes |
| Existing files that change | None | Those with a supplementary character in a sorted string | Those with any non-ASCII sorted string, such as documentation |
| Java | Built in | A comparator and a count | The writer has the bytes anyway |
| Rust | `encode_utf16().count()`, and comparing `encode_utf16()` iterators | `chars().count()`, and `cmp`, since byte order is code point order | `len()` and `cmp` |
| C | A short loop | A short loop | `memcmp` |

**Recommendation:** O1 for every existing version, specified exactly, with test vectors that put supplementary
characters next to U+E000 to U+FFFF. It is cheap in any language, and it changes nothing. A string index, metadata,
or element version created later for another reason uses O3. Reference ID generation keeps its order until a new
reference ID version (see the [versions plan](2026-10-16-pelt-path-provider-and-reference-id-versions.md)), since
that order decides content.

### 6.4 Reference ID text

Keep the backslash-u escapes of UTF-16 code units. They are an encoding rule, ASCII, and easy to produce anywhere
(Rust has `encode_utf16`). The specification states them (the byte-identity plan's Phase 0, item 5).

### 6.5 File names

- legend-pure's grammar allows only ASCII element names, so path provider V1 can say so, and serialization can
  report any other path segment as a build-time error, after a scan of existing models. Two more checks belong with
  it, as build-time errors on every platform: the case-only collisions of the byte-identity plan's decision 7, and
  Windows device names compared without regard to case (`NUL` at least; all of them, for earlier Windows versions).
- If element names outside ASCII are ever needed, for example if legend-engine models with quoted identifiers
  become PELTs, a new path provider version limits each segment to 255 bytes of UTF-8, and fixes a normalization
  form (NFC), since file systems treat normalization differently (HFS+ stores decomposed names). That is out of
  scope until then.

### 6.6 Source information

No format change (section 4).

## 7. The mixed approach

The approach in question uses code points for source coordinates and string lengths, and UTF-8 lengths for
serialization.

- **It is coherent.** The numbers people see (columns, lengths, and indexes) count characters, and the numbers
  formats use (length prefixes) count bytes. The two never meet: no serialized length is visible to a user, and
  columns are stored as plain integers whatever they count.
- **Its serialization half is already in place.** Length prefixes are UTF-8 byte counts today. What the approach
  adds is C2 and L2.
- **What it leaves in UTF-16:** the existing orders, the file-name limit, and the escapes in reference IDs. They
  stay as specified encoding rules of existing versions (O1), which every language can follow cheaply, and later
  versions use UTF-8 throughout (O3). UTF-16 then survives only inside format rules, never in a number that a
  person or a Pure program sees.
- **The alternatives do worse:**
  - code points in length prefixes too: readers could not skip or allocate without decoding, and Rust and C would
    count twice, for no benefit;
  - UTF-8 bytes for columns and lengths too: natural for byte-oriented tools, but strange to users, a change to
    existing output wherever non-ASCII text precedes a position, and a conversion in every editor;
  - UTF-16 everywhere, written down: no change now, but it writes Java's representation into the language and its
    coordinates, keeps unpaired surrogates, and leaves legend-engine and the stores out of line.

**Recommendation:** the mixed approach, with V2, and with O1 for existing versions.

## 8. Hazards to fix whatever is chosen

Each is a small, separate fix that can start now:

1. **Default charset.** Name UTF-8 at the sites in 1.7, and add a build rule that forbids default-charset APIs,
   such as the forbidden-apis Maven plugin's `jdk-unsafe` signatures, which also catch default locales and time
   zones.
2. **Default locale.** Use `Locale.ROOT` for anything that becomes an identifier: the M3 parser's operator names,
   legend-sdlc's artifact IDs, the generators, and the default overloads in `TextTools`. Pure's `toUpper` and
   `toLower` follow (decision 11); their results change only where the default locale is Turkish, Azerbaijani, or
   Lithuanian.
3. **Byte order marks.** Drop one leading U+FEFF when reading source text: in legend-pure's code storages, in
   legend-sdlc's text entity files, and at legend-engine's grammar entry points.
4. **Malformed UTF-8 in sources.** Report an error that names the file and byte offset, instead of substituting
   U+FFFD.
5. **Line endings.** Add a `.gitattributes` file (`* text=auto`, and `*.pure text eol=lf`) to each of the four
   repositories. Include a CRLF checkout in the byte-identity plan's Phase 2 matrix, and CRLF and CR-only sources
   in its corpus.
6. **End columns.** Compute them from the token's stop index, in the stream's unit: legend-pure's
   `AntlrSourceInformation.java:86`, `:124`, and `:146`, legend-engine's
   `ParseTreeWalkerSourceInformation.java:111`, and any other place that adds a token text's Java length to a
   column.
7. **`split` and `trim`.** Make each function's implementation and documentation agree (decisions 7 and 8).
8. **Continuous integration.** Run the compiler and PCT suites once with `-Duser.language=tr -Duser.country=TR`,
   and once on Windows with JDK 17, where the default charset is windows-1252.

## 9. Scope by project

For the recommended combination. Items marked *now* are the hazards of section 8.

### legend-shared

- It handles no source information and no Pure strings.
- Now: name UTF-8 in `BasicAuthenticationProvider.java:36` (the only charset RFC 7617 names for basic
  authentication), and in the static server's copy of its configuration (`Server.java:55`).
  `LegendSecurityLogic.java:212` lowercases a user agent with the default locale, which is harmless for the text it
  looks for, but `Locale.ROOT` is the rule.
- Optionally, the same build rule against default-charset and default-locale APIs.

### legend-pure

- **Parsers:** move the nine `ANTLRInputStream` setups to `CharStreams.fromString` (C2); compute end columns from
  stop indexes; drop a leading BOM; apply the line terminator rule; and fix `FunctionDescriptor.java:275`.
- **Literals:** reject unpaired surrogate escapes (`StringEscape`), and optionally add a code point escape.
- **Code storages:** decode UTF-8 strictly, with a clear error (`FSCodeStorage.java:168`,
  `ClassLoaderFileNode.java:40`, and the others).
- **Language server and MCP server:** convert code point columns into the client's position encoding using the
  document's text, or negotiate `utf-32`; that covers the twelve language server files that use columns, semantic
  token lengths and the debug adapter among them, and `PureTools.java`.
- **String functions (L2):** `length`, `substring`, `indexOf`, and `lastIndexOf` in both engines
  (`CompiledSupport.java:1531` to `:1572`, and the interpreted natives), through a shared helper for code point
  offsets; PCT tests with supplementary characters; documentation that defines a character; `toUpper` and `toLower`
  with `Locale.ROOT`; and `split` and `trim` as decided.
- **Serialization:** writers reject unpaired surrogates; non-ASCII path segments, case-only collisions, and Windows
  device names are build-time errors, each after its scan.
- **Specification:** `pelt-format.md` (the byte-identity plan's Phase 0) states UTF-8 byte lengths, the O1 orders
  with test vectors, the reference ID escapes, and the file-name rules. The rules for compilers (its Phase 6) state
  the coordinate rules of section 4.
- **Corpus** (its Phase 3): supplementary characters in strings, in quoted property names, and before tokens on the
  same line; U+E000 to U+FFFF beside supplementary characters, for order; and sources with CRLF, CR only, and a
  BOM.
- **Now:** the default-locale and default-charset fixes listed in 1.7.
- **One home for the rules:** m4's tools package, for example an extension of `TextTools`, holding code point
  length and slicing, the O1 and O3 comparators, strict decoding, BOM removal, line terminators, and conversion
  between code point and UTF-16 columns. legend-engine and the language server then use the same code.

### legend-engine

- **Grammar:** end columns (`ParseTreeWalkerSourceInformation.java:111`, and any other place that adds a token
  text's length to a column); island offsets in code points; drop a leading BOM at the grammar entry points; and
  reject unpaired surrogate escapes in `PureGrammarParserUtility.fromGrammarString`.
- **Protocol:** document that `SourceInformation` columns count code points. No schema change.
- **Pure functions:** `ascii` returns the first code point, and `char` accepts only scalar values
  (`FunctionsHelper.java:285` and `:290`, and the interpreted `ASCII` and `Char`). Now: name the charset in the
  interpreted `encodeBase64`, `encrypt`, and `decrypt`, and in `JsonPropertySerialization`.
- **Java platform binding (L2):** generated code for `length`, `substring`, `indexOf`, and their relatives uses
  code point helpers, and the tests that expect its code change.
- **Relational dialects (L2):** code point functions for length, slicing, and position in each dialect; PCT tests
  with supplementary characters on every store, the cloud profile included; and manifest exclusions where a store
  cannot comply.
- **Pure IDE server:** now, name the charset of its responses.

### legend-sdlc

- Entity paths are ASCII already (`EntityPaths.java`).
- Reject an entity path that differs from an existing one only in case. GitLab accepts both, but Windows and macOS
  working copies, legend-sdlc-local's among them, cannot hold both files.
- Now: `Locale.ROOT` for artifact IDs (`MavenProjectStructure.java:387`,
  `MultiModuleMavenProjectStructure.java:519`).
- Text entity files (`.pure`): drop a leading BOM before parsing, and keep naming UTF-8, as `PureEntitySerializer`
  does.
- Test generation budgets class names in mixed units: a name's UTF-8 bytes against a limit computed from UTF-16
  byte lengths that include byte order marks (`AbstractTestClassGenerator.java:36` and `:79`). It is harmless, but
  one shared rule, 255 bytes of UTF-8 per name, would serve here and in 6.5.

### Outside these four repositories (not reviewed)

- **legend-studio:** convert code point columns into Monaco's UTF-16 columns using each line's text, for Studio's
  error markers and for the Pure IDE front end.
- **legend-depot,** and anything else that stores entity paths as file names: check how paths that differ only in
  case are handled.

## 10. Recommendation

1. **The value:** strings are sequences of Unicode scalar values (V2), enforced where they are created or enter.
   PELT writers reject unpaired surrogates, which settles the byte-identity plan's decision 6.
2. **Coordinates:** columns count code points (C2), under the rules in section 4.
3. **The language:** string functions count code points (L2), as a separate language decision.
4. **Serialization:** the mixed approach. UTF-8 with byte-count prefixes, as today; existing orders and escapes
   specified as UTF-16 rules (O1); and UTF-8 throughout in later versions (O3).
5. **Hazards:** fix those in section 8 now.

In one table:

| Where | Today | Recommended |
|---|---|---|
| The string value | UTF-16 code units, possibly ill-formed | Unicode scalar values (V2) |
| Source columns | UTF-16 code units in legend-pure; code points, with mixed end columns, in legend-engine | Code points (C2) |
| Lengths, indexes, and slices in Pure | UTF-16 code units | Code points (L2) |
| PELT length prefixes | UTF-8 bytes | UTF-8 bytes, unchanged |
| PELT orders | UTF-16 length, then code units | Unchanged in existing versions (O1); UTF-8 bytes in later ones (O3) |
| Reference ID escapes | UTF-16 code units | Unchanged |
| PELT file-name limit | 126 UTF-16 code units per segment | ASCII path segments; 255 bytes of UTF-8 in any later path provider version |
| Editor positions (LSP, Monaco) | UTF-16 code units | Converted from code points where they are shown |

## 11. Order of work

1. **Now, independently:** the fixes in section 8, one per bug; the scans (unpaired surrogates, non-ASCII path
   segments, case-only collisions, and device names); and PCT tests that record each store's current results for
   supplementary characters, with exclusions where needed, so that the differences are visible before L2 changes
   them.
2. **With the byte-identity plan's Phases 0 and 3:** the string rules in `pelt-format.md` (6.1 to 6.5), and the
   corpus cases.
3. **After decisions 1, 3, 4, and 5:** V2 enforcement, C2 in legend-pure's parsers and language server,
   legend-engine's end columns, and the front ends' conversions, under one release note.
4. **After decision 6:** L2 in both Java engines, the Java platform binding, and the relational dialects, with PCT
   on every store.
5. **Only with a version made for another reason:** O3.

## 12. Decisions to make

1. **The string value:** V2 (recommended), or V1.
2. **Unpaired surrogates arriving at run time** from stores, JSON, or files: reject them, or replace them with
   U+FFFD.
3. **The column unit:** C2 (recommended), C1, or C3.
4. **Line terminators:** LF, CRLF, and CR (recommended, as in LSP), or LF only (today).
5. **A leading byte order mark:** drop it (recommended), or reject it with a clear message.
6. **Counting in the language:** L2 (recommended), or L1 written down.
7. **`trim`:** for all three functions, either Java whitespace, as `ltrim` and `rtrim` use today, or code points up
   to U+0020, as `trim` uses today.
8. **`split`:** a literal separator, as documented, or a set of characters, as implemented.
9. **Orders in existing versions:** O1 written down (recommended), or new versions now.
10. **Path segments in path provider V1:** ASCII only (recommended), or a byte limit in a new path provider
    version.
11. **Case mapping:** `Locale.ROOT` everywhere (recommended), with any locale-aware variants as separate functions.
12. **A code point escape in literals,** such as `\u{1F600}`: add it, or not.

## Outside this document

- Collation (language-aware ordering), normalization of user data, and grapheme-aware functions.
- Regular expressions: `regexpLike`, `regexpReplace`, and the other regular expression functions use
  `java.util.regex` (`FunctionsHelper.java:315`), whose dialect a non-Java runtime would have to match.
- Number formatting, beyond the note in 1.7.
