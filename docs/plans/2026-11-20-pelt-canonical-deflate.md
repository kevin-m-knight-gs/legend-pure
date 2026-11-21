# Canonical deflate encoder: specification

Status: draft for the prototype, 2026-11-20. It becomes normative only if the decision gate adopts Option 1, and it
then moves into the format specification that Phase 0 writes.

This is the encoder that
[Option 1 of the byte-identity plan](2026-10-15-pelt-byte-identity.md#option-1-a-specified-canonical-deflate-encoder)
calls for, written for the prototype in its
[Phase 4](2026-10-15-pelt-byte-identity.md#phase-4-measurements-encoder-prototype-and-decision-gate). It defines
exactly one output for each input and setting. The output is raw deflate ([RFC 1951]), with no zlib or gzip
wrapper, so any inflater decodes it, and readers need nothing new. Every choice that RFC 1951 leaves to an encoder
is fixed here: matches, block boundaries, block types, Huffman code lengths, and the encoding of code lengths.

The prototype's measurements are in
[PELT compression measurements](2026-11-18-pelt-compression-measurements.md#10-the-canonical-encoder).

## 1. Settings and constants

A setting fixes five values. A frozen encoder has exactly one setting, and changing any value changes the output.

| Value | Meaning | Level-7-like | Level-9-like |
|---|---|---|---|
| `good` | A pending match at least this long cuts the chain limit to a quarter | 8 | 32 |
| `lazy` | A pending match at least this long is emitted without a search at the next position | 32 | 258 |
| `nice` | A search stops at a match at least this long | 128 | 258 |
| `chain` | The most candidates a search visits | 256 | 4,096 |
| `block` | Symbols per block | 16,384 | 16,384 |

The first four values are zlib's for levels 7 and 9.

Constants: a window of 32,768 bytes; matches of 3 to 258 bytes; a "too far" distance of 4,096 for matches of
3 bytes; and a hash of 15 bits.

## 2. Matching

The input is bytes d[0] to d[n − 1], with n ≥ 0. Positions are indexes into the input.

### 2.1 Hash

For a position i with i + 3 ≤ n:

h(i) = ((d[i] + 2^8 · d[i + 1] + 2^16 · d[i + 2]) · 0x9E3779B1 mod 2^32) div 2^17

That is, the three bytes as a little-endian 24-bit integer, multiplied by 0x9E3779B1 in unsigned 32-bit
arithmetic, keeping the top 15 bits.

### 2.2 Hash chains

There are two tables. `head` has 2^15 entries, all empty at the start of each input. `prev` has an entry for each
position. Inserting position i, when i + 3 ≤ n, sets prev[i] to head[h(i)], and then head[h(i)] to i. Positions
with i + 3 > n are never inserted. A chain starts at an entry of `head` and continues from each position c to
prev[c], so it visits earlier positions in turn, from the most recent.

### 2.3 The longest match

The longest match at a position p is defined when p + 3 ≤ n, and is found before p is inserted. It depends on L,
the length of the pending match (2.4), or 0 if there is none:

1. **Chain limit:** `chain` div 4 if L ≥ `good`, otherwise `chain`.
2. **Limits:** the length limit is min(258, n − p), and the stop length is min(`nice`, the length limit).
3. **Candidates:** head[h(p)], then down its chain. The search ends at an empty entry, at a candidate c with
   p − c > 32,768, or once it has visited as many candidates as the chain limit.
4. **A candidate's length:** the number of leading bytes in which the input at c and at p agree, up to the length
   limit. A candidate whose hash only collides has a length under 3.
5. **The best candidate:** the first visited of those with the greatest length. The search ends early as soon as
   that length reaches the stop length.
6. **Result:** a match, of the best length and distance p − c, if the best length is at least 3; otherwise none.
   A match of length 3 at a distance over 4,096 counts as none.

### 2.4 Parsing

The parse turns the input into a sequence of symbols: literals (one byte) and matches (a length and a distance).
It holds back each position's byte for one step, to see whether the next position has a longer match (lazy
matching). Below, `held` means that d[p − 1] has not been emitted yet, and M is the match found at p − 1, or none.

```
p ← 0; held ← false
while p < n:
    m ← none
    if p + 3 ≤ n:
        unless held and M is a match of length ≥ lazy:
            m ← the longest match at p, with L = M's length if held and M is a match, else 0
        insert p
    if held and M is a match and (m is none or m's length ≤ M's length):
        emit M
        insert each position q with p < q < p − 1 + M's length and q + 3 ≤ n
        p ← p − 1 + M's length; held ← false
    else:
        if held: emit the literal d[p − 1]
        held ← true; M ← m; p ← p + 1
if held: emit the literal d[n − 1]
```

## 3. Blocks

### 3.1 Division

The symbol sequence splits into groups of `block` symbols, and the last group holds the rest. An empty input has
one empty group. Each group is written as one block of the type that 3.5 chooses, except that stored data longer
than 65,535 bytes is written as several stored blocks (3.6). BFINAL is 1 on the last block written for the last
group, and 0 on every other block.

### 3.2 Frequencies

In each group, a literal counts its byte as a literal/length symbol, and a match counts one length symbol (257 to
285) and one distance symbol (0 to 29), as [RFC 1951] section 3.2.5 assigns them. A length of 258 is always
symbol 285. The end-of-block symbol, 256, counts once.

### 3.3 Code lengths

The lengths for an alphabet with frequencies f, limited to B bits (15 for the literal/length and distance codes,
7 for the code length code), are as follows. Symbols with f > 0 *occur*.

1. **At most two symbols occur:** each that occurs gets length 1. If fewer than two occur, the lowest-numbered
   symbols that do not occur also get length 1, until two symbols have it. Every other symbol gets length 0.
2. **Otherwise, package-merge.** Let n be the number of symbols that occur.
   1. Sort the symbols that occur by frequency, then by symbol number, both ascending. Their frequencies are their
      weights.
   2. The list at level B is those symbols, in that order.
   3. For each level ℓ from B − 1 down to 1: pair the items of the list at level ℓ + 1 in order (the first with
      the second, the third with the fourth, and so on, dropping a last item without a partner) into packages,
      each weighing the sum of its two items. The list at level ℓ merges the sorted symbols with these packages
      in ascending order of weight, with a symbol before a package of equal weight.
   4. Select the first 2n − 2 items of the list at level 1. Then, at each level from 1 down: every selected
      symbol's length grows by 1, and if k of the selected items are packages, the first 2k items of the list at
      the next level are selected.

Either way the code is complete, and in case 2 it is optimal among codes of at most B bits. The codes follow from
the lengths, as [RFC 1951] section 3.2.2 specifies.

### 3.4 The dynamic block header

1. **HLIT** is max(257, one more than the highest literal/length symbol with a nonzero length). **HDIST** is
   max(1, one more than the highest distance symbol with a nonzero length).
2. **The code length sequence** is the HLIT literal/length code lengths followed by the HDIST distance code
   lengths, as one sequence, so a run can continue from one into the other ([RFC 1951] section 3.2.7 allows it).
3. **Run-length encoding.** Split the sequence into maximal runs of equal values, and encode each run on its own:
   - **r zeros:** while r ≥ 11, a 18 repeating min(r, 138) zeros, taken from r. Then, if r ≥ 3, a 17 repeating
     r zeros. Then any remaining zeros, one by one.
   - **r copies of v > 0:** v itself, and r − 1 remain. While at least 3 remain, a 16 repeating min(remaining,
     6). Then any remaining copies, one by one.
4. **The code length code:** the frequencies of the 19 symbols in that encoding, with lengths by 3.3 for B = 7.
5. **HCLEN** counts the code length code's lengths written in the order 16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4,
   12, 3, 13, 2, 14, 1, 15, without trailing zeros, and at least 4.

### 3.5 The block type

The cost of each type is the exact number of bits it takes to write the group, from the 3 header bits through the
end-of-block code:

- **Stored:** from the current bit position, for each stored block (3.6): 3 header bits, the bits up to the next
  byte boundary, 32 bits for LEN and NLEN, and 8 bits per byte.
- **Fixed:** 3, plus each symbol's length in the fixed code and its extra bits, plus 7 for end of block.
- **Dynamic:** 3 + 5 + 5 + 4 + 3 · HCLEN, plus each run-length symbol's code length and its extra bits (2, 3, and
  7 for 16, 17, and 18), plus each symbol's code length and extra bits, plus the end-of-block code's length.

The smallest wins. Ties go to stored, then fixed, then dynamic.

### 3.6 Writing

As [RFC 1951] specifies: bits are packed least significant first, and Huffman codes most significant bit first.
The fixed code is that of section 3.2.6, with all 288 literal/length symbols, of which 286 and 287 never occur.
Stored data is written in pieces of 65,535 bytes, the last piece holding the rest; data of 0 bytes is one stored
block. Each stored block has its header bits, zero bits up to the next byte boundary, LEN, NLEN (the one's
complement of LEN), and its bytes.

### 3.7 End

After the last block, zero bits up to the next byte boundary. Nothing follows.

## 4. How it differs from zlib

The settings come from zlib, but the encoder is not zlib's and does not reproduce its output.

- **The input is whole.** Matching works on positions in the input, with no sliding buffer, and distances reach
  the full window of 32,768. zlib slides its window and stops 262 bytes short of it.
- **The hash.** zlib shifts and XORs the three bytes, which drops three bits of the first. The multiplicative hash
  spreads all 24 bits. With the level-7-like setting, it gave 0.02% fewer bytes than zlib's hash on platform's
  files, and 0.04% fewer on legend-engine's.
- **Code lengths.** Package-merge gives optimal lengths under the limit by a short rule. zlib builds a Huffman tree
  with a heap and then shortens any code over the limit, and its ties follow its heap.
- **Complete codes.** Every alphabet gets at least two codes, as in zlib, so every code is complete. RFC 1951
  allows a single distance code of one bit, but complete codes are the case that every inflater accepts.
- **Code lengths run across the two sequences.** zlib encodes the literal/length and distance code lengths
  separately.
- **The block type is chosen by exact costs,** including the stored cost at the actual bit position.

## 5. Test vectors

The inputs need no files:

| Vector | Input |
|---|---|
| `empty` | No bytes |
| `one-byte-00`, `one-byte-61`, `one-byte-ff` | The one byte 0x00, 0x61, or 0xFF |
| `abc-x10` | `abc` ten times |
| `pangram-x2` | `The quick brown fox jumps over the lazy dog.` twice, with one space between |
| `a-x1000000` | The byte 0x61, 1,000,000 times |
| `bytes-0-255-x4` | The bytes 0 to 255 in order, four times |
| `lcg-100000` | 100,000 bytes from the generator below |
| `lines-5000` | For each n from 0 to 4,999, the line `line n: the quick brown fox jumps over the lazy dog`, with n in decimal, ending in a line feed |
| `lcg-32768-copy-1000` | 32,768 bytes from the generator, followed by a copy of its first 1,000: a copy at exactly the window's distance |
| `lcg-32769-copy-1000` | The same with 32,769 bytes, so the copy lies one byte beyond the window |

The generator starts from x = 1, and for each byte sets x to (1,103,515,245 · x + 12,345) mod 2^31 and takes bits
16 to 23 of x. Text is ASCII.

Outputs are given in hex when they have at most 6 bytes, and otherwise as SHA-256. Both settings give the same
output except for `lines-5000`.

| Vector | Input bytes | Input SHA-256 | Output bytes | Output |
|---|---|---|---|---|
| `empty` | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` | 2 | `0300` |
| `one-byte-00` | 1 | `6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d` | 3 | `630000` |
| `one-byte-61` | 1 | `ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb` | 3 | `4b0400` |
| `one-byte-ff` | 1 | `a8100ae6aa1940d0b663bb31cd466142ebbdbd5187131b92d93818987832eb89` | 3 | `fb0f00` |
| `abc-x10` | 30 | `ebae678bcd141cc9b9d532b264f854096b645af41490c787218299e1bbc6510e` | 6 | `4b4c4ac68300` |
| `pangram-x2` | 89 | `635241ac823ee4a81fbb410c92be616b0a89191083d8d7b5d232c823dc8df4f5` | 48 | `c7982026e28934394d2f5831f59e601c65002394e7953d4a4783d941c9b48a1d` |
| `a-x1000000` | 1,000,000 | `cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0` | 986 | `648ecd686bb5cc814c8f02f5f10bce205f7307b0491a7700610c303adad5b1ce` |
| `bytes-0-255-x4` | 1,024 | `785b0751fc2c53dc14a4ce3d800e69ef9ce1009eb327ccf458afe09c242c26c9` | 279 | `02fa4c1018d01746438abee71aab3879b4a9bf5d7662b7115423f3ea1bfb9b3c` |
| `lcg-100000` | 100,000 | `1ef37abda5dc5ec15556f061d1a8fc9a547458583918dcca8d89c17b38f54fcd` | 100,035 | `74dcb44593bad6fd392425728d603d13774cd25edb29ce6e1ae4cc4a94554b34` |
| `lines-5000`, level-7-like | 273,890 | `327c6f2071428ba1e777d790fc2246492101172b735e6f886b0258e8c2d7fa18` | 12,882 | `68a30b96ebcab289ba11a0a171c1c2029417b7ea5a9a0b44c69383e5aec2890d` |
| `lines-5000`, level-9-like | 273,890 | `327c6f2071428ba1e777d790fc2246492101172b735e6f886b0258e8c2d7fa18` | 12,684 | `820ebba5a7367e40fae8a6ec1964c556053fe3925dc5bbe60d3deeed4e1f6c33` |
| `lcg-32768-copy-1000` | 33,768 | `5d579bc36184112cf0f4e4f423d4c8b793235fa8a26d55abb4be602178f3ebc4` | 32,828 | `92dd36412e0992aa33ea897f4004e07e09d18e7180a2a62ccee280ba0cf90166` |
| `lcg-32769-copy-1000` | 33,769 | `c96d151822d3cac1a2e0b1db5f034a88e100d5b998961d87702a854749f48741` | 33,784 | `15d49ccd3dbfbca23b0fd2b2b15be3833e270fe3506be8daee2a0e805d2ad352` |

Between them, the vectors cover the cases that Phase 4 lists: empty input, one byte, a long run of one byte,
random data (stored blocks), input longer than the window, and input longer than one block.

## 6. Conformance and provenance

An implementation conforms when it reproduces these vectors and the conformance corpus that Phase 3 builds. The
prototype's own tests also check, for many generated inputs, that every output inflates to its input.

The prototype was written from [RFC 1951] and this document, with no code from any library. Package-merge is due
to Larmore and Hirschberg (1990). The hash multiplier 0x9E3779B1 is the prime closest to 2^32 divided by the
golden ratio, the usual constant for Knuth's multiplicative hashing. Algorithms and parameter values are not
covered by copyright.

[RFC 1951]: https://www.rfc-editor.org/rfc/rfc1951
