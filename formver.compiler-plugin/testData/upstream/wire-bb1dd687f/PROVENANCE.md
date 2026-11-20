# Wire ByteArrayProtoReader32 at revision bb1dd687f

Upstream: https://github.com/square/wire
Commit: bb1dd687f2099ef30e7c5775ecc8b534ef57857c, master on 2026-11-20. It contains the fixes for
CVE-2026-63126 (25ebcabb9) and GHSA-7xpr-hc2w-34m9 (e4e56fab3).
Source: wire-runtime/src/commonMain/kotlin/com/squareup/wire/ByteArrayProtoReader32.kt
Git blob: e70db5175d6bf2374332ebbc09d5f7fbe0c404af
SHA-256: d6598eda86e71e8eff5be798e40a8100e0777a1f501b111e963fc2115a081e15
License: Apache-2.0; LICENSE.txt is copied from the repository root at this commit. The source
header carries the BSD notice of the protobuf code it derives from.

The fixture programs/ports/wire/ByteArrayProtoReader32 ports the class, including its constructor,
except the members D-7 removes. Its .port.diff is the deviation list, checked by
PortCorrespondenceTest. Labels D-1, D-2, D-3 and D-7 are those of
../wire-85803bb99/PROVENANCE.md, and D-4 is that of ../wire-50ba803e8/PROVENANCE.md.
D-2 also adds a ByteString stand-in whose only member is `EMPTY`.
D-7 removes unknown-field buffering and reading, merge state, `asProtoReader`, `lastTag`,
`readBytes` and `readByteString`. Further labels:

- D-5: `@NeverVerify` on `readVarint64` and `readIntLe`, whose verification does not finish
  within the land gate's time limit. Their bodies are trusted to keep the class law.
- D-6: each `require(c) { message }` is written as `if (!(c)) throw IllegalArgumentException(message)`
  and each `check(c) { message }` as `if (!(c)) throw IllegalStateException(message)`, the
  exceptions the stdlib functions throw. Without `REPLACE_STDLIB_EXTENSIONS`, SnaKt rejects these
  calls as a lambda that is not invoked in place; with it, they convert to no-ops.
- D-8: `readLongLe` widens each byte with `toLong()` before masking instead of calling the
  `Byte.and(Long)` helper, which computes the same value and stays in the D-2 copy of Util.kt
  without a caller. Verification of the helper form does not finish within the time limit.

The specification:

- The class law `framing`: `0 <= pos <= source.size`, `limit <= source.size`, `pos <= limit` unless
  `limit` is -1, and, while a limit is pushed, `pos <= limit <= pushedLimit <= source.size`.
  `limit` is -1 after `endMessageAndGetUnknownFields(-1)` ends a top-level message.
- `endMessageAndGetUnknownFields` requires its token to be -1 or within `[pos, source.size]`, the
  values `beginMessage` returns.
- Postconditions on `checkedLimit`, `remainingInLimit` and `requireNonNegativeLength` state the
  ranges they establish and that they leave `pos`, `limit` and `pushedLimit` unchanged.

With these, every array index and every length computation outside `readVarint64` and `readIntLe`
verifies. `readUtf8` passes the verified range `[pos, checkedLimit(byteCount))` to
`decodeToString`, an unmodelled library call.

The fixture states nothing about `recursionDepth`, so its increments in `beginMessage` and
`skipGroup` and its decrements in `endMessageAndGetUnknownFields` and `skipGroup` are reported as
possible Int overflows. The constructor accepts `recursionLimit == Int.MAX_VALUE`, and
`endMessageAndGetUnknownFields` decrements `recursionDepth` before the check that throws on a
negative depth, so repeated failing calls keep decreasing it.
