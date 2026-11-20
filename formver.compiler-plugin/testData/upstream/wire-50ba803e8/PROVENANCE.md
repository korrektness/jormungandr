# Wire ByteArrayProtoReader32 at revision 50ba803e8

Upstream: https://github.com/square/wire
Commit: 50ba803e865fcf43d4a4718dce851e6cf6d981dc, the parent of
e4e56fab38a547d9625f05c97f1d8f0bcc3a5773 ("Add negative-length check in skipGroup", PR #3597),
which fixes GHSA-7xpr-hc2w-34m9.
Source: wire-runtime/src/commonMain/kotlin/com/squareup/wire/ByteArrayProtoReader32.kt
Git blob: 2dfa9aafe8d9cc4ff0e5b90ee2f2b00332a92eaf
SHA-256: 1096d83f2b173efaadb3adfa0cc24439fafdeba279c95b7cd6f1cdc4d47aa52b
License: Apache-2.0; LICENSE.txt is copied from the repository root at this commit. The source
header carries the BSD notice of the protobuf code it derives from.

The fixture programs/ports/wire/negativeLengthPreGhsa7xpr ports skipGroup and its callees. Its
.port.diff is the deviation list, checked by PortCorrespondenceTest. Labels D-1, D-2, D-3 and D-7
are those of ../wire-85803bb99/PROVENANCE.md. Further labels:

- D-4: skipGroup decrements `recursionDepth` after the nested `skipGroup` call instead of in a
  `finally` block, because SnaKt rejects `try` in a function that owns its receiver. The two agree
  on normal completion. On an exceptional exit the port leaves `recursionDepth` one higher. SnaKt
  checks class laws only at normal exits, so this difference is outside what it checks.
- D-5: `@NeverVerify` on the constructor, as for the CVE fixture, and on the scalar readers that
  skipGroup calls (`readVarint64`, `readFixed32`, `readFixed64`) and on their callees
  `afterPackableScalar`, `readIntLe` and `readLongLe`. Their bodies
  are trusted to keep the class law; the fixture does not claim anything about them.

The specification is the class law `0 <= pos <= limit <= source.size` and `0 <= byteCount` on
`skip`, whose other caller passes a remaining length.
