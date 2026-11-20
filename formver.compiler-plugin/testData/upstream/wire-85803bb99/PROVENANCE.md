# Wire ByteArrayProtoReader32 at revision 85803bb99

Upstream: https://github.com/square/wire
Commit: 85803bb996496b0ecf19806a1e561cafd3a0d0a0, the parent of
25ebcabb9ab7f12d1d77af75ecbc51726fddc015 ("Prevent overflow for 32-bit length integers"),
which fixes GHSA-9rm7-3qhh-h2mc (CVE-2026-63126).
Source: wire-runtime/src/commonMain/kotlin/com/squareup/wire/ByteArrayProtoReader32.kt
Git blob: dfc3b6f028c0645707070e9a3d0a26f2f09bc6ea
SHA-256: 7b4aeac3ef0acb19cb248f5ffbc3cb0c4b5d5c09ca2558801407893d625059f3
License: Apache-2.0; LICENSE.txt is copied from the repository root at this commit. The source
header carries the BSD notice of the protobuf code it derives from.

The fixture programs/ports/wire/lengthOverflowPreCve63126 ports the members on the length
arithmetic path. Its .port.diff is the deviation list, checked by PortCorrespondenceTest.
Labels:

- D-1: test directives, and the formver and JDK imports in place of the package declaration.
  `okio.EOFException` and `com.squareup.wire.internal.ProtocolException` are typealiases of the
  imported `java.io.EOFException` and `java.net.ProtocolException` on the JVM.
- D-2: the ProtoReader companion constants and the `Byte.and`/`Byte.shl` helpers of
  internal/Util.kt, copied from the same commit, and a FieldEncoding stand-in that keeps only its
  entries.
- D-3: no ProtoReader32 supertype, so no `override` modifiers.
- D-5: `@NeverVerify` on the constructor. This revision's constructor checks nothing; the fixture
  assumes `0 <= pos <= limit <= source.size` as the class law, which this revision's constructor does not check.
- D-7: removed members.

The specification is the class law and `0 <= byteCount` on `skip` and `readUtf8`, whose callers
other than skipGroup pass a remaining length.
