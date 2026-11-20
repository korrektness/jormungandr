# Landscape: Kotlin correctness defects and verification targets

Use this note to choose the SnaKt verification targets. It answers two
questions: which correctness defects in widely used Kotlin code a verifier would
have made people notice, and which Kotlin repositories care whether they are
correct. It is research only. Nothing here is implemented.

Recommendation: next take OkHttp's HPACK coder (rank 1) or Wire's
`ByteArrayProtoReader32` (rank 2). Each has 2026 defects in the obligations
SnaKt checks (overflow, index bounds, narrowing). The upstream fixes are small:
OkHttp PR #9401 changes 22 lines of `Hpack.kt`, and Wire PR #3635 changes 53
lines of `ByteArrayProtoReader32.kt`. So the demonstration is the one the
TimSort precedent used: the verifier rejects the code before
the fix and accepts the code after it. The verifier feature with the most
leverage is a specification of the exceptional outcome ("rejects iff"). Eight of
the ten candidates need it to state their owners' contract in full.

## Evidence labels and baseline

- (I) inspected: the public report, advisory or upstream source was opened
  while writing this note. Every linked report below was opened.
- (H) hypothesis: plausible, not checked.

Verifier baseline: SnaKt as specified in `SPECIFICATIONS.md`.
SnaKt checks Int and Long overflow, narrowing, division by zero, and the bounds
of `IntArray`, `ByteArray`, `CharArray`, `LongArray`, `Array<T>` and `String`
indexing. It also models bitwise operations and shifts, opt-in wrapping
arithmetic and narrowing, class laws, `while` and `for` loops over progressions,
recursive `@Pure` functions with `decreases`, and `Multiset<Int>`.

The serialization gap list is in
`~/dev/verified-kotlinx-serialization/main/docs/verification/plan.md` § SnaKt
gaps. SnaKt implements its first three gaps:

1. Gap 1, bitwise operations: implemented.
2. Gap 2, `ByteArray`, `CharArray` and `LongArray` elements: implemented.
3. Gap 3, wrapping narrowing: implemented.

Gaps 4 to 10 are open. They are the exceptional outcome, unsigned types,
loop termination, `Double`/`Float`, bit-count intrinsics, `CharSequence` sources
and descriptor models. Below, "gap N" means that list.

## 1. Public defects by proof obligation

Each obligation is coded by the property a proof would have had to establish:

| Code | Obligation |
|:--|:--|
| O | Arithmetic stays in range (overflow). |
| B | Every index is in bounds. |
| N | A narrowing or sign conversion preserves the value. |
| X | Malformed input leaves through the declared exception or result, not some other exception or a wrong value. |
| I | A data-structure or representation invariant holds. |
| S | The output conforms to a written specification: an RFC, BIP, ISO 8601 or Unicode. |

A row with two codes needs both.

SnaKt discharges O, B and N natively. I needs a class law or a
representation predicate, which SnaKt has. X needs gap 4: without it, a proof
covers only normal returns, and malformed input must come back as a sentinel.
S needs the specification written as a `@Pure` function.

### Kotlin stdlib (YouTrack)

| Report | Year | Violated contract | Code | Fixed |
|:--|:--|:--|:--|:--|
| [KT-80661](https://youtrack.jetbrains.com/issue/KT-80661) | 2025 | `ArrayDeque.lastIndexOf` returns -1 for a present element. | B, I | 2.3.0-Beta1 |
| [KT-80390](https://youtrack.jetbrains.com/issue/KT-80390) | 2025 | `ArrayDeque.indexOf(null)` on an empty deque reads a stale slot. | I | 2.3.0-Beta1 |
| [KT-70005](https://youtrack.jetbrains.com/issue/KT-70005) | 2024 | `IntArray.sort(from, to)` on an empty array accesses out of bounds (Wasm/Native). | B | 2.1.0-Beta1 |
| [KT-79239](https://youtrack.jetbrains.com/issue/KT-79239) | 2025 | `elementAt` traps instead of throwing `IndexOutOfBoundsException` (Wasm). | B, X | 2.3.0-Beta1 |
| [KT-32728](https://youtrack.jetbrains.com/issue/KT-32728) | 2019 | `windowed`/`chunked` with size `Int.MAX_VALUE` overflow into an illegal capacity. | O | 1.3.60 |
| [KT-31639](https://youtrack.jetbrains.com/issue/KT-31639) | 2019 | `Iterable.drop`'s counter overflows and drops too many elements. | O | 1.3.40 |
| [KT-82901](https://youtrack.jetbrains.com/issue/KT-82901) | 2025 | `Long.MIN_VALUE.milliseconds` builds a denormalized `Duration`. | O, I | 2.3.0-RC3 |
| [KT-80129](https://youtrack.jetbrains.com/issue/KT-80129) | 2025 | `Duration.parse` throws `NumberFormatException` for large values instead of saturating. | X, O | open |
| [KT-79791](https://youtrack.jetbrains.com/issue/KT-79791) | 2025 | `Duration.parse` gets the sign of negative fractional ISO 8601 seconds wrong. | S | 2.3.0-Beta2 |
| [KT-72380](https://youtrack.jetbrains.com/issue/KT-72380) | 2024 | `Duration.parse` turns leading-zero components into an infinite duration and accepts repeated signs. | S, X | 2.1.20-Beta1 |
| [KT-67768](https://youtrack.jetbrains.com/issue/KT-67768) | 2024 | `decodeToString` reads past the end of a truncated 4-byte UTF-8 sequence (Wasm). | B, S | 2.0.20-Beta1 |
| [KT-67807](https://youtrack.jetbrains.com/issue/KT-67807) | 2024 | JS/Wasm `decodeToString` emits the wrong number of U+FFFD for ill-formed sequences. | S | 2.0.20-Beta1 |
| [KT-33225](https://youtrack.jetbrains.com/issue/KT-33225) | 2019 | JS `Double.toLong()` maps ±Infinity to 0 instead of saturating. | N | 1.3.50 |
| [KT-48530](https://youtrack.jetbrains.com/issue/KT-48530) | 2021 | Range and progression `equals` is not symmetric. | I | 2.5.0-Beta2 |

No defect report was found for `toUInt`/`toULong` parsing, Base64, HexFormat,
Random or StringBuilder. `kotlin-arraydeque.md` covers KT-80390 and KT-80661 in
depth.

### kotlinx-datetime

| Report | Year | Violated contract | Code | Fixed |
|:--|:--|:--|:--|:--|
| [#644](https://github.com/Kotlin/kotlinx-datetime/issues/644) | 2026 | `DateTimePeriod.parse` multiplies weeks by 7 in Int before widening; wrapped day counts parse as valid. | O, N | [PR #645](https://github.com/Kotlin/kotlinx-datetime/pull/645), after 0.8.0 |
| [#263](https://github.com/Kotlin/kotlinx-datetime/issues/263) | 2023 | JS `Instant.plus(Duration)` wraps instead of clamping. | O | 0.4.1 |
| [#488](https://github.com/Kotlin/kotlinx-datetime/issues/488) | 2025 | `Instant.until` breaks its documented whole-units guarantee. | I | 0.9.0 milestone |
| [#484](https://github.com/Kotlin/kotlinx-datetime/issues/484) | 2025 | `periodUntil` returns negative components for a later instant across a DST gap. | I | 0.9.0 milestone |
| [#487](https://github.com/Kotlin/kotlinx-datetime/issues/487) | 2025 | Adding a `DateTimePeriod` to an `Instant` departs from ISO 8601 month handling. | S | 0.9.0 milestone |
| [#149](https://github.com/Kotlin/kotlinx-datetime/issues/149) | 2021 | `ZoneRules.isFixedOffset` indexes out of bounds (Android). | B | PR #150 |
| [#427](https://github.com/Kotlin/kotlinx-datetime/issues/427) | 2024 | `Instant` to `NSDate` round trip loses precision, contrary to its KDoc. | N | 0.7.0 |

### kotlinx-io

| Report | Year | Violated contract | Code | Fixed |
|:--|:--|:--|:--|:--|
| [#551](https://github.com/Kotlin/kotlinx-io/issues/551) | 2026 | `asNSOutputStream` overflows Int on buffers over 2 GiB. | O | 0.9.3 |
| [#547](https://github.com/Kotlin/kotlinx-io/issues/547) | 2026 | `Buffer.indexOf(byte)` narrows a Long offset to Int and throws on a 2 GiB buffer. | N, O | 0.9.3 |
| [#548](https://github.com/Kotlin/kotlinx-io/issues/548) | 2026 | `ByteString.substring(i, i)` accepts out-of-range indices. | B | 0.9.3 |
| [#537](https://github.com/Kotlin/kotlinx-io/issues/537) | 2026 | `ByteBuffer.getByteString` throws `OutOfMemoryError` instead of a bounds exception. | X | 0.9.3 |
| [#546](https://github.com/Kotlin/kotlinx-io/issues/546) | 2026 | `Buffer` reads the wrong byte after `moveToTail` of an empty range. | I | 0.9.3 |
| [#529](https://github.com/Kotlin/kotlinx-io/issues/529) | 2026 | Native `FileSink.write` with byteCount 0, which the contract allows, indexes out of bounds. | B, X | 0.9.2 |
| [#543](https://github.com/Kotlin/kotlinx-io/issues/543) | 2026 | JVM `readString` consumes input before rejecting an oversized byteCount. | X | open |

### Okio and OkHttp

Both repositories are at `lysine-dev`; `square/` URLs redirect.

| Report | Year | Violated contract | Code | Fixed |
|:--|:--|:--|:--|:--|
| [Okio #250](https://github.com/lysine-dev/okio/issues/250) | 2016 | `GzipSource` rejects streams over 2 GB; RFC 1952 defines ISIZE modulo 2^32. | O, S | 1.10 |
| [Okio PR #1280](https://github.com/lysine-dev/okio/pull/1280) | 2023 | A gzip XLEN above 0x7fff is read as a signed 16-bit value. | N, S | merged 2023-06 |
| [Okio PR #142](https://github.com/lysine-dev/okio/pull/142) | 2015 | Reading digits from an empty source fails while building the exception instead of throwing `EOFException`. | X | merged 2015-04 |
| [Okio PR #1295](https://github.com/lysine-dev/okio/pull/1295) | 2023 | `FileHandle` read/write of an empty array indexes out of bounds (Native). | B | merged 2023-07 |
| [Okio #128](https://github.com/lysine-dev/okio/issues/128) | 2015 | `Buffer.readByte` indexes out of bounds; the maintainer suspects an earlier call broke the buffer state. | I | closed, cause unknown |
| [Okio #645](https://github.com/lysine-dev/okio/issues/645) | 2019 | `Buffer.writeUtf8` writes past the segment limit; no explaining path was found. | I | unresolved |
| [OkHttp PR #9401](https://github.com/lysine-dev/okhttp/pull/9401) | 2026 | HPACK integer decoding (RFC 7541 §5.1) has no overflow bound. | O | merged 2026-04 |
| [OkHttp PR #9643](https://github.com/lysine-dev/okhttp/pull/9643) | 2026 | HTTP/2 SETTINGS values, unsigned 32-bit under RFC 9113, are read as signed. | N, S | merged 2026-11 |
| [OkHttp PR #9733](https://github.com/lysine-dev/okhttp/pull/9733) | 2026 | The header-list size omits RFC 7541's 32-byte per-entry overhead. | S | merged 2026-11 |
| [OkHttp #9772](https://github.com/lysine-dev/okhttp/issues/9772) | 2026 | DATA frame padding is not counted toward connection flow control. | S | [PR #9786](https://github.com/lysine-dev/okhttp/pull/9786) |
| [OkHttp PR #9537](https://github.com/lysine-dev/okhttp/pull/9537) | 2026 | DER variable-length integers can wrap a Long (the code carried a "TODO detect overflow"). | O | merged 2026-11 |
| [OkHttp #9827](https://github.com/lysine-dev/okhttp/issues/9827) | 2026 | A malformed compressed IPv6 host makes `HttpUrl` parsing index out of bounds instead of rejecting it. | B, X | closed 2026-10 |
| [OkHttp #9451](https://github.com/lysine-dev/okhttp/issues/9451) | 2026 | Dot-segment removal misses percent-encoded forms (RFC 3986 §5.2.4). | S | closed 2026-06 |
| [OkHttp #9726](https://github.com/lysine-dev/okhttp/issues/9726) | 2026 | An HTTP/1 connection returns to the pool with unread response bytes. | I, S | open |
| [OkHttp #9856](https://github.com/lysine-dev/okhttp/issues/9856) | 2026 | An interrupted read stores a truncated body in the cache as complete. | I | open |
| [OkHttp #8658](https://github.com/lysine-dev/okhttp/issues/8658), [#9325](https://github.com/lysine-dev/okhttp/issues/9325) | 2025–26 | Okio sink writes index out of bounds from the HTTP/2 and HTTP/1 writers. | B, I | #8658 closed, #9325 open |

### Ktor

The detailed reports are on YouTrack (KTOR-nnnn); the links below are the GitHub
advisory or fix PR.

| Report | Year | Violated contract | Code | Fixed |
|:--|:--|:--|:--|:--|
| [GHSA-xrr9-rh8p-433v](https://github.com/ktorio/ktor/security/advisories/GHSA-xrr9-rh8p-433v) (CVE-2020-5207) | 2020 | Request framing with both chunked Transfer-Encoding and Content-Length departs from RFC 7230. | S | 1.3.0 |
| [PR #5324](https://github.com/ktorio/ktor/pull/5324) (KTOR-9263) | 2026 | Quoted strings in chunk extensions are framed wrongly (RFC 9112 §7.1.1). | S | merged 2026-01 |
| [PR #5897](https://github.com/ktorio/ktor/pull/5897) (KTOR-9438) | 2026 | CIO server Transfer-Encoding framing departs from RFC 9112/9110. | S | merged 2026-10 |
| [PR #5110](https://github.com/ktorio/ktor/pull/5110) (KTOR-4828) | 2025 | An invalid Content-Length throws `NumberFormatException`. | X | merged 2025-10 |
| [PR #5848](https://github.com/ktorio/ktor/pull/5848) | 2026 | A non-numeric or out-of-range cookie Max-Age throws; RFC 6265 §5.2.2 says ignore it. | X, S | merged 2026-09 |
| [PR #5814](https://github.com/ktorio/ktor/pull/5814) | 2026 | Range parsing rejects optional whitespace around commas (RFC 9110 §5.6.1). | S | merged 2026-08 |
| [PR #5985](https://github.com/ktorio/ktor/pull/5985) (KTOR-10111) | 2026 | 204 responses carry Content-Length. | S | merged 2026-11 |
| [PR #4509](https://github.com/ktorio/ktor/pull/4509) (KTOR-7172) | 2024 | Client `HttpCache` indexes out of bounds. | B | merged 2024-11 |
| [PR #5829](https://github.com/ktorio/ktor/pull/5829) (KTOR-8355) | 2026 | Bad percent-encoding in a query gives an uncaught exception (500) instead of 400. | X | merged 2026-08 |
| [PR #5956](https://github.com/ktorio/ktor/pull/5956) (KTOR-10023) | 2026 | A 3.6.0 regression stops malformed paths from raising `BadRequestException`. | X | merged 2026-10 |
| [PR #6013](https://github.com/ktorio/ktor/pull/6013) (KTOR-10034) | 2026 | `mergeHeaders` compares header names case-sensitively. | S | merged 2026-11 |
| [PR #5863](https://github.com/ktorio/ktor/pull/5863) (KTOR-9868) | 2026 | A malformed TLS record header crashes the parser instead of raising `TlsException`. | X | open |

`ktor-ranges.md` lists further RFC 9110 departures in Range parsing.

### Wire

| Report | Year | Violated contract | Code | Fixed |
|:--|:--|:--|:--|:--|
| [GHSA-9rm7-3qhh-h2mc](https://github.com/square/wire/security/advisories/GHSA-9rm7-3qhh-h2mc) (CVE-2026-63126) | 2026 | A positive length overflows 32-bit position arithmetic in the `ByteArrayProtoReader32` fast path. | O | 6.4.5, 7.0.0-alpha04 |
| [PR #3597](https://github.com/square/wire/pull/3597) (GHSA-7xpr-hc2w-34m9) | 2026 | `skipGroup` accepts negative lengths. | B, X | merged 2026-05 |
| [#3050](https://github.com/square/wire/issues/3050) | 2024 | Malformed packed varints are read past the end of their field. | B, S | open |
| [#3738](https://github.com/square/wire/issues/3738) | 2026 | A length-delimited payload is accepted for a singular scalar field, which the protobuf encoding forbids. | S | [PR #3740](https://github.com/square/wire/pull/3740) |
| [#3737](https://github.com/square/wire/issues/3737) | 2026 | An incompatible wire type throws `IllegalStateException` instead of `ProtocolException`. | X | closed 2026-10 |
| [PR #3782](https://github.com/square/wire/pull/3782) | 2026 | Out-of-range proto3 JSON uint64/fixed64 wrap, and uint32 loses high bits. | N, S | merged 2026-11 |
| [PR #3799](https://github.com/square/wire/pull/3799) | 2026 | JSON uint32 accepts non-numeric text as a number. | N, S | merged 2026-11 |
| [#3348](https://github.com/square/wire/issues/3348) | 2025 | A negative hex default in a `.proto` file throws `NumberFormatException` in the compiler. | X | 5.3.6 |

### kotlinx.serialization

These are public reports only. The defects in the sibling repository's
`docs/verification/upstream-defects.md` are unfiled, so they are not counted
here.

| Report | Year | Violated contract | Code | Fixed |
|:--|:--|:--|:--|:--|
| [#3267](https://github.com/Kotlin/kotlinx.serialization/issues/3267) | 2026 | `JsonPrimitive.longOrNull` returns a wrapped value above 2^64 instead of the documented null. | O, X | open |
| [#3143](https://github.com/Kotlin/kotlinx.serialization/issues/3143) | 2026 | CBOR encodes Kotlin unsigned types as two's complement, not RFC 8949's unsigned major type. | N, S | closed 2026-04 |
| [#3190](https://github.com/Kotlin/kotlinx.serialization/issues/3190) | 2026 | A negative `@CborLabel` decodes incorrectly. | S | PR #3195 |
| [#3307](https://github.com/Kotlin/kotlinx.serialization/issues/3307) | 2026 | With `ignoreUnknownKeys`, CBOR accepts a truncated byte string. | X | open |
| [PR #3314](https://github.com/Kotlin/kotlinx.serialization/pull/3314) | 2026 | Truncated protobuf fixed-width fields decode with 0xFF-filled bytes. | X | open |
| [PR #2985](https://github.com/Kotlin/kotlinx.serialization/pull/2985) | 2025 | Buffer doubling past 1 GiB overflows Int in CBOR and protobuf output. | O | open |
| [#3276](https://github.com/Kotlin/kotlinx.serialization/issues/3276) | 2026 | Strict mode accepts unescaped control characters in strings (RFC 8259 §7). | S | open |
| [PR #2852](https://github.com/Kotlin/kotlinx.serialization/pull/2852) | 2024 | `JsonLiteral.long` accepts trailing garbage and throws the wrong exception type. | X | merged 2024-11 |
| [#779](https://github.com/Kotlin/kotlinx.serialization/issues/779) | 2020 | A bad JSON number throws a raw `NumberFormatException`. | X | closed 2020-04 |

### Compose runtime (Google Issue Tracker)

| Report | Year | Violated contract | Code | Fixed |
|:--|:--|:--|:--|:--|
| [b/370536104](https://issuetracker.google.com/issues/370536104) | 2024 | Adding `Int.MAX_VALUE` to `SnapshotIdSet` recurses without bound. | O, I | 1.8.0-alpha05 |
| [b/570534659](https://issuetracker.google.com/issues/570534659) | 2026 | The `SnapshotIdSet.andNot` fast path gives wrong results when `belowBound` is shared. | I | 1.14.0-alpha01 |
| [b/570652672](https://issuetracker.google.com/issues/570652672) | 2026 | Nested snapshot apply misapplies merged records. | I | 1.14.0-alpha01 |
| [b/249076084](https://issuetracker.google.com/issues/249076084) | 2022 | A faulty SlotTable range check for markers indexes out of bounds. | B | 1.3.0-rc01 |
| [b/383769314](https://issuetracker.google.com/issues/383769314) | 2024 | The slot index is zero after exiting a movable group. | I | 1.9.0-alpha01 |
| [b/442791065](https://issuetracker.google.com/issues/442791065) | 2025 | `SnapshotMutationPolicy.merge` receives an inconsistent `previous`. | I | 1.10.0-alpha05 |
| [b/169471813](https://issuetracker.google.com/issues/169471813), [b/169425446](https://issuetracker.google.com/issues/169425446) | 2020 | `SnapshotStateList` range checks are wrong. | B | fixed, release unknown |

### Bitcoin and cryptography libraries

| Report | Year | Violated contract | Code | Fixed |
|:--|:--|:--|:--|:--|
| [bitcoin-kmp PR #177](https://github.com/ACINQ/bitcoin-kmp/pull/177) | 2026 | Five fixes. An oversized varint is truncated (N). A malformed DER length crashes verification (X). A taproot SIGHASH_SINGLE output index is unchecked (B). MuSig2 nonce reuse is possible (I). P2WPKH PSBT signing trusts a supplied script (I). | N, X, B, I | merged |
| [bitcoin-kmp PR #202](https://github.com/ACINQ/bitcoin-kmp/pull/202) | 2026 | An extended key whose payload is not the 78 bytes of BIP32 decodes silently to another key. | I, S | merged |
| [bitcoin-kmp PR #203](https://github.com/ACINQ/bitcoin-kmp/pull/203) | 2026 | BIP32 child numbers outside unsigned 32-bit are accepted; an empty path component throws the wrong exception. | N, X | merged |
| [bitcoin-kmp PR #193](https://github.com/ACINQ/bitcoin-kmp/pull/193) | 2026 | The taproot key fingerprint is sign-extended, so values with the high bit set never match (BIP371). | N | merged |
| [bitcoin-kmp #180](https://github.com/ACINQ/bitcoin-kmp/issues/180) | 2026 | `fingerprint()` is not truncated to 32 bits. | N | PR #181 |
| [bitcoin-kmp PR #204](https://github.com/ACINQ/bitcoin-kmp/pull/204) | 2026 | The base58 address payload length is not checked. | S | merged |
| [bitcoin-kmp PR #158](https://github.com/ACINQ/bitcoin-kmp/pull/158) | 2025 | Bech32 decoding does not reject characters outside its charset (BIP173), and later indexing goes out of bounds. | B, S | merged |
| [bitcoin-kmp PR #85](https://github.com/ACINQ/bitcoin-kmp/pull/85) | 2023 | OP_PUSHDATA encoding is wrong at a 255-byte push. | S | merged |
| [bitcoin-kmp PR #189](https://github.com/ACINQ/bitcoin-kmp/pull/189) | 2026 | Native PBKDF2 is wrong for some key lengths (RFC 8018). | S | merged |
| [bitcoin-kmp PR #208](https://github.com/ACINQ/bitcoin-kmp/pull/208) | 2026 | Signature verification throws on unparseable signatures instead of returning false. | X | merged |
| [secp256k1-kmp #91](https://github.com/ACINQ/secp256k1-kmp/issues/91) | 2023 | An unchecked recovery id reaches the native library's abort. | X | PR #94 |
| [lightning-kmp #802](https://github.com/ACINQ/lightning-kmp/issues/802) | 2025 | BOLT11 recovery and verification disagree on high-s signatures (found by fuzzing). | I | closed |
| [lightning-kmp #893](https://github.com/ACINQ/lightning-kmp/issues/893) | 2026 | Unknown signature TLVs are hashed into the BOLT12 merkle root. | S | open |
| [lightning-kmp #885](https://github.com/ACINQ/lightning-kmp/issues/885) | 2026 | `point` fields are not checked to lie on the curve (BOLT 1). | S | open |
| [lightning-kmp #186](https://github.com/ACINQ/lightning-kmp/issues/186) | 2021 | Unbounded recursion while decoding. | I | closed |
| [kotlin-bip39 #270](https://github.com/zcash/kotlin-bip39/issues/270) | 2024 | Audit finding: Unix `SecureRandom` ignores a short `read()`. | I | closed |
| [bignum #292](https://github.com/ionspin/kotlin-multiplatform-bignum/issues/292) | 2024 | `ModularBigInteger.negate` returns `this`. | I | closed |
| [bignum #290](https://github.com/ionspin/kotlin-multiplatform-bignum/issues/290) | 2024 | `or` is wrong on negative operands. | S | closed |
| [bignum #324](https://github.com/ionspin/kotlin-multiplatform-bignum/issues/324) | 2025 | `shl` by a negative distance is wrong. | S | closed |
| [KotlinCrypto hash #114](https://github.com/KotlinCrypto/hash/issues/114) | 2025 | An invalid BLAKE2 key length throws the wrong exception type. | X | closed |
| [KotlinCrypto random #9](https://github.com/KotlinCrypto/random/issues/9) | 2024 | Browser JS throws for requests over 65,536 bytes. | X | closed |
| [KEthereum #52](https://github.com/komputing/KEthereum/issues/52) | 2019 | RLP encodes 0 as `00` instead of `0x80`. | S | closed |

### What the tables say

- The defects cluster in three places: binary decoders (protobuf, CBOR, HPACK,
  DER, gzip, Bech32, varints), text parsers with numeric accumulation (ISO 8601,
  JSON numbers, HTTP headers, URLs), and byte buffers (Okio and kotlinx-io
  segments).
- O, B and N defects are common in 2026 (OkHttp, Wire, kotlinx-io,
  kotlinx-datetime, bitcoin-kmp). Their fixes are small; kotlinx-datetime
  PR #645 changes one line. SnaKt proves these obligations.
- S is the most frequent code (36 of 102 rows), and X is second (27). Owners
  treat "malformed input raises the declared exception" as part of the contract:
  Wire issued advisories for it, and Ktor fixed malformed requests answered with
  500 instead of 400 (PR #5829, PR #5956). SnaKt cannot state X without gap 4.
- I defects are less frequent than S or X (23 rows, against 36 and 27); B has
  17, O 15 and N 13. I and S cost more to specify than O, B and N. A test cannot
  settle an I report without a root cause, such as Okio #645; a class law can. A
  class law found this kind of defect in the KeY precedents (TimSort,
  IdentityHashMap).

## 2. Precedents that got wide attention

| Precedent | What was found or proved | Why it landed |
|:--|:--|:--|
| JDK binary search ([Bloch, 2006](https://research.google/blog/extra-extra-read-all-about-it-nearly-all-binary-searches-and-mergesorts-are-broken/)) | `(low + high) / 2` overflows. | Ubiquitous code, a one-line witness and a one-line fix. Found in the field, not by a tool. |
| TimSort ([de Gouw et al., CAV 2015](https://ir.cwi.nl/pub/23709)) | `mergeCollapse` does not maintain the run-length invariant, giving `ArrayIndexOutOfBoundsException`. A repaired version was proved in KeY. | Java, Android and Python all use it. The authors supplied a generator for failing inputs and a small fix. Python merged it ([bpo-23515](https://bugs.python.org/issue23515)). The OpenJDK response was not confirmed here. |
| OpenJDK `LinkedList` ([Hiep et al., TACAS 2020](https://arxiv.org/abs/1911.04195v1)) | With 2^31 or more elements `size()` goes negative, violating its Javadoc. A repaired version was proved in KeY. | A written contract (the Javadoc) made the defect indisputable. It won a [Google award](https://www.key-project.org/2023/07/23/cwi-researchers-win-google-award-for-finding-a-bug-in-javas-linkedlist-using-key/). |
| Dual-pivot quicksort ([Beckert et al., VSTTE 2017](https://www.key-project.org/2017/08/17/dual-pivot/)) | Sortedness, permutation and absence of overflow proved. A loop invariant stated in a source comment was false; no code defect. | It showed that the JDK's default sort is provable. SnaKt's port of the stdlib `IntArray` quicksort has the same shape. |
| `IdentityHashMap` ([de Boer et al., iFM 2022](https://formal.kastel.kit.edu/biblio/researcher/ulbrich/pdf/identity_hash_map_ifm2022.pdf)) | Deserialization does not re-check the class invariant, so a corrupted stream makes `putForCreate` loop forever. | A class invariant broken at an input boundary (code I and X). |
| `BitSet` ([Tatman, Hiep, de Gouw, iFM 2023](https://ir.cwi.nl/pub/33589/)) | Several defects found, with repairs checked against the specification. Full proof needs bitwise proof rules in KeY, which the journal version adds. | It shows that bitwise support gates JDK-class targets; SnaKt models bitwise operations (see `SPECIFICATIONS.md`). |
| HACL\* in Firefox ([Mozilla, 2017](https://blog.mozilla.org/security/2017/09/13/verified-cryptography-firefox-57/)) | Verified Curve25519 shipped in NSS, about 20% faster than the code it replaced. | A drop-in replacement with no performance cost. |
| s2n and SAW ([Galois](https://galois.com/project/amazon-s2n/), CAV 2018) | HMAC and DRBG proved equivalent to specifications; proofs rerun in CI on every push. | The owner maintains the proofs, so they survive code changes. |
| Fiat-crypto ([Erbsen et al., S&P 2019](https://adam.chlipala.net/papers/FiatCryptoSP19/)) | Generated field arithmetic with proofs, adopted by BoringSSL (Chrome, Android). | Performance parity and generated code that needed no hand maintenance. |
| Csmith ([Yang et al., PLDI 2011](https://users.cs.utah.edu/~regehr/papers/pldi11-preprint.pdf)) | Over 325 compiler bugs found. CompCert's wrong-code bugs were only in its unverified front end. | The contrast between verified and unverified parts made the case for verification. |

No published verification of a Kotlin library that found a defect was found.

What made these land, and what follows for SnaKt:

1. Ubiquity. Every widely noticed defect sat in code that nearly every program
   runs: sort, binary search, `LinkedList`, the TLS stack.
2. A written contract. The defect violated a Javadoc, RFC or invariant that the
   owners had written, so nobody could dismiss it as a matter of
   interpretation. Choose targets whose contract is an RFC, BIP, ISO 8601,
   Unicode or KDoc.
3. A short witness and a small fix that upstream accepted. "The verifier
   rejects the code before the fix and accepts the code after it" is that
   pattern, and it needs no unreported defect. Upstream 2026 fixes in O, B and N
   code are the cheapest evidence SnaKt can produce.
4. No cost to the owner. HACL\* and Fiat-crypto replaced code with no runtime
   cost; s2n's proofs run in the owner's CI. The sibling repository's
   mirror-plus-equivalence-test structure is the SnaKt form of this.

## 3. Ranked candidate targets

Rank is the value of a proof to its owners times its fit against
`SPECIFICATIONS.md`. "Defects reached" are reports from section 1 that the
property would have rejected. "Fit" reads the code against `SPECIFICATIONS.md`
and the plan.md gap list. "Needs" is the verifier work the target lacks.

Source paths are at each repository's default branch as of 2026-11-20 (I).

### 1. OkHttp HPACK coder

Source: `okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http2/Hpack.kt`
(`Reader.readInt`, `Writer.writeInt`, the dynamic table in `Reader` and
`Writer`).

Property:

- P1: `readInt(firstByte, prefixMask)` returns the RFC 7541 §5.1 value of the
  bytes it reads, without overflow, and rejects exactly the values above
  `Int.MAX_VALUE`.
- P2: `writeInt` emits the RFC encoding, so decoding after encoding is the
  identity.
- P3: a class law for the dynamic table:
  - `headerCount == dynamicTable.size - 1 - nextHeaderIndex`;
  - the slots above `nextHeaderIndex` are non-null and the others null;
  - `dynamicTableByteCount` is the sum of their `hpackSize` and at most
    `maxDynamicTableByteCount`.

  Under P3 the lookup in `readIndexedHeader` is complete with only its
  `dynamicTable.size` check, and its `!!` cannot fail (I).

Why the owners care: OkHttp is a widely used HTTP client on Android and the JVM.
Fixes for three HPACK and SETTINGS spec defects merged in 2026, two of them on
one day.

Defects reached: PR #9401 (P1), PR #9643 (the negative table size reaches
`resizeHeaderTable`; P3 with a precondition), PR #9733 (the
header-list limit is the same per-entry sum, including the 32-byte overhead,
over decoded headers).

Fit: good. The integer code is Int and Long arithmetic with literal masks and
a variable shift. The table is a ring buffer over `arrayOfNulls<Header>`, the
same shape as the ArrayDeque work, so it reuses `Array<T>`, `fill` and class
laws.

Needs:

- An external specification of Okio `BufferedSource.readByte` (a value in
  -128..127, or an exit).
- `System.arraycopy` on `Array<T>`. Only `copyInto` is modelled.
- `ByteString.size` as an observer for `hpackSize`.
- Gap 4 for "rejects iff" in P1. Without it, P1 holds on normal returns.

### 2. Wire `ByteArrayProtoReader32`

Source: `wire-runtime/src/commonMain/kotlin/com/squareup/wire/ByteArrayProtoReader32.kt`.

Property:

- The framing law `0 <= pos <= limit <= source.size`, with `pushedLimit`
  nested inside the enclosing limit.
- Every read stays within `[pos, limit)`, and length arithmetic never
  overflows.
- `readVarint32`/`readVarint64` return the value of the protobuf varint they
  consume.
- `skipGroup` and `beginMessage` accept only non-negative lengths that fit.

Why the owners care: Square published two 2026 advisories with CVEs for decoder
crashes on malformed input. The CVE-2026-63126 advisory states the invariant its
fix enforces: every decoded or skipped byte count is non-negative and no larger
than the bytes remaining in the current message limit. A draft PR adds a decode
fuzz test
([PR #3817](https://github.com/square/wire/pull/3817)).

Defects reached: CVE-2026-63126 (O), GHSA-7xpr-hc2w-34m9 (B), #3050 (B). #3737
needs gap 4.

Fit: good. The reader is a class with a private `ByteArray`, Int cursors and
`require`d constructor bounds. Byte elements, sign extension and bitwise
operations are modelled.

Its varint specification is the one ranks 3 and 4 of the plan.md list need.
One recursive `@Pure` "value of these bytes" function therefore serves Wire and
kotlinx.serialization, and a mismatch between the two is itself evidence.

Needs: class laws over a unique array field with several `var` cursors (H: in
reach, as in the ArrayDeque proof). Gap 4 for `ProtocolException` iff
malformed.

### 3. stdlib `kotlin.time.Duration`

Source: `libraries/stdlib/src/kotlin/time/Duration.kt` and
`longSaturatedMath.kt`.

Property:

- A representation law over `rawValue`:
  - The low bit selects the unit.
  - A nanosecond value lies in `±MAX_NANOS`.
  - A millisecond value lies outside the nanosecond range and within
    `±MAX_MILLIS`, where `±MAX_MILLIS` is infinite. This law is what
    `fromRawValue`'s assertions check.
- Every constructor and arithmetic operation preserves the law and saturates
  as documented.
- `parse`/`parseIsoString` return the ISO 8601 value or reject.

Why the owners care: every coroutine timeout, `delay` and `measureTime` uses
`Duration`. Four public defects since 2024 hit the law and the parser.

Defects reached: KT-82901 (law), KT-80129 (O, X), KT-79791 and KT-72380 (S).

Fit: fair. Long shifts and the unit bit are modelled. The parser is `String`
indexing in `while` loops.

Needs:

- Value classes, which `SPECIFICATIONS.md` does not mention (H: unsupported).
- Gap 4 for the parser's rejections.
- Guidance for saturating multiplication. The `kotlinx-datetime.md` pilot timed
  out at 300 seconds on the same shape; that barrier is open.

### 4. UTF-8 codec shared by stdlib, Okio and kotlinx-io

Source:

- `libraries/stdlib/wasm/src/kotlin/text/utf8Encoding.kt`, with a JS twin
  (`decodeUtf8`, `encodeUtf8`, `codePointFrom2/3/4`);
- `okio/src/commonMain/kotlin/okio/Utf8.kt`.

Property:

- Every index stays in `[startIndex, endIndex)`.
- The output length is bounded by the input length.
- Decoding substitutes U+FFFD per maximal subpart (Unicode Standard §3.9,
  Table 3-7 well-formed sequences).
- Decoding after encoding is the identity on well-formed text.

Why the owners care: this is the text boundary of every non-JVM Kotlin program
and of OkHttp's I/O. Two 2024 stdlib defects broke exactly these properties.

Defects reached: KT-67768 (B), KT-67807 (S).

Fit: fair to good. `ByteArray`, `CharArray` and bitwise operations are modelled.
The specification is a recursive `@Pure` decoder over the byte sequence
with `decreases` on the index. One specification checks three implementations.

Needs: a model of `CharArray.concatToString` or of the string result, and gap 4
for the `throwOnMalformed` branch.

### 5. kotlinx-datetime ISO 8601 period parsing

Source: `core/common/src/DateTimePeriod.kt` (`DateTimePeriod.parse`).

Property: the parsed components equal the ISO 8601 period's. No Int arithmetic
wraps. A component outside the representable range is rejected.

Why the owners care: #644 is a 2026 defect whose fix (PR #645) is in no release;
the latest is 0.8.0. Malformed periods parse as valid values with wrong day
counts.

Defects reached: #644 (O, N). #263 is in the `Instant` arithmetic next to it.

Fit: good. The function is a `String` scanner with Int and Long accumulators. A
source pin, provenance checker and correspondence test exist for this library
(`kotlinx-datetime.md`).

This candidate gives the cleanest "rejected before the fix, verified after it"
demonstration among the ten.

Needs: gap 4 for the rejection contract. Without it, the overflow obligation
alone rejects the code before the fix.

### 6. OkHttp IPv6 host parsing and canonical form

Source: `okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/-HostnamesCommon.kt`
(`decodeIpv6`, `decodeIpv4Suffix`, `inet6AddressToAscii`).

Property:

- Every index into the 16-byte address and the input string is in bounds.
- `decodeIpv6` returns null for every string that is not an RFC 4291 §2.2
  address, and the address otherwise.
- `inet6AddressToAscii` produces the RFC 5952 §4 canonical form: longest zero
  run compressed, lower case, no leading zeros.

Why the owners care: every `HttpUrl`, including redirect targets, goes through
it. #9827 (2026) was an index exception on this path.

Defects reached: #9827 (B, X).

Fit: good. `String` indexing, a `ByteArray(16)`, Int arithmetic and bitwise
operations, all in `while` loops. It is small.

Needs: `inet6AddressToAscii` writes through an Okio `Buffer` (H); it needs a
`StringBuilder`-like external specification. The RFC grammar as a `@Pure`
predicate. Gap 4 for rejecting malformed addresses.

### 7. bitcoin-kmp encoding primitives

Source: `src/commonMain/kotlin/fr/acinq/bitcoin/BtcSerializer.kt`
(compact-size varint), `Bech32.kt` (`eight2five`, `five2eight`, `decode`) and
`DeterministicWallet.kt` (path and child numbers).

Property:

- Compact-size decoding inverts encoding over unsigned 64-bit values, with no
  truncation.
- `eight2five` and `five2eight` regroup bits exactly (BIP173 `convertbits`).
- Bech32 decoding indexes its tables only with checked charset positions.
- BIP32 child numbers stay in unsigned 32-bit.

Why the owners care: ACINQ's Phoenix wallet holds user funds. Its maintainers
label these fixes security issues (PR #177), and lightning-kmp is a target of
the [bitcoinfuzz](https://delvingbitcoin.org/t/the-state-of-bitcoinfuzz/1946)
differential fuzzer.

Defects reached: #177's varint truncation (N), PR #158 (B), PR #203, PR #193
and #180 (N).

Fit: fair.

- The 64-bit values need the negative-Long representation of the serialization
  kernel, or gap 5.
- Bech32 holds 5-bit groups in `Array<Byte>`, a boxed `Array<T>` whose element
  values SnaKt does not model (H).
- `polymod`'s data-dependent `xor` with a table is the case `SPECIFICATIONS.md`
  says can reach the timeout. Leave it out of the first proof.

Needs: gap 5 or the kernel's representation, `Array<Byte>` element values, and
gap 4.

### 8. Compose runtime `SnapshotIdSet`

Source: `compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/snapshots/SnapshotIdSet.kt`
in androidx.

Property:

- `get`, `set`, `clear`, `andNot`, `and` and `or` implement set membership.
  The specification is a `@Pure` membership function over the 128-bit window
  (`lowerSet`, `upperSet` from `lowerBound`) and the sorted `belowBound` array.
- A law: `belowBound` is sorted and below `lowerBound`.

Why the owners care: snapshot isolation decides what every Compose
recomposition sees. Two public defects broke this set's arithmetic and its
algebra.

Defects reached: b/370536104 (O at the upper boundary), b/570534659 (I).

Fit: fair. Long bitwise operations with a variable shift are modelled, and the
proof splits on the shift distance.

Needs:

- An immutable shared-array model. The class is immutable and shares
  `belowBound` between instances, but a shared `IntArray` read yields an
  arbitrary value in SnaKt. This gap is not on the plan.md list.
- `SnapshotId`/`SnapshotIdArray`, which are platform typealiases (H).
- Quantified set equality, through `Multiset` or a membership predicate.

### 9. kotlinx-io `Segment` and `Buffer`

Source: `core/common/src/Segment.kt` and `Buffer.kt`, and Okio's twin.

Property:

- The segment law `0 <= pos <= limit <= SIZE`, with `shared`/`owner`
  consistency.
- `Buffer.size` equals the sum of `limit - pos` over the segments.
- `indexOf` returns an offset in `[fromIndex, toIndex)` or -1, with every
  Long-to-Int narrowing proved.
- `ByteString.substring` checks bounds.

Why the owners care: this is the base of kotlinx-io, and Okio underlies OkHttp,
Wire and Coil. Seven kotlinx-io defects are dated 2026. Okio #645
has no root cause (reported 2019). That is the case a proof can settle and a
test cannot.

Defects reached: kotlinx-io #546 (I), #547 (N), #548 (B), #551 (O). Okio #128
and #645 if the law holds.

Fit: poor for the whole buffer. Segments form a circular doubly-linked list
whose data arrays are shared between buffers. SnaKt's ownership model has no
cycles, and shared objects carry no stable facts. The fit is good for
`ByteString` range checks and the per-segment `indexOf` loop.

Needs: a model of cyclic or shared linked structures with laws (research), or
a segment-array mirror like the sibling repository's.

### 10. stdlib list index and capacity helpers

Source: `libraries/stdlib/src/kotlin/collections/AbstractList.kt` companion
(`checkElementIndex`, `checkPositionIndex`, `checkRangeIndexes`,
`checkBoundsIndexes`, `newCapacity`), with `windowed`, `chunked` and `drop`.

Property:

- Each check throws exactly when its documented condition fails.
- `newCapacity` returns at least `minCapacity` without overflow.
- `windowed`/`chunked`/`drop` size arithmetic never overflows.

Why the owners care: every stdlib list and every `subList` uses these helpers.
They are the stdlib's binary-search analogue, and they sit beside the stdlib
`IntArray` sort that SnaKt ports.

Defects reached: KT-32728 (O), KT-31639 (O), KT-70005 (B), KT-79239 (X).

Fit: excellent: small Int functions. The value is in bundled external
specifications, which SnaKt users' own list code could then rely on, more than
in finding unreported defects. Tests cover these paths well (H).

Needs: gap 4. Without it, only the normal-return half of "throws iff" is
provable.

### Not ranked

- kotlinx.serialization JSON integer lexer and CBOR head codec. These are
  ranks 1 and 2 of the sibling repository's plan, in progress in that
  repository.
  The public reports #3267, #3143, #3307, PR #3314 and PR #2985 corroborate
  that ranking.
- Ktor HTTP/1 framing (GHSA-xrr9-rh8p-433v, PR #5324, PR #5897). It is suspend
  code over byte channels, which SnaKt cannot convert.
- Ktor Range merging. `ktor-ranges.md` assessed it and did not select it.
- ionspin bignum. It needs unsigned word arrays (gap 5) and nonlinear arithmetic
  whose specification is `java.math`. Differential tests find its
  defects.
- KotlinCrypto hashes. They are chains of data-dependent `xor` and rotations,
  which `SPECIFICATIONS.md` says reach the timeout, and test vectors cover them.
- lightning-kmp BOLT TLV handling. It is protocol logic over collections and
  maps with no SnaKt shape.

## Verifier features by candidate

| Feature | Candidates | Status |
|:--|:--|:--|
| Exceptional outcome ("throws iff"), gap 4 | 1, 2, 3, 4, 5, 6, 7, 10 | open |
| External specifications of Okio I/O (`readByte`, `Buffer` writes) | 1, 6 | open; pattern exists |
| `System.arraycopy` on `Array<T>` | 1 | open; `copyInto` exists |
| Value classes | 3 | not mentioned in SPECIFICATIONS.md (H) |
| Saturating-multiply guidance | 3 | open barrier (`kotlinx-datetime.md`) |
| Unsigned types, gap 5 | 7 | open; kernel representation as workaround |
| `Array<Byte>` element values | 7 | open (H) |
| Immutable shared arrays | 8, 9 | open; not on the plan.md list |
| Cyclic or shared linked structures with laws | 9 | research |

## Evidence gaps

- The Ktor YouTrack pages (KTOR-nnnn) and JDK-8072909 (TimSort) were not
  opened. The OpenJDK response to TimSort is unconfirmed.
- The defect search covered issue titles and opened bodies, not complete
  trackers. GitHub's search rate limit cut short the crypto-library sweep.
- Fit marked (H) was not checked by converting code: value classes,
  `Array<Byte>` elements, the Okio `Buffer` use in `inet6AddressToAscii`, and
  the `SnapshotId` aliases. Before committing to a candidate, run a conversion
  spike on its verbatim source.
- No candidate's upstream pin or license check has been done. The
  `kotlinx-datetime.md` provenance pattern applies.
