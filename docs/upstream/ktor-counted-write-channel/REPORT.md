# Ktor CountedByteWriteChannel byte-count overflow

Status: prepared, NOT submitted; submission needs operator authorization.

`CountedByteWriteChannel` keeps `flushedCount` in an `Int`, so
`totalBytesWritten`, a `Long`, is wrong after 2^31 bytes. Census rows:
[census-ktor-obligations.md](../../targets/census-ktor-obligations.md), ktor-io
`CountedByteWriteChannel.kt` `CountedByteWriteChannel.totalBytesWritten` and
`CountedByteWriteChannel.flush`, class D1.

## Verifier diagnostic

```text
CountedByteWriteChannel.kt, in totalBytesWritten: error: [INTEGER_OVERFLOW] Could not establish that + stays within the Int range [-2147483648, 2147483647].
CountedByteWriteChannel.kt, in flush: error: [INTEGER_OVERFLOW] Could not establish that + stays within the Int range [-2147483648, 2147483647].
```

## Upstream status (checked 2026-11-20)

ktorio/ktor `main` has the same code; `flushedCount` is an `Int` in every
release from 3.0.0 to 3.6.0. No existing report found. Existing reports were searched with the YouTrack REST search in project KTOR and with `gh search issues` and `gh search prs` on ktorio/ktor. Search tools match on text, so a report phrased differently could be missed.

## Unit test

`run-test.sh <classpath> [patched Ktor checkout]` compiles the test against the Ktor 3.6.0 JVM jars on `<classpath>` (with their dependencies, JUnit 4 and Hamcrest) and runs it with JUnit 4 under `-Xmx512m`. With a checkout argument it first compiles the patched source file from that checkout ahead of the jar. Runs used kotlinc 2.3.0 and JDK 21.0.10, with the jars from the census build of tag `3.6.0`. The test writes 2^31 bytes in 1 MiB chunks to a channel that a
second coroutine discards; it runs in about 1 s.

| Test | 3.6.0 | 3.6.0 with the patch |
|:--|:--|:--|
| `CountedTotalBytesOverflowTest` | fails: `expected:<2147483648> but was:<-2147483648>` | passes |

## Patch

`counted-write-channel-long-count.patch` makes `flushedCount` a `Long`,
as `CountedByteReadChannel` counts. `git apply --check` succeeds on `main`
and on `3.6.0`. Not built with Ktor's Gradle build; JVM only.

A scratch check outside this package found that bytes already buffered in the
delegate before `counted()` are counted at the first `flush()`, because
`flush()` adds `writeBuffer.size` without subtracting `initial`. The
patch does not change that.
