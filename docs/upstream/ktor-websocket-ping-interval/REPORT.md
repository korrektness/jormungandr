# Ktor client WebSockets ping-interval timeout overflow

Status: prepared, NOT submitted; submission needs operator authorization.

`WebSockets` computes the session timeout as `pingIntervalMillis * 2`. For
`pingInterval = Duration.INFINITE` the interval is `Long.MAX_VALUE`, and the
product wraps to -2. Census row: [census-ktor-obligations.md](../../targets/census-ktor-obligations.md),
ktor-client-core `WebSockets.kt` `WebSockets.convertSessionToDefault`, class D1.

## Verifier diagnostic

```text
WebSockets.kt, in convertSessionToDefault: error: [INTEGER_OVERFLOW] Could not establish that * stays within the Long range [-9223372036854775808, 9223372036854775807].
```

## Upstream status (checked 2026-11-20)

ktorio/ktor `main` has the same expression. No existing report found. Existing reports were searched with the YouTrack REST search in project KTOR and with `gh search issues` and `gh search prs` on ktorio/ktor. Search tools match on text, so a report phrased differently could be missed.

## Unit test

`run-test.sh <classpath> [patched Ktor checkout]` compiles the test against the Ktor 3.6.0 JVM jars on `<classpath>` (with their dependencies, JUnit 4 and Hamcrest) and runs it with JUnit 4 under `-Xmx512m`. With a checkout argument it first compiles the patched source file from that checkout ahead of the jar. Runs used kotlinc 2.3.0 and JDK 21.0.10, with the jars from the census build of tag `3.6.0`.

| Test | 3.6.0 | 3.6.0 with the patch |
|:--|:--|:--|
| `PingIntervalOverflowTest` | fails: `AssertionError: timeoutMillis = -2` | passes |

## Patch

`ping-interval-timeout-overflow.patch` saturates the doubling with
`coerceAtMost(Long.MAX_VALUE / 2) * 2`. `git apply --check` succeeds on
`main` and on `3.6.0`; the hunk has one line of context because the
surrounding lines differ between them. With an infinite interval no ping is
sent, so the negative value is visible through `timeoutMillis` and
`timeout`. Not built with Ktor's Gradle build; JVM only.
