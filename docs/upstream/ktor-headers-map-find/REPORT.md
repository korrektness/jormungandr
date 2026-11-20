# Ktor HttpHeadersMap.find probe

Status: prepared, NOT submitted; submission needs operator authorization.

The deprecated public `HttpHeadersMap.find(name, fromIndex)` sets
`offset = (offset / HEADER_SIZE) % headerCapacity` inside its loop, which
does not move to the next header. It returns -1 for a present header, and for
an absent name with the first header in slot 0 it does not return. Census
row: [census-ktor-obligations.md](../../targets/census-ktor-obligations.md),
ktor-http-cio `HttpHeadersMap.kt` `HttpHeadersMap.find`, class D1.

## Verifier diagnostic

```text
HttpHeadersMap.kt, in find: error: [INTEGER_OVERFLOW] Could not establish that + stays within the Int range [-2147483648, 2147483647].
```

The overflow is `nextIndex++` in a loop that does not end. The same loop's
`DIVISION_BY_ZERO` diagnostic holds by the map's capacity invariant and
is class B in the census.

## Upstream status (checked 2026-11-20)

ktorio/ktor `main` has the same code; the defect is in releases 3.1.2 to 3.6.0. No existing report found.
Existing reports were searched with the YouTrack REST search in project KTOR and with `gh search issues` and `gh search prs` on ktorio/ktor. Search tools match on text, so a report phrased differently could be missed.

## Unit test

`run-test.sh <classpath> [patched Ktor checkout]` compiles the test against the Ktor 3.6.0 JVM jars on `<classpath>` (with their dependencies, JUnit 4 and Hamcrest) and runs it with JUnit 4 under `-Xmx512m`. With a checkout argument it first compiles the patched source file from that checkout ahead of the jar. Runs used kotlinc 2.3.0 and JDK 21.0.10, with the jars from the census build of tag `3.6.0`.

| Test | 3.6.0 | 3.6.0 with the patch |
|:--|:--|:--|
| `HeadersFindTest` | fails: `expected:<1> but was:<-1>` | passes |

A scratch check outside this package parsed one header named `h91` (slot 0)
and called `find("absent")`: on 3.6.0 the call had not returned after 3 s;
with the patch it returns -1.

## Patch

`headers-map-find-probe.patch` walks `offsets()`, the index order
`nameAt` and `valueAt` use. A negative `fromIndex` throws; a
`fromIndex` at or past `size` returns -1. `git apply
--check` succeeds on `main` and on `3.6.0`. Not built with Ktor's Gradle
build; JVM only.
