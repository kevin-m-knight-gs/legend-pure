# PELT format

Status: draft, 2026-11-22. This document describes the PELT files that legend-pure writes and reads at `a67907c83`.
Three rules in it were decided on 2026-11-20: strings in files are well-formed UTF-8 (section 2.1), existing
versions keep their UTF-16 orders (section 2.3), and path segments under path provider version 1 are ASCII
(section 8.2). Three parts are proposals, not yet decided: the digest encoding (section 10), the text encoding
(section 11), and the rule that Float and Decimal numerals compare as written (section 5.1). Where the Java
implementation departs from this document, [section 12](#12-departures-of-the-java-implementation) lists the
departure. Each departure is a bug.

## 1. Scope

PELT serialization writes each compiled module as many files: one for each concrete element, and, for its metadata,
five files and one more for each element that the module's elements refer back to. This document specifies:

- the serializable form of each kind of file, which says what the file holds apart from any encoding (section 5);
- the binary encodings that legend-pure writes and reads (sections 3, 4, 6, and 7);
- where the files go (section 8), and the syntax of reference IDs (section 9);
- a digest encoding and a content digest, both defined on serializable forms (section 10);
- a text encoding of serializable forms, for test corpora (section 11).

It does not specify how a compiler produces serializable forms from a compiled graph: which instances an element
holds and in what order, how reference IDs are generated, how module metadata is derived from elements, and what
source information records. Those are rules for compilers, which a later revision adds.

Someone who reads this document should be able to write a reader and a writer of these files, and compute content
digests, without reading legend-pure's code.

### 1.1 Versions

Five extensions each have a version, chosen independently:

| Extension | Versions | Written by default | Specified here |
|---|---|---|---|
| Element serializer | 1, 2 | 2 | 1 and 2 (section 6) |
| Module metadata serializer | 1, 2, 3 | 3 | 1, 2, and 3 (section 7) |
| String index | 0, 1, 2, 3 | 3 | 3 (section 4) |
| Path provider | 1 | 1 | 1 (section 8) |
| Reference IDs | 1 | 1 | 1, its syntax only (section 9) |

Element version 2 compresses element version 1's payload, and module metadata version 3 compresses version 2's.
Element version 1 and metadata versions 1 and 2 were the defaults in earlier releases, so files written with them
exist. String index versions 0 to 2 can be read, but version 3 has been the default since PELT serialization was
added, so this document does not specify them.

A file's header names its element or metadata version. The string index version is the first field of each payload
that has one, which for element version 2 and metadata version 3 is the inflated payload. The reference ID version of
an element file is in its header, and that of external reference and back reference metadata in their payloads.
Nothing in a file names the path provider version: readers compute file names with version 1.

### 1.2 Conventions

- **Must** states a requirement. So does every statement of what an encoding writes, such as a width, an order, or a
  choice between two forms, whether or not it says must. A file that breaks one does not conform, and a writer must
  not write one. Conformance tools reject such a file. Other readers may accept it, unless this document says that
  they reject it. But a reader that cannot decode a file into a form fails, rather than produce some other form: for
  example, on an unassigned code, on a string ID that names no string, or on a file that ends before its counts and
  lengths say it does.
- **Integers** are big-endian and two's complement: `i8`, `i16`, `i32`, and `i64` take 1, 2, 4, and 8 bytes. `u8`
  is an unsigned byte. The one exception, three-byte string IDs, is in section 4.5.
- **Byte values** are in hexadecimal, as in `0xC0`. A byte that combines fields is written as a sum of hexadecimal
  values, such as `0xC0 + w`, where the parts never overlap.
- **Positions in strings** are counted in UTF-16 code units, from 0, and `s[a, b)` is the part of `s` from position
  `a` up to but not including `b`. Every delimiter in this document is ASCII, so splitting at one never splits a
  character.
- **Strings** are sequences of Unicode scalar values (section 2.1).

## 2. Strings

### 2.1 Well-formed text

Every string in a PELT file is well-formed: a sequence of Unicode scalar values, which excludes unpaired surrogates,
stored as UTF-8. A writer must reject a string that holds an unpaired surrogate, and say which element and which
part of it holds the string, rather than write anything in its place. Readers may decode leniently, for speed, but
conformance tools decode strictly, so that a malformed file fails where it is checked.

### 2.2 Lengths

A length that precedes UTF-8 bytes counts the bytes.

### 2.3 Orders

The format sorts strings in two orders. Both are defined on UTF-16 code units, the representation of Java's
strings, and every existing version keeps them. A version created later for another reason may sort by UTF-8
bytes instead.

- **Code unit order:** compare the strings' UTF-16 code units, as unsigned 16-bit numbers, from the first, until two
  differ; the string with the smaller unit sorts first. A string that is a proper prefix of another sorts first.
  This is Java's `String.compareTo`.
- **Index order:** the string with fewer UTF-16 code units sorts first. Strings of equal length sort in code unit
  order. Only the string index uses it (section 4.3).

A string's UTF-16 length is its number of characters plus its number of supplementary characters (U+10000 and
above), which take two units each.

Code unit order is code point order, and so UTF-8 byte order, with one exception: a supplementary character sorts
before every character from U+E000 to U+FFFF, since its first code unit is a surrogate (U+D800 to U+DBFF). So in
code unit order, U+10000 sorts before U+E000, although U+E000 has the smaller code point. Comparing the strings'
UTF-16BE encodings byte by byte also gives code unit order.

### 2.4 Special strings

The string index (section 4) gives 87 strings fixed negative IDs, so that they never need a definition. In order,
with their IDs:

| ID | String | ID | String |
|---|---|---|---|
| -1 | (none: a null string) | -45 | `functions` |
| -2 | (the empty string) | -46 | `meta` |
| -3 | `::` | -47 | `metamodel` |
| -4 | `/` | -48 | `property` |
| -5 | `.` | -49 | `pure` |
| -6 | `Boolean` | -50 | `relationship` |
| -7 | `Byte` | -51 | `string` |
| -8 | `Date` | -52 | `type` |
| -9 | `DateTime` | -53 | `children` |
| -10 | `Decimal` | -54 | `classifierGenericType` |
| -11 | `Float` | -55 | `constraints` |
| -12 | `Integer` | -56 | `expressionSequence` |
| -13 | `LatestDate` | -57 | `func` |
| -14 | `Number` | -58 | `genericType` |
| -15 | `StrictDate` | -59 | `multiplicity` |
| -16 | `StrictTime` | -60 | `multiplicityArguments` |
| -17 | `String` | -61 | `multiplicityParameters` |
| -18 | `meta::pure::metamodel::type::Any` | -62 | `owner` |
| -19 | `meta::pure::metamodel::relationship::Association` | -63 | `postConstraints` |
| -20 | `meta::pure::metamodel::type::Class` | -64 | `preConstraints` |
| -21 | `meta::pure::metamodel::function::ConcreteFunctionDefinition` | -65 | `properties` |
| -22 | `meta::pure::metamodel::type::Enumeration` | -66 | `propertiesFromAssociations` |
| -23 | `meta::pure::metamodel::import::ImportGroup` | -67 | `qualifiedProperties` |
| -24 | `meta::pure::metamodel::function::LambdaFunction` | -68 | `qualifiedPropertiesFromAssociations` |
| -25 | `meta::pure::functions::collection::List` | -69 | `rawType` |
| -26 | `meta::pure::functions::collection::Map` | -70 | `resolvedEnum` |
| -27 | `meta::pure::metamodel::function::NativeFunction` | -71 | `resolvedNode` |
| -28 | `meta::pure::metamodel::type::Nil` | -72 | `resolvedProperty` |
| -29 | `Package` | -73 | `returnMultiplicity` |
| -30 | `meta::pure::functions::collection::Pair` | -74 | `returnType` |
| -31 | `meta::pure::metamodel::type::PrimitiveType` | -75 | `stereotypes` |
| -32 | `meta::pure::metamodel::extension::Profile` | -76 | `tag` |
| -33 | `meta::pure::metamodel::function::property::Property` | -77 | `taggedValues` |
| -34 | `meta::pure::metamodel::function::property::QualifiedProperty` | -78 | `typeArguments` |
| -35 | `meta::pure::metamodel::ReferenceUsage` | -79 | `typeParameters` |
| -36 | `meta::pure::metamodel::relation::Relation` | -80 | `values` |
| -37 | `meta::pure::metamodel::relation::RelationType` | -81 | `Pure` |
| -38 | `meta::pure::metamodel::treepath::RootRouteNode` | -82 | `meta::pure::metamodel::multiplicity::OneMany` |
| -39 | `meta::pure::metamodel::extension::Stereotype` | -83 | `meta::pure::metamodel::multiplicity::PureOne` |
| -40 | `meta::pure::metamodel::extension::Tag` | -84 | `meta::pure::metamodel::multiplicity::PureZero` |
| -41 | `meta::pure::metamodel::extension::TaggedValue` | -85 | `meta::pure::metamodel::multiplicity::ZeroMany` |
| -42 | `Root` | -86 | `meta::pure::metamodel::multiplicity::ZeroOne` |
| -43 | `collection` | -87 | `import` |
| -44 | `function` | | |

The ID -1 stands for no string at all. No form holds one, so it never appears in a conforming file.

## 3. Primitives

### 3.1 Narrowest widths

Some integers are written in the narrowest of several widths that holds every value of a group, with a code for the
width. The narrowest width is the smallest whose signed range holds every value: 1 byte for -128 to 127, 2 bytes
for -32,768 to 32,767, 4 bytes for the rest of the `i32` range, and 8 bytes beyond it. So 127 takes 1 byte and 128
takes 2, and -128 takes 1 byte and -129 takes 2.

The codes differ between parts of the format:

| Width | Element version 1 (W1) | String index version 3 (W3) | Metadata version 2 (W2) |
|---|---|---|---|
| 1 byte | 0 | 2 | 0 |
| 2 bytes | 1 | 1 | 1 |
| 4 bytes | 2 | 0 | 2 |
| 8 bytes | 3 | (none) | (none) |

### 3.2 Booleans

A Boolean is a `u8`: 0 for false and 1 for true. Readers reject any other value.

### 3.3 Digit strings

Element version 1 writes some numbers as digit strings: sequences, possibly empty, of the ASCII digits `0` to `9`.
The first byte selects one of four forms. Writers choose the first that applies:

| Digits | First byte | Then |
|---|---|---|
| None | `0x80` | nothing |
| One | `0x40` | the digit's ASCII code (`0x30` to `0x39`) |
| Two or more, all the same | `0xC0 + w` | the number of digits, in the W1 width `w` that is narrowest for it; then the digit's ASCII code |
| Two or more, not all the same | `0x00 + w` | the number of digits `n`, in width `w` as above; then `ceil(n / 2)` bytes: each pair of digits `d1 d2`, in order, as the value `10 * d1 + d2`, and a last unpaired digit `d` as the value `d` |

Readers select the form by the byte's two high bits (`0x80`, `0x40`, `0xC0`, or `0x00`), and read the width from its
two low bits. For example, `1234` is `00 04 0C 22`, `12345` is `00 05 0C 22 05`, and `777` is `C0 03 37`.

No serializable form holds an empty digit string (section 5.1), so `0x80` never begins a digit string in a
conforming file.

## 4. The string index, version 3

A payload with a string index begins with an `i32` string index version, 3, and the table. Every string in the rest
of the payload is written as a string ID (section 4.5) that names a string in the table or a special string.

### 4.1 What the table holds

Each kind of payload lists the strings it writes as string IDs. The table holds each of them that is not a special
string (section 2.4), and, for each string it holds that has a decomposition (section 4.2), every part of the
decomposition that is not a special string, recursively. Each string appears in the table once.

### 4.2 Decomposition

A string `s` of two or more UTF-16 code units has a decomposition if one of the rules below applies to it; the first
that applies gives it. Strings of one code unit, and strings to which no rule applies, are simple.

1. **Source path:** `s` begins with `/`. Its parts are what the `/` characters after the first one separate, from
   position 1: `/a//b` has the parts `a`, the empty string, and `b`. The string is `/` followed by its parts joined
   with `/`.
2. **Import group:** `s` begins with `import`, and has `_` at a position of 6 or more. Its parts are what `_`
   separates, from the start: `import_a_1` has the parts `import`, `a`, and `1`. The string is its parts joined
   with `_`.
3. **Dotted:** `s` contains `.`. Its parts are what `.` separates: `a..b` has the parts `a`, the empty string, and
   `b`. The string is its parts joined with `.`.
4. **Package path:** `s` contains `::`. Its parts are what `::` separates, found from the left, each after the end of
   the previous: `a:::b` has the parts `a` and `:b`. The string is its parts joined with `::`.
5. **Bracketed:** `s` ends with `]` and contains `[`. Let `i` be the position of the last `[`, and `L` that of the
   final `]`. The prefix is `s[0, i)`, and `s` is the first of these that applies:
   - **quoted,** if `s[i + 1]` and `s[L - 1]` are both `'`, and `L - i` is 3 or more, so that they are different
     characters. Its value is `s[i + 2, L - 1)`, and the string is the prefix, `['`, the value, and `']`;
   - **keyed,** if `s[i + 1]` is not `'` but `s[L - 1]` is, and the first `=` after position `i`, at position `e`,
     is followed by `'`, with `L - e` 3 or more. Its key is `s[i + 1, e)` and its value `s[e + 2, L - 1)`, and the
     string is the prefix, `[`, the key, `='`, the value, and `']`;
   - **simple bracketed,** in every other case. Its value is `s[i + 1, L)`, and the string is the prefix, `[`, the
     value, and `]`.

Each part, prefix, key, and value is shorter than the string it comes from.

For example, `meta::pure::functions::string::plus_String_MANY__String_1_` is a package path, `x.properties['name']`
is dotted, with the parts `x` and `properties['name']`, and `properties['name']` is quoted, with the prefix
`properties` and the value `name`.

### 4.3 Order and IDs

The table is in index order (section 2.3), and the string at position `n` has the ID `n`. Special strings have their
fixed negative IDs. Since every part is shorter than its string, a definition refers only to strings earlier in the
table, or to special strings.

### 4.4 Definitions

After the version, the table is an `i32` count of strings, followed by one definition for each, in table order. The
first byte of a definition gives its kind:

| First byte | Kind | Then |
|---|---|---|
| `0x00 + w` | Simple | the number of UTF-8 bytes, in W3 width `w`; then the bytes |
| `0x80 + w` | Source path | the number of parts, in W3 width `w`; then the parts' IDs |
| `0x40 + w` | Package path | the same |
| `0x20 + w` | Dotted | the same |
| `0x60 + w` | Import group | the same |
| `0xC0` | Simple bracketed | the prefix's ID; the value's ID |
| `0xC8` | Quoted | the prefix's ID; the value's ID |
| `0xC4` | Keyed | the prefix's ID; the key's ID; the value's ID |

Each width `w` is the narrowest W3 width for its number (section 3.1). Readers select the kind by the byte's three
high bits (`byte & 0xE0`), the width by its two low bits, and the bracketed form by `byte & 0x0C`.

### 4.5 String IDs

All string IDs in a payload, in the table and after it, have the same width, which the number of strings in the
table, `n`, decides:

| Strings in the table | ID width | ID `x` written as, where `u = x + 87` |
|---|---|---|
| 0 to 168 | 1 byte | the byte `u XOR 0x80` |
| 169 to 65,448 | 2 bytes | the 16 bits `u XOR 0x8000`, big-endian |
| 65,449 to 16,777,128 | 3 bytes | `u` in three bytes, least significant first |
| More | 4 bytes | `x` as an `i32`, with no offset |

The limits are 255, 65,535, and 16,777,215, each less the 87 special strings. For example, with one string in the
table, its ID, 0, is the byte `0xD7`, and the special string `::`, -3, is `0xD4`.

## 5. Serializable forms

A serializable form is what a file holds, apart from how it is encoded. A file's form is what decoding it gives, and
encoding a form gives a file. Two files hold the same content when their forms are equal, whatever element,
metadata, and string index versions wrote them.

**Rules for every form:**

- Every encoding of a form is a function of the form alone, apart from compression: which deflate stream a writer
  produces is not specified yet (section 6.3). For element version 2 and metadata version 3, a file's uncompressed
  counterpart (sections 6.3 and 7.4) is a function of its form.
- A form keeps everything that any encoding keeps. So for conforming files written with the same versions, forms are
  equal exactly when the files are equal, or, for element version 2 and metadata version 3, exactly when their
  uncompressed counterparts are.
- An encoder must reject a form that its version cannot represent. It must never drop or alter part of one.

### 5.1 Elements

An element's form has:

- **a path:** the element's path: the names of its packages below the root package, and its own, joined with `::`;
  the root package's path is its name, `Root`;
- **a reference ID version:** the version of every reference ID in the form;
- **instances:** a list, never empty, of the element itself, first, and the instances that belong to it. An
  instance's position in the list is its internal ID.

Pure also takes `::` as the root package's path, and String values can hold it, as an import stub's `idOrPath` does.
Paths and reference IDs in files never do: they always name the root package `Root`.

Each instance has:

- **a name:** a string, or absent for an anonymous instance;
- **a classifier:** the path of its classifier;
- **source information:** absent, or a source ID and six `i32` values: start line, start column, line, column, end
  line, and end column;
- **a reference ID:** a string, or absent;
- **compile states:** a set of the states `PROCESSED`, `VALIDATED`, and `COMPILE_EVENT_EXTRA_STATE_1` to
  `COMPILE_EVENT_EXTRA_STATE_4`;
- **properties:** a list of properties, each with a name, a source type (the path of the type that declares the
  property), and a list of values.

Each value has one of these kinds:

| Kind | Content |
|---|---|
| Internal reference | an internal ID: the position of an instance in the same form |
| External reference | the reference ID of an instance outside the element |
| Boolean | true or false |
| Byte | an integer from -128 to 127 |
| Date, StrictDate, DateTime | a date |
| LatestDate | nothing |
| Integer | an integer of any size |
| Float, Decimal | a decimal numeral |
| StrictTime | a time |
| String | a string |

- **A date** has a year, from -2,147,483,648 to 2,147,483,647, and then, each only when the one before it is present,
  a month, a day, an hour, a minute, a second, and a subsecond. The month, day, hour, minute, and second are each
  from 0 to 127. The subsecond is a digit string of one or more digits.
- **A time** has an hour and a minute, and then optionally a second, and then optionally a subsecond, with the same
  ranges as in a date.
- **A decimal numeral** has a sign, negative or not, an integer part, and optionally a fraction part. Each part is a
  digit string of one or more digits. Numerals compare as written: `007.50`, `7.50`, and `7.5` are three different
  numerals, and `-0.0` differs from `0.0`.
- **An integer** compares by value. How an encoding writes it depends only on its value.

No form holds a value outside these ranges, so a writer must reject one, as it rejects a string that is not
well-formed (section 2.1).

Forms with different reference ID versions are never equal, since the version is part of the form. They can be
compared only through what their reference IDs name, which is outside this document.

Internal references hold internal IDs from 0 to one less than the number of instances. Back references from other
elements are not part of an element's form: the back reference metadata of the referring elements' modules holds
them (section 5.2).

**What Java's compiler writes,** for information: Float and Decimal numerals are Java's plain string of the value,
so they have no exponent, no leading zeros apart from a single `0` before the point, and no negative zero. A Float
always has a fraction part, which ends in a digit other than `0` unless it is `0`. A Decimal keeps its literal's
scale, the number of digits after the point less the exponent, so `7.50d` stays `7.50`; it has no point when the
scale is 0 or less, as for `1e3d`, which is `1000`, and `1.5e1d`, which is `15`.

### 5.2 Module metadata

Each module has five metadata files, and an element back reference file for each element, in the module or in
another, that the module's elements refer back to. Their forms hold strings, integers, and lists. Every list
described as sorted must be in that order and hold no duplicates; a list of entries with a key holds one entry for
each key.

- **Manifest:** a module name; its dependencies, the names of other modules, sorted in code unit order; and its
  elements, sorted by path in code unit order. Each element has a path, a classifier path, and source information,
  which is required.
- **Source metadata:** a module name; and its sources, sorted by source ID in code unit order. A source ID belongs to
  the module: after an optional leading `/`, it begins with the module's name and a `/`; in the root module, named
  `root`, it is not empty and has no `/` after the optional leading one. Each source has a source ID and a list of
  sections, in the order they appear in the source. Each section has the name of its parser and the paths of its
  elements, in the order they appear in it.
- **External references:** a module name; a reference ID version; and its elements, sorted by path in code unit
  order. Each element has a path and the reference IDs that the element refers to outside itself, sorted in code
  unit order.
- **Element back references:** an element path; a reference ID version; and the element's instances that the
  module's elements refer back to, sorted by reference ID in code unit order. Each instance has a reference ID and
  one or more back references, sorted as below.
- **Function names:** a module name; and its function names, sorted in code unit order. Each function name has the
  paths of the module's functions with that name, sorted in code unit order.
- **Back reference index:** a module name; and the paths of the elements that have back reference files in the
  module, sorted in code unit order.

A back reference is one of these kinds, each with the reference IDs of the instances that refer back:

| Kind | Content |
|---|---|
| Application | a function expression |
| Model element | an element |
| Property from association | a property |
| Qualified property from association | a qualified property |
| Reference usage | an owner, the name of the owner's property, an `i32` offset among its values, and source information, which may be absent |
| Specialization | a generalization |

Back references sort by kind, in the order of the table, then within a kind by their reference ID in code unit
order. Reference usages sort by owner, then property name, in code unit order, then offset, as a number, and then by
source information: absent first, then by source ID in code unit order, and then by its six values in order, as
numbers.

### 5.3 How forms grow

Forms have no version of their own. They leave a process only through encodings that carry versions: file headers,
string index versions, digest prefixes, and, for the text encoding, the revision of this document that the text
follows. When a form must grow, for a new kind of value or a new attribute of instances or metadata, it grows by
additions only:

- each addition has an "absent" state, which every existing file means;
- an encoder whose version cannot represent an addition rejects any form that uses it (section 5);
- the digest encoding gives each addition a new tag, so that no existing digest changes (section 10);
- the text encoding gives each addition a new key, left out when absent, so that no existing text changes
  (section 11).

## 6. Element files

### 6.1 Header

| Field | Type | Value |
|---|---|---|
| Signature | `i64` | `0x014FD4C4B66395B9`, the base-36 number `PureElement` |
| Element version | `i32` | 1 or 2 |
| Reference ID version | `i32` | the form's reference ID version |

The payload follows the header, and the file ends where the payload ends. legend-pure's readers stop at the end of
the payload and do not notice bytes after it, so conformance tests check this rule.

### 6.2 Element version 1

The payload is, in order:

1. a string index (section 4);
2. the path, as a string ID;
3. the source ID of the first instance's source information, as a string ID;
4. a `u8`: the W1 code of the narrowest width for the number 63, the set of every compile state (section 6.4),
   which is 0;
5. an `i32`: the number of instances;
6. each instance, in order.

The strings it writes as string IDs, which the string index holds (section 4.1), are the path, the source ID, and
every instance's name (when present), classifier, reference ID (when present), property names, property source
types, external references' reference IDs, and String values.

**Each instance** is, in order:

| Field | Encoding |
|---|---|
| Name | a Boolean: whether there is a name; if there is, its string ID |
| Classifier | a string ID |
| Source information | the `u8` `0x00` if absent; otherwise the `u8` `0x80 + w`, then the six values in width `w`, the narrowest W1 width for all six |
| Reference ID | the `u8` `0x00` if absent; otherwise the `u8` `0x80`, then a string ID |
| Compile states | the set's bits (section 6.4), in the width that the payload's fourth field names |
| Properties | an `i32` count; then each property's name, as a string ID, its source type, as a string ID, an `i32` count of values, and each value |

Readers decide whether source information and a reference ID are present by the byte's high bit (`0x80`).

**Each value** begins with a `u8` whose three high bits (`byte & 0xE0`) give its kind:

| Kind | First byte | Then |
|---|---|---|
| Internal reference | `0x00` | the internal ID, in the narrowest W1 width for the number of instances |
| External reference | `0x80` | the reference ID's string ID |
| Boolean | `0x40 + b`, with `b` 1 for true and 0 for false | nothing |
| Byte | `0x20` | the byte, as an `i8` |
| Date | `0xC0 + p` | the date (below) |
| StrictDate | `0xC8 + p` | the date |
| DateTime | `0xD0 + p` | the date |
| LatestDate | `0xD8` | nothing |
| Integer, from -2^63 to 2^63 - 1 | `0xA0 + w` | the value, in the narrowest W1 width `w` for it |
| Integer, otherwise | `0xA8 + n` | the digits of its absolute value, without leading zeros, as a digit string (section 3.3) |
| Float | `0xB0 + d + n` | the integer part, as a digit string; then, if there is a fraction part, it as a digit string |
| Decimal | `0xB8 + d + n` | the same |
| StrictTime | `0x60 + p` | the time (below) |
| String | `0xE0` | the string's string ID |

Here `n` is 1 for a negative number and 0 otherwise, and `d` is 2 when there is a fraction part and 0 otherwise.
Readers select the kind of date or number by `byte & 0x18`.

The width of internal IDs depends on the number of instances, not on the largest ID: in an element of 128 instances,
every internal ID takes 2 bytes, although the largest, 127, fits in 1.

**Dates.** The code `p` gives the last part present:

| Last part present | `p` |
|---|---|
| Year | 0 |
| Month | 1 |
| Day | 2 |
| Hour | 4 |
| Minute | 3 |
| Second | 6 |
| Subsecond | 5 |

The date is the year as an `i32`; then each of the month, day, hour, minute, and second that is present, as an `i8`;
and then the subsecond, if present, as a digit string. A time is written the same way, from its hour, with no year,
month, or day: its `p` is 3, 6, or 5.

**What it cannot represent.** An encoder of element version 1 must reject a form:

- whose first instance has no source information;
- in which an instance's source information has a source ID other than the first instance's: the payload holds one
  source ID, and readers give it to every instance with source information.

### 6.3 Element version 2

The payload is element version 1's payload for the same form, compressed as one raw deflate stream (RFC 1951),
without a zlib or gzip wrapper. The stream must end exactly at the end of the file, as section 6.1 requires of every
payload.

The **uncompressed counterpart** of an element version 2 file is the file that element version 1 writes for the same
form: the same header with the version 1, followed by the inflated payload.

Which deflate stream a writer produces is not specified yet: legend-pure's Java implementation uses the JDK's zlib
at level 7, whose output depends on the zlib build. A later revision will specify it.

### 6.4 Compile states

Element version 1 writes the set of compile states as an integer whose bits are these:

| State | Bit |
|---|---|
| `PROCESSED` | 1 |
| `VALIDATED` | 2 |
| `COMPILE_EVENT_EXTRA_STATE_1` | 4 |
| `COMPILE_EVENT_EXTRA_STATE_2` | 8 |
| `COMPILE_EVENT_EXTRA_STATE_3` | 16 |
| `COMPILE_EVENT_EXTRA_STATE_4` | 32 |

Every other bit is 0.

## 7. Module metadata files

### 7.1 Header

| Field | Type | Value |
|---|---|---|
| Signature | `i64` | the base-36 number of the file kind's name (below) |
| Metadata version | `i32` | 1, 2, or 3 |

| File kind | Name | Signature |
|---|---|---|
| Manifest | `PureManifest` | `0x2F39EC3629C726AD` |
| Source metadata | `PureSource` | `0x000954221B6C8DEE` |
| External references | `PureExtRefs` | `0x014FD4C4E32742D8` |
| Element back references | `PureBackRefs` | `0x2F39EB6D4ED125D8` |
| Back reference index | `PureBRIndex` | `0x014FD4C3472D71A9` |
| Function names | `PureFuncName` | `0x2F39EBC0940C7D86` |

The payload follows the header, and the file ends where the payload ends, as for element files (section 6.1).

### 7.2 Metadata version 2

Every payload begins with a string index (section 4), and writes its strings as string IDs. The strings it writes,
which the index holds, are every string in its form.

**Source information** in metadata is the source ID, as a string ID; a `u8`, the narrowest W2 width code `w` for all
six values; and the six values, in width `w`.

| File kind | Payload after the string index |
|---|---|
| Manifest | the module name; an `i32` count of dependencies; an `i32` count of elements; each dependency; each element's path, classifier path, and source information |
| Source metadata | the module name; an `i32` count of sources; for each source, its source ID, an `i32` count of sections, and for each section its parser name, an `i32` count of elements, and each element path |
| External references | the module name; the reference ID version, as an `i32`; an `i32` count of elements; for each element, its path, an `i32` count of reference IDs, and each reference ID |
| Element back references | the element path; the reference ID version, as an `i32`; an `i32` count of instances; for each instance, its reference ID, an `i32` count of back references, and each back reference (below) |
| Function names | the module name; an `i32` count of function names; for each, the function name, an `i32` count of functions, and each function path |
| Back reference index | the module name; an `i32` count of element paths; each element path |

Every string in this table is written as a string ID.

**Back references** each begin with a `u8` whose three high bits (`byte & 0xE0`) give their kind:

| Kind | First byte | Then |
|---|---|---|
| Application | `0x00` | the function expression |
| Model element | `0x80` | the element |
| Property from association | `0x40` | the property |
| Qualified property from association | `0x20` | the qualified property |
| Reference usage | `0xC0 + s + w` | the owner; the property name; the offset, in the narrowest W2 width `w` for it; and then, if `s` is 8, the source information |
| Specialization | `0xA0` | the generalization |

Here `s` is 8 when the reference usage has source information and 0 when it does not. Readers read `s` as
`byte & 0x08`, and `w` as `byte & 0x03`.

### 7.3 Metadata version 1

Version 1 is version 2 without dependencies: its manifest payload is the module name, an `i32` count of elements,
and each element, and its string index does not hold dependencies. An encoder of version 1 must reject a manifest
form with dependencies. The other five kinds of file are the same as in version 2.

### 7.4 Metadata version 3

The payload is version 2's payload for the same form, compressed as in element version 2 (section 6.3): one raw
deflate stream that ends exactly at the end of the file. The uncompressed counterpart of a version 3 file is the file
that version 2 writes for the same form.

## 8. File layout, path provider version 1

### 8.1 Where files go

Paths below separate directories with `/`. In a directory, the platform's separator takes its place; in an archive,
`/` remains. `<module>` is the module's name, as a file name (section 8.2).

| File | Path |
|---|---|
| Element file | the element path's names, as file names, separated by `/`, with `.pelt` after the last; `Root.pelt` for the root package |
| Manifest | `org/finos/legend/pure/module/<module>.pmf` |
| Source metadata | `org/finos/legend/pure/module/<module>.psr` |
| External references | `org/finos/legend/pure/module/<module>.pxr` |
| Back reference index | `org/finos/legend/pure/module/<module>.pbr` |
| Function names | `org/finos/legend/pure/module/<module>.pfn` |
| Element back references | `org/finos/legend/pure/module/<module>/`, then the element path's names, as file names, separated by `/`, with `.pbr` after the last; `Root.pbr` for the root package |

So the element `meta::pure::metamodel::type::Class` is in `meta/pure/metamodel/type/Class.pelt`, and the platform
module's manifest is `org/finos/legend/pure/module/platform.pmf`.

In each path, the last name becomes a file name with the extension after it (section 8.2). Since `.pbr` is one
character shorter than `.pelt`, a long last name can be cut and hashed differently in an element file and in the
element's back reference files (section 13).

### 8.2 File names

Each name in an element path, and each module name, becomes a file name. They must be ASCII: a writer must reject
any other, as an error when the module is built. (legend-pure's M3 grammar allows only the ASCII letters, digits,
`_`, and `$` in element names, and module names are `root` or match `[a-z]+(_[a-z0-9]+)*`.) Since names are ASCII,
their lengths below count characters, which are also bytes.

A name `n` that becomes a file name with the extension `e`, which is empty for directories, gives:

- `n` followed by `e`, if together they have at most 126 characters;
- otherwise, the first `113 - |e|` characters of `n`, then a 13-character hash of the rest of `n`, then `e`. The
  result has exactly 126 characters.

**The hash** of a string is a 64-bit unsigned value `h`, which starts at 0. For each character `c` of the string,
in order, `h` becomes `spread(h) + c`, modulo 2^64, where `c` is the character's code. Then `h` is written in base 32,
with the digits `0` to `9` and `a` to `v`, with no leading zeros, and padded on the left with `0` to 13 characters.

`spread(x)`, with every operation on unsigned 64-bit values and modulo 2^64, is:

1. `x = x XOR (x >> 28)`;
2. `x = x * 0xC4F419D59D1885A9`;
3. `x = x XOR (x >> 43)`;
4. `x = x * 0xF3649B01E44CF6C3`;
5. `x = x XOR (x >> 23)`.

This is `SpreadFunctions.longSpreadOne` from Eclipse Collections (EPL-1.0 and EDL-1.0).

Since a module's names are case-sensitive and some file systems are not, element paths that differ only in letter
case name files that collide on Windows and macOS. Windows also reserves device names, compared without regard to
case. In a path with a directory, Windows 11 build 26300 reserves only `NUL`, and only without an extension, so
there only a directory named `nul` collides: a package's, or a module's directory of element back reference files.
Earlier versions reserve `CON`, `PRN`, `AUX`, `NUL`, `COM1` to `COM9`, and `LPT1` to `LPT9`, each with or without
an extension. Whether writers must reject either kind of name is not yet decided.

## 9. Reference IDs, version 1

### 9.1 Syntax

A reference ID names an instance by a path through the graph from a packageable element:

```
referenceId = elementPath edge*
elementPath = name ("::" name)*
edge        = "." name selector?
selector    = "[" index "]" | "['" key "']" | "[" name "='" key "']"
```

- **Names,** of elements, properties, and key properties, are one or more ASCII letters, ASCII digits, `_`, or
  `$`.
- **The element path** is the path of a packageable element (section 5.1), `Root` for the root package.
- **Each edge** follows one property: `.p` its only value, `.p[3]` the value at index 3, from 0, `.p['k']` the value
  whose `name` is `k`, and `.p[q='k']` the value whose property `q` is `k`.
- **An index** is written in decimal, with no sign and no leading zeros.
- **A key** is a string, escaped as in section 9.2.
- **Writers** write `.p['k']` for the value whose `name` is `k`, and never `.p[name='k']`.

Since names are ASCII and keys are escaped, reference IDs of version 1 are ASCII.

### 9.2 Escapes in keys

A writer writes each UTF-16 code unit of a key as follows:

| Code unit | Written as |
|---|---|
| `'` | `\'` |
| `\` | `\\` |
| U+0008, U+0009, U+000A, U+000C, U+000D | `\b`, `\t`, `\n`, `\f`, `\r` |
| Any other below U+0020, or above U+007F | `\u` and four uppercase hexadecimal digits |
| Any other | itself |

So a supplementary character is written as the escapes of its two surrogates: U+10B00 is `\uD802\uDF00`. U+007F
is written as itself.

### 9.3 Reading

Readers accept at least what writers write. legend-pure's parser accepts more, for example:

- `::` as the root package's path, which Pure also accepts in source code;
- whitespace around names and indexes;
- names with letters and digits outside ASCII, and property names in quotes;
- `.p[name='k']`;
- indexes with a sign, with leading zeros, or with digits outside ASCII, such as `.p[+3]`, `.p[03]`, and `.p[-0]`;
- text inside the brackets before or after a quoted key, which it ignores, as in `.p[x'k']` and `.p['k'x]`;
- escapes in keys that writers never write: octal escapes; `\u` escapes with lowercase hexadecimal digits, with more
  than one `u`, with a `+` or `-` sign, or with digits outside ASCII; and a backslash before any other character,
  which it drops.

Such IDs do not conform.

## 10. Digests

### 10.1 The digest encoding

The digest encoding writes a serializable form as bytes, for digests only: it is never written to a file. It uses:

- `u8`, `u32`, and `i32` values, big-endian;
- **strings:** a `u32` count of UTF-8 bytes, then the bytes;
- **lists:** a `u32` count of items, then the items;
- **records:** a sequence of fields, each a `u8` tag, which is never 0, followed by the field's content, in
  ascending order of tag, and then the byte `0x00`. A field that is absent is left out, and so a field added to a
  record later is left out of every form that does not use it, which keeps every existing digest unchanged.

**An element** is a record:

| Tag | Field | Content |
|---|---|---|
| 1 | Path | a string |
| 2 | Reference ID version | an `i32` |
| 3 | Instances | a list of instances |

**An instance** is a record:

| Tag | Field | Content |
|---|---|---|
| 1 | Name | a string; left out when absent |
| 2 | Classifier | a string |
| 3 | Source information | a string, the source ID; then six `i32` values; left out when absent |
| 4 | Reference ID | a string; left out when absent |
| 5 | Compile states | a `u32`: the set's bits (section 6.4) |
| 6 | Properties | a list of properties |

**A property** is a record:

| Tag | Field | Content |
|---|---|---|
| 1 | Name | a string |
| 2 | Source type | a string |
| 3 | Values | a list of values |

**A value** is a `u8` tag for its kind, followed by its content:

| Tag | Kind | Content |
|---|---|---|
| 1 | Internal reference | a `u32`, the internal ID |
| 2 | External reference | a string, the reference ID |
| 3 | Boolean | a `u8`: 0 or 1 |
| 4 | Byte | a `u8`: the byte's two's complement bits |
| 5 | Date | a date |
| 6 | StrictDate | a date |
| 7 | DateTime | a date |
| 8 | LatestDate | nothing |
| 9 | Integer | a string: the value in decimal, with a leading `-` if negative, and no leading zeros (`0` for zero) |
| 10 | Float | a string: the numeral (below) |
| 11 | Decimal | a string: the numeral |
| 12 | StrictTime | a time |
| 13 | String | a string |

- **A date** is an `i32` year; a `u8` for its last part present: 0 year, 1 month, 2 day, 3 hour, 4 minute, 5 second,
  6 subsecond; a `u8` for each of the month, day, hour, minute, and second that is present; and the subsecond, if
  present, as a string of its digits. These codes follow the order of the parts, unlike element version 1's codes
  `p` (section 6.2).
- **A time** is a `u8` for its last part present: 4 minute, 5 second, 6 subsecond; a `u8` each for the hour, the
  minute, and the second if present; and the subsecond, if present, as a string of its digits.
- **A numeral** is written as text: `-` if negative, the integer part's digits, and, if there is a fraction part, `.`
  and its digits.

A new attribute gets a new field tag, and a new kind of value a new value tag, so that neither changes an existing
digest. Any other change to the encoding takes a new digest prefix (section 10.2).

### 10.2 Content digests

The content digest of an element is the SHA-256 hash of the ASCII bytes `pelt-content-1`, a zero byte, and the
digest encoding of the element's form. It is the same whatever element and string index versions wrote the element's
file. The reference ID version is part of the form (section 5.1), so it is part of the digest.

A digest of a projection of a form, such as one that leaves out source information, or of a form together with
other data, uses the same encoding under a prefix of its own. So would a digest of module metadata. Whatever
stores a digest records which definition produced it, since the prefix is hashed, not stored.

## 11. The text encoding

The text encoding writes a serializable form as JSON (RFC 8259), so that test corpora can hold forms that people can
read and that any language can parse. Forms compare by their JSON values, not by their text.

**An element** is an object:

| Key | Value |
|---|---|
| `kind` | `"element"` |
| `path` | a string |
| `referenceIdVersion` | a number |
| `instances` | an array of instances |

**An instance** is an object with the keys `name` (a string; left out when absent), `classifier` (a string),
`sourceInformation` (an array of the source ID and the six numbers; left out when absent), `referenceId` (a string;
left out when absent), `compileStates` (an array of state names, in the order of section 6.4), and `properties` (an
array). **A property** is an object with the keys `name`, `sourceType`, and `values` (an array).

**A value** is an object with one key, which names its kind:

| Key | Value |
|---|---|
| `internal` | a number, the internal ID |
| `external` | a string, the reference ID |
| `Boolean` | `true` or `false` |
| `Byte` | a number |
| `Date`, `StrictDate`, `DateTime` | a string: the year in decimal, with `-` if negative, and no leading zeros; then, as present, `-` and the month, `-` and the day, `T` and the hour, `:` and the minute, and `:` and the second, each in decimal with at least two digits, padded with `0`; and `.` and the subsecond's digits |
| `LatestDate` | `null` |
| `Integer` | a string: the value in decimal, as in section 10.1 |
| `Float`, `Decimal` | a string: the numeral, as in section 10.1 |
| `StrictTime` | a string: the hour, `:` and the minute, and then, as present, `:` and the second and `.` and the subsecond's digits, written as in a date |
| `String` | a string |

Numbers that can exceed 2^53 are written as strings, since many JSON parsers read numbers as doubles.

**Module metadata** forms are objects with `kind` and the fields of section 5.2, in its order, with the same
conventions:

| `kind` | Other keys |
|---|---|
| `"manifest"` | `module`, `dependencies` (strings), `elements` (objects with `path`, `classifier`, and `sourceInformation`) |
| `"sourceMetadata"` | `module`, `sources` (objects with `sourceId` and `sections`, which are objects with `parser` and `elements`) |
| `"externalReferences"` | `module`, `referenceIdVersion`, `elements` (objects with `path` and `references`) |
| `"elementBackReferences"` | `path`, `referenceIdVersion`, `instances` (objects with `referenceId` and `backReferences`) |
| `"functionNames"` | `module`, `functionNames` (objects with `name` and `functions`) |
| `"backReferenceIndex"` | `module`, `elements` (strings) |

A back reference is an object with one key, its kind: `application`, `modelElement`, `propertyFromAssociation`,
`qualifiedPropertyFromAssociation`, and `specialization` have a string; `referenceUsage` has an object with `owner`,
`property`, `offset`, and `sourceInformation`, which is left out when absent.

**Layout.** Corpora publish dumps in one layout, so that equal forms have equal text. Section 13 has two examples.

- Keys are in the orders above.
- `sourceInformation`, `compileStates`, each value object, and every empty array are on one line. An empty array is
  `[]`. In the others, each `,` is followed by one space, and no space follows the opening bracket or precedes the
  closing one.
- Every other object or array is on several lines. Its opening bracket ends a line. Each member or item follows on a
  line of its own, indented by two spaces more than the line with the opening bracket, and followed by `,` unless it
  is the last. The closing bracket is on a line of its own, indented as the line with the opening bracket. The
  outermost object begins the first line, with no indentation. So a back reference object is on several lines, and
  so is a reference usage's object.
- Each `:` after a key is followed by one space. Apart from indentation and the spaces that these rules name, no
  space appears outside strings.
- Numbers are in decimal, with a leading `-` if negative, no leading zeros (`0` for zero), and no fraction or
  exponent.
- Strings use the escapes `\"`, `\\`, `\b`, `\f`, `\n`, `\r`, and `\t`, and `\u` with four lowercase hexadecimal
  digits for every other character below U+0020 or above U+007F, with a surrogate pair for a supplementary
  character. Every other character is written as itself.
- Every line, the last included, ends with a line feed (U+000A), with no space or carriage return before it.

## 12. Departures of the Java implementation

Each of these is a bug in legend-pure at `a67907c83`. A fix that changes no file can be made at once. A fix that
changes files written today comes after a scan of existing models shows which it affects.

1. **Unpaired surrogates** are written as `?` (`0x3F`), without an error, since `String.getBytes` replaces them
   (`StringWriterV3.java:134`). Section 2.1 requires an error.
2. **Bracketed strings whose two quotes would be one character.** For a string such as `x[']` or `a[k=']`, where
   the quote after `[` or `=` is also the quote before `]`, section 4.2 makes the string simple bracketed, but the
   string index writer throws `StringIndexOutOfBoundsException` (`StringWriterV3.java:415` and `:425`). So no
   element or metadata can be serialized that holds such a string, or a string that decomposes into one, such as
   `a.x[']`. Fixing it changes no file, since no file can hold one.
3. **Integers with leading zeros.** An Integer literal of more than 20 characters becomes a `BigInteger`, even when
   its value fits in an `i64`, as `000000000000000000001` does (`ModelRepository.java:602`). Element version 1 then
   writes it in the big integer form, which section 6.2 reserves for values outside the `i64` range.
4. **Metadata version 1 drops dependencies.** It writes a manifest that has dependencies without them, rather than
   rejecting it (section 7.3).
5. **Source IDs of instances.** Element version 1 writes one source ID for the element, and does not check that each
   instance's source information has the same one (section 6.2). No file is known to be affected, although files
   cannot show it, since each holds one source ID. An instance with source information belongs to the element only
   when the element's source information contains it, which requires the same source ID (`SerializerV1.java:573`),
   with one exception: a reference usage belongs to the element when its owner does (`SerializerV1.java:177`).
   legend-pure gives a reference usage no source information, or that of the place where the reference is written,
   in its owner's source.
6. **Reference usages that tie** on owner, property name, and offset keep the order in which they were added, since
   the comparison ignores their source information (`BackReference.java:503`). Section 5.2 orders them by source
   information. And since duplicates are removed only when they are next to each other (`ListHelper.java:116`,
   called from `ElementBackReferenceMetadata.java:237`), two equal reference usages with a tie between them both
   remain, although section 5.2 allows no duplicates.
7. **Names outside ASCII** in element paths and module names are written, not rejected (section 8.2). legend-pure's
   M3 grammar and its check on module names do not let them arise.
8. **The Java form classes.** `Value` holds Float and Decimal values as `BigDecimal`, which loses a negative zero
   and leading zeros, and tells apart values that the form writes as one numeral: a Decimal `1e3d` built from a
   compiled graph (`1E+3`) differs from the same value read back from its file (`1000`). It also compares integers by
   their Java type, so an `Integer` 5 differs from a `Long` 5 (`Value.java:318`, `:338`, and `:358`). Section 5.1
   compares numerals as written and integers by value. The bytes are not affected: the encoder takes numerals from
   instance names.
9. **Property names in reference IDs** are not checked against section 9.1 when IDs are generated
   (`ReferenceIdGenerator.java:230`, `:253`, and `:268`). Pure lets a property be declared with a quoted name, such
   as `'my prop'`, which would give any instance reached through that property an ID that is outside section 9.1's
   syntax and that `GraphPath.parse` rejects.
10. **Source types of back reference properties.** Element version 1 writes the source type of each back reference
    property it writes, but does not add it to the strings for the string index (`SerializerV1.java:195`), so it
    would throw if the string were not there for another reason. In the files checked, it always was. No file is
    affected.

## 13. Test vectors

**Orders (section 2.3).** In index order: `a`, `b`, U+00E9, U+E000, U+FFFD, `ab`, `zz`, U+10B00, and U+E000
followed by `a`. The last two show the exception in section 2.3: both have two code units, so they sort in code unit
order, which puts U+10B00 first; comparing their code points instead would put U+E000 followed by `a` first. In code
unit order, U+10000 sorts before U+E000.

**String indexes (section 4).** Each is a payload with the given strings written, in turn, after the index:

| Strings | Bytes |
|---|---|
| `::` | `00 00 00 03 00 00 00 00 D4` |
| `a::` | `00 00 00 03 00 00 00 02 02 01 61 42 02 D7 D5 D8` |
| `x.y` | `00 00 00 03 00 00 00 03 02 01 78 02 01 79 22 02 D7 D8 D9` |
| `/a//b` | `00 00 00 03 00 00 00 03 02 01 61 02 01 62 82 03 D7 D5 D8 D9` |
| `import_` | `00 00 00 03 00 00 00 01 62 02 80 D5 D7` |
| `a[3]` | `00 00 00 03 00 00 00 03 02 01 33 02 01 61 C0 D8 D7 D9` |
| `a['v']` | `00 00 00 03 00 00 00 03 02 01 61 02 01 76 C8 D7 D8 D9` |
| `a[k='v']` | `00 00 00 03 00 00 00 04 02 01 61 02 01 6B 02 01 76 C4 D7 D8 D9 DA` |
| `a['']` | `00 00 00 03 00 00 00 02 02 01 61 C8 D7 D5 D8` |
| `a[k='']` | `00 00 00 03 00 00 00 03 02 01 61 02 01 6B C4 D7 D8 D5 D9` |
| U+E000 followed by `a`, then U+10B00 | `00 00 00 03 00 00 00 02 02 04 F0 90 AC 80 02 04 EE 80 80 61 D8 D7` |

In the last row, U+10B00 is first in the table, with the ID 0, since its first code unit, U+D802, is less than
U+E000.

The next three are simple bracketed (section 4.2), although legend-pure's writer fails on them today (section
12, item 2):

| Strings | Bytes |
|---|---|
| `x[']` | `00 00 00 03 00 00 00 03 02 01 27 02 01 78 C0 D8 D7 D9` |
| `[']` | `00 00 00 03 00 00 00 02 02 01 27 C0 D5 D7 D8` |
| `a[k=']` | `00 00 00 03 00 00 00 03 02 01 61 02 03 6B 3D 27 C0 D7 D8 D9` |

**String ID widths (section 4.5).** Each table holds the decimal numerals `0` to `n - 1`, which are simple, so the
numeral `k` has the ID `k`. After the index, each payload writes `0`, the numeral `n - 1`, and `::`:

| Strings in the table, `n` | Payload bytes | Payload SHA-256 | `0` | `n - 1` | `::` |
|---|---|---|---|---|---|
| 168 | 741 | `11b99d733bdc11a8f0038df569cbfacf4ac912cc6f09b567133f5a2c5ba8e898` | `D7` | `7E` | `D4` |
| 169 | 749 | `dba6cc407a42dc42990f403dca9b5184f3f5c9b504c2813e626e08f5a01002b5` | `80 57` | `80 FF` | `80 54` |
| 65,448 | 447,040 | `0069d63112521ceb83b8bccddb1462381aa01d66cb2c4eceb254b605d9e0f5e4` | `80 57` | `7F FE` | `80 54` |
| 65,449 | 447,050 | `22247723c586b96fb53966f641484b94c3de9467d27bc46d6e5f8b7f39efe8a2` | `57 00 00` | `FF FF 00` | `54 00 00` |
| 16,777,128 | 156,660,187 | `817dc9e04c5cfcea82a037619fec65fbb329572f912f6135f753e6af286e2b27` | `57 00 00` | `FE FF FF` | `54 00 00` |
| 16,777,129 | 156,660,200 | `0a1953a943cec1ede06b5f13cc29989b939ebc3ea77014dea6cf9989383e59f6` | `00 00 00 00` | `00 FF FF A8` | `FF FF FF FD` |

In the rows for 169, 65,449, and 16,777,129 strings, the last ID would fit in the next narrower width: the limits
leave each width's top value unused.

**Wider lengths and counts (section 4.4).** With `a` repeated 200 times, `a` repeated 40,000 times, and
`a::a::...::a`, whose parts are 130 strings `a`, written in that order after the index, the payload has 40,355 bytes,
with the SHA-256 `75fc191472b14d42431bc30f75fc1f843883c17318155801aabb365d4a37127b`. It is
`00 00 00 03 00 00 00 04`; then `02 01 61`, for `a`; `01 00 C8` and 200 bytes `61`; `41 00 82` and 130 bytes `D7`,
for the package path; `00 00 00 9C 40` and 40,000 bytes `61`; and then `D8 DA D9`.

**Values (section 6.2).** Values without strings or references encode the same way in every payload. Written as in
the text encoding (section 11), and read back by legend-pure as the same values:

| Value | Bytes |
|---|---|
| Date `2026` | `C0 00 00 07 EA` |
| DateTime `2026-11-20T13` | `D4 00 00 07 EA 0B 14 0D` |
| DateTime `2026-11-20T13:05:09` | `D6 00 00 07 EA 0B 14 0D 05 09` |
| StrictTime `08:30:15` | `66 08 1E 0F` |
| Decimal `0.777` | `BA 40 30 C0 03 37` |
| Integer `128` | `A1 00 80` |
| Integer `2147483648` | `A3 00 00 00 00 80 00 00 00` |
| Integer `11111111111111111111111` | `A8 C0 17 31` |

**File-name hashes (section 8.2).**

| String | Hash, as a signed 64-bit value | In base 32, padded to 13 characters |
|---|---|---|
| (empty) | 0 | `0000000000000` |
| `a` | 97 | `0000000000031` |
| `ab` | -2,348,236,215,798,108,722 | `duqb3j9maaree` |
| `abc` | 6,950,668,482,129,537,676 | `60tds522dbakc` |
| `Avestan` | -895,522,193,269,313,661 | `f74jn9tar8bs3` |
| `longSpreadOne` | -5,733,343,490,630,880,054 | `b0roif34qbt6a` |

**File names (sections 8.1 and 8.2).** For the element `pkg::<name>`, where `<name>` repeats
`abcdefghijklmnopqrstuvwxyz` up to the given length, the element file and the element back reference file under
`org/finos/legend/pure/module/m/` are:

| Length of `<name>` | Element file | Element back reference file |
|---|---|---|
| 121 | `pkg/` + the name + `.pelt`, a file name of 126 characters | `pkg/` + the name + `.pbr`, a file name of 125 characters |
| 122 | `pkg/` + the first 108 characters + `52tisufrbfm28.pelt` | `pkg/` + the name + `.pbr`, a file name of 126 characters |
| 123 | `pkg/` + the first 108 characters + `bnno9dd9bvkme.pelt` | `pkg/` + the first 109 characters + `c323rv9kr7tfk.pbr` |
| 128 | `pkg/` + the first 108 characters + `0rq0a52sf28du.pelt` | `pkg/` + the first 109 characters + `019utp3t3pc72.pbr` |
| 130 | `pkg/` + the first 108 characters + `74d13ahrei85t.pelt` | `pkg/` + the first 109 characters + `4hr3v6q47lgr1.pbr` |

In the row for 128, both hashes have only 12 digits in base 32, so each begins with a `0` of padding.

**Headers (sections 6.1 and 7.1).** An element version 2 file with reference ID version 1 begins
`01 4F D4 C4 B6 63 95 B9 00 00 00 02 00 00 00 01`, and a version 3 manifest begins
`2F 39 EC 36 29 C7 26 AD 00 00 00 03`.

**A complete element (sections 5, 6, 10, and 11).** This made-up element exercises every kind of value. Its form, in
the text encoding, is:

```json
{
  "kind": "element",
  "path": "test::Example",
  "referenceIdVersion": 1,
  "instances": [
    {
      "name": "Example",
      "classifier": "test::Thing",
      "sourceInformation": ["/test/example.pure", 1, 1, 1, 7, 4, 1],
      "compileStates": ["PROCESSED", "VALIDATED"],
      "properties": [
        {
          "name": "children",
          "sourceType": "test::Thing",
          "values": [
            {"internal": 1}
          ]
        },
        {
          "name": "label",
          "sourceType": "test::Thing",
          "values": [
            {"String": "caf\u00e9"}
          ]
        }
      ]
    },
    {
      "classifier": "test::Part",
      "sourceInformation": ["/test/example.pure", 2, 3, 2, 3, 3, 40],
      "referenceId": "test::Example.children[0]",
      "compileStates": ["PROCESSED"],
      "properties": [
        {
          "name": "owner",
          "sourceType": "test::Part",
          "values": [
            {"internal": 0}
          ]
        },
        {
          "name": "type",
          "sourceType": "test::Part",
          "values": [
            {"external": "meta::pure::metamodel::type::Any"}
          ]
        },
        {
          "name": "values",
          "sourceType": "test::Part",
          "values": [
            {"Boolean": true},
            {"Byte": -1},
            {"Integer": "300"},
            {"Integer": "-1180591620717411303424"},
            {"Float": "-0.25"},
            {"Decimal": "1000"},
            {"Date": "2026-11"},
            {"StrictDate": "2026-11-20"},
            {"DateTime": "2026-11-20T13:05:09.120"},
            {"LatestDate": null},
            {"StrictTime": "08:30"}
          ]
        }
      ]
    }
  ]
}
```

Its file with element version 1 and reference ID version 1, which legend-pure reads back as this form, is these 244
bytes:

```
01 4F D4 C4 B6 63 95 B9 00 00 00 01 00 00 00 01
00 00 00 03 00 00 00 0F 02 01 30 02 04 50 61 72
74 02 05 63 61 66 C3 A9 02 04 74 65 73 74 02 05
54 68 69 6E 67 02 05 6C 61 62 65 6C 02 07 45 78
61 6D 70 6C 65 02 07 65 78 61 6D 70 6C 65 42 02
DA D8 C0 A2 D7 42 02 DA DB 22 02 DE A6 42 02 DA
DD 82 02 DA E2 22 02 E3 E0 E3 E4 00 00 00 00 02
01 DD E1 80 01 01 01 07 04 01 00 03 00 00 00 02
A2 E1 00 00 00 01 00 01 DC E1 00 00 00 01 E0 D9
00 DF 80 02 03 02 03 03 28 80 E5 01 00 00 00 03
99 DF 00 00 00 01 00 00 A3 DF 00 00 00 01 80 C5
87 DF 00 00 00 0B 41 20 FF A1 01 2C A9 00 16 0B
50 3B 10 14 47 4A 0B 1E 22 18 B3 40 30 00 02 19
B8 00 04 0A 00 C1 00 00 07 EA 0B CA 00 00 07 EA
0B 14 D5 00 00 07 EA 0B 14 0D 05 09 00 03 0C 00
D8 63 08 1E
```

Its digest encoding is these 532 bytes:

```
01 00 00 00 0D 74 65 73 74 3A 3A 45 78 61 6D 70
6C 65 02 00 00 00 01 03 00 00 00 02 01 00 00 00
07 45 78 61 6D 70 6C 65 02 00 00 00 0B 74 65 73
74 3A 3A 54 68 69 6E 67 03 00 00 00 12 2F 74 65
73 74 2F 65 78 61 6D 70 6C 65 2E 70 75 72 65 00
00 00 01 00 00 00 01 00 00 00 01 00 00 00 07 00
00 00 04 00 00 00 01 05 00 00 00 03 06 00 00 00
02 01 00 00 00 08 63 68 69 6C 64 72 65 6E 02 00
00 00 0B 74 65 73 74 3A 3A 54 68 69 6E 67 03 00
00 00 01 01 00 00 00 01 00 01 00 00 00 05 6C 61
62 65 6C 02 00 00 00 0B 74 65 73 74 3A 3A 54 68
69 6E 67 03 00 00 00 01 0D 00 00 00 05 63 61 66
C3 A9 00 00 02 00 00 00 0A 74 65 73 74 3A 3A 50
61 72 74 03 00 00 00 12 2F 74 65 73 74 2F 65 78
61 6D 70 6C 65 2E 70 75 72 65 00 00 00 02 00 00
00 03 00 00 00 02 00 00 00 03 00 00 00 03 00 00
00 28 04 00 00 00 19 74 65 73 74 3A 3A 45 78 61
6D 70 6C 65 2E 63 68 69 6C 64 72 65 6E 5B 30 5D
05 00 00 00 01 06 00 00 00 03 01 00 00 00 05 6F
77 6E 65 72 02 00 00 00 0A 74 65 73 74 3A 3A 50
61 72 74 03 00 00 00 01 01 00 00 00 00 00 01 00
00 00 04 74 79 70 65 02 00 00 00 0A 74 65 73 74
3A 3A 50 61 72 74 03 00 00 00 01 02 00 00 00 20
6D 65 74 61 3A 3A 70 75 72 65 3A 3A 6D 65 74 61
6D 6F 64 65 6C 3A 3A 74 79 70 65 3A 3A 41 6E 79
00 01 00 00 00 06 76 61 6C 75 65 73 02 00 00 00
0A 74 65 73 74 3A 3A 50 61 72 74 03 00 00 00 0B
03 01 04 FF 09 00 00 00 03 33 30 30 09 00 00 00
17 2D 31 31 38 30 35 39 31 36 32 30 37 31 37 34
31 31 33 30 33 34 32 34 0A 00 00 00 05 2D 30 2E
32 35 0B 00 00 00 04 31 30 30 30 05 00 00 07 EA
01 0B 06 00 00 07 EA 02 0B 14 07 00 00 07 EA 06
0B 14 0D 05 09 00 00 00 03 31 32 30 08 0C 04 08
1E 00 00 00
```

So its content digest is `bf044c1887dbf1c6315eef971ec0549c8239be5cf368e4b0b93536c01063640b`.

**A module manifest (sections 5.2, 7, and 11).** This made-up manifest has one dependency and one element. Its form,
in the text encoding, is:

```json
{
  "kind": "manifest",
  "module": "m",
  "dependencies": [
    "platform"
  ],
  "elements": [
    {
      "path": "m::Example",
      "classifier": "meta::pure::metamodel::type::Class",
      "sourceInformation": ["/m/example.pure", 3, 1, 3, 7, 200, 1]
    }
  ]
}
```

Its file with metadata version 2, which legend-pure writes for this form and reads back as it, is these 89 bytes.
The end line, 200, takes the source information to the 2-byte width:

```
2F 39 EC 36 29 C7 26 AD 00 00 00 02 00 00 00 03
00 00 00 07 02 01 6D 02 07 45 78 61 6D 70 6C 65
02 07 65 78 61 6D 70 6C 65 02 08 70 6C 61 74 66
6F 72 6D 42 02 D7 D8 22 02 D9 A6 82 02 D7 DC D7
00 00 00 01 00 00 00 01 DA DB C3 DD 01 00 03 00
01 00 03 00 07 00 C8 00 01
```

An encoder of metadata version 1 rejects this form, since it has a dependency (section 7.3). legend-pure's drops
the dependency instead (section 12, item 4).
