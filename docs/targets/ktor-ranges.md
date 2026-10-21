# Target assessment: ktor HTTP range handling

Use this note when deciding whether to spend SnaKt work on ktor's `Range`
header handling, or when starting that work. It is a research and design note.
Nothing here is implemented.

Recommendation: do not select this target ahead of persistent collections or
Kotlin `ArrayDeque`. The range-merging code is small, already behaves correctly
under a randomized RFC differential test that kills every mutant tried, and
connecting a proof to the verbatim upstream bodies needs seven verifier
changes and fixture tooling. The deviations from RFC 9110 that matter to ktor users are in the
parser and the plugin's policy. A plain upstream issue with unit tests fixes them
without SnaKt. "When to revisit" says what would change this recommendation.

## Evidence labels

- (I) inspected: read in the pinned source or RFC text.
- (R) reproduced: observed by running the pinned code (see "Reproduction").
- (H) hypothesis: plausible, not checked.

## Pinned upstream

ktorio/ktor tag `3.6.0`, commit `111c5807d0a9004c8e0f17ae01e12a71aae56a29`.

| File | sha256 |
|:-----|:-------|
| `ktor-http/common/src/io/ktor/http/Ranges.kt` | `f335fed565102a11030f6f8efe80e66f3c0a58764acb21a0a5a8fc9cc348bf5e` |
| `ktor-http/common/src/io/ktor/http/RangesSpecifier.kt` | `2932218ad71da366e3af052fc065566710e138bd3d34a69b19d46e19583886d8` |
| `ktor-http/common/src/io/ktor/http/ContentRange.kt` | `3efece8a6aac13a522e20d446359e2a16602dca629bb9e6c35e37a6106d1a353` |

Helpers the bodies use: `io.ktor.util.chomp` (`ktor-utils/common/src/io/ktor/util/Text.kt`)
and `io.ktor.util.contains(LongRange, LongRange)` (`ktor-utils/common/src/io/ktor/util/Ranges.kt`).

## The code and its callers (I)

`Ranges.kt` holds the sealed class `ContentRange` with three data classes:
`Bounded(from, to)` for `first-pos "-" last-pos`, `TailFrom(from)` for
`first-pos "-"`, and `Suffix(lastCount)` for `"-" suffix-length`. Endpoints are
inclusive `Long`s.

- `parseRangesSpecifier(String): RangesSpecifier?` splits on `=` and `,`, trims SP
  and HTAB, parses each element with `String.toLong`, and returns the specifier
  only when `isValid()` holds. Any `Throwable` gives `null`.
- `RangesSpecifier.isValid()` checks the unit with a predicate (default: equal to
  `"bytes"`) and rejects `from < 0`, `to < from` and `lastCount < 0`.
- `List<ContentRange>.toLongRanges(contentLength)` resolves each spec against the
  length: `Bounded` to `from..min(to, len-1)`, `TailFrom` to `from until len`,
  `Suffix` to `max(len-n, 0) until len`, and drops empty results.
- `List<LongRange>.mergeRangesKeepOrder()` sorts by `first`, folds overlapping and
  adjacent ranges together, then orders the merged ranges by the index of the
  first input range each one contains.
- `RangesSpecifier.merge(length, maxRangeCount = 50)` returns the hull from
  `mergeToSingle` when more than `maxRangeCount` ranges were requested, and
  `toLongRanges(length).mergeRangesKeepOrder()` otherwise.
- `mergeToSingle(length)` returns `min first .. max last` of the resolved ranges,
  or `null` when none is satisfiable.

The only production caller chain is the server plugin
`ktor-server-plugins/ktor-server-partial-content`:

- `ApplicationRequest.ranges()` (`ktor-server-core`) calls
  `parseRangesSpecifier` on the `Range` header on every call; it is evaluated
  fresh in `onCall` and again in the `BodyTransformedHook` handler.
- `PartialContent` reads `contentLength` of a `ReadChannelContent` and skips
  content without one. `PartialContentConfig.maxRangeCount` defaults to 10 and
  must be positive.
- `PartialContentUtils.kt::processRange` does `require(length >= 0L)`, calls
  `merge(length, maxRangeCount)`, answers 416 with `Content-Range: bytes */len`
  when the result is empty, sends a single part for one range, a multipart body
  for several ascending ranges, and the `mergeToSingle` hull for several
  non-ascending ones.
- The consumers of each range are `PartialOutgoingContent.Single`
  (`contentLength = last - first + 1`), `calculateMultipleRangesBodyLength`, and
  `readFrom(range)`. The JVM file implementation, `File.readChannel(start,
  endInclusive)` in `ktor-utils/jvm/src/io/ktor/util/cio/FileChannels.kt`, does
  `require(start >= 0)` and `require(endInclusive <= fileLength - 1)`. The
  default `ReadChannelContent.readFrom(range)` discards `first` bytes and copies
  `last - first + 1`, so a range past the end gives fewer bytes than the
  `Content-Length` header announced.

Tests: `ktor-http/common/test/io/ktor/tests/http/RangesTest.kt` covers parsing,
resolution and merging on fixed examples; the plugin has end-to-end tests in
`ktor-server/ktor-server-tests/jvm/test/io/ktor/server/plugins/PartialContentTest.kt`.

## HTTP semantics (RFC 9110, I)

- §14.1.2: byte offsets start at zero and both ends of an int-range are
  inclusive. A last-pos at or past the length means "to the end". A suffix-range
  `-N` is the last N bytes, or the whole representation when it is shorter.
- §14.1.1: an int-range with last-pos below first-pos is invalid, and so is the
  whole specifier. A valid specifier is satisfiable when some range-spec is.
- §14.1.2: a bytes range-spec is satisfiable when it is an int-range with
  first-pos below the length, or a suffix-range with a non-zero length. "When a selected representation has zero length, the only
  satisfiable form of range-spec in a GET request is a suffix-range with a
  non-zero suffix-length."
- §14.1: range unit names are case-insensitive.
- §14.1.2: recipients "MUST anticipate potentially large decimal numerals and
  prevent parsing errors due to integer conversion overflows".
- §5.6.1.2: a recipient "MUST parse and ignore a reasonable number of empty list
  elements".
- §14.2: a server MAY ignore the `Range` field, and MAY ignore or reject a
  specifier with many overlapping or many small unordered ranges. It MAY ignore
  `Range` on a zero-length representation.
- §15.3.7.2: a server MAY coalesce ranges that overlap or whose gap is smaller
  than the multipart overhead (about 80 bytes). Parts SHOULD follow the order of
  the request, excluding unsatisfiable and coalesced ranges. Clients MUST read
  each part's `Content-Range`.
- §15.5.17: 416 means no requested range is satisfiable, or the request has an
  excessive number of small or overlapping ranges.

## Findings

Each finding states the pinned behaviour and the RFC text it departs from. None
is a security defect: F1 to F3 ignore `Range` (a full 200, which §14.2
permits), F4 answers 416 to a satisfiable request, and F5 sends a
self-describing superset.

| # | Input | ktor 3.6.0 | RFC 9110 | Class |
|:--|:------|:-----------|:---------|:------|
| F1 | `bytes=0-99999999999999999999` | `toLong` overflows, header ignored, 200 | §14.1.2 MUST prevent overflow parse errors; the range means "to the end" | R |
| F2 | `bytes=0-499,,1000-1499` | header ignored, 200; `RangesTest` asserts this | §5.6.1.2 MUST ignore empty elements | R |
| F3 | `Bytes=0-1` | header ignored, 200 | §14.1 units are case-insensitive | R |
| F4 | `bytes=-5` on a zero-length body | 416 `bytes */0` | satisfiable (§14.1.2); MAY ignore (§14.2), so 200 fits, 416 does not | R |
| F5 | `bytes=999-999,0-0`, length 1000 | single part `0-999` | §15.3.7.2 permits coalescing only overlaps and gaps below the multipart overhead | R |
| F6 | `bytes=+5-7` | accepted as `5-7` | grammar is `1*DIGIT`; lenient, harmless | R |
| F7 | `RangesSpecifier(Bytes, listOf(Bounded(-5, 3))).merge(10)` | `[-5..3]`; `merge` does not re-check validity | API contract, not wire behaviour; the plugin only sees parsed specifiers | R |

F5 is deliberate: the plugin comments it as merging "for non-seekable channel".
F1 to F4 are parser and plugin policy. None of them is in the code a
`merge` proof would cover.

The resolution and merge path itself agrees with RFC byte selection (R): 200,000
random valid specifiers with up to five ranges against lengths 0 to 11 give exact
coverage, in-bounds non-empty ranges, pairwise non-adjacent output, request
order, and the correct hull. Six single-line mutants of `Ranges.kt` (adjacent
merge, missing clamp, inclusive `until`, missing `max`, missing `break`,
inclusive suffix) each fail that test.

Evidence gaps: no search of ktor's YouTrack for range reports beyond the
changelog (which lists an inverted `maxRangeCount` check fixed in the plugin);
no check of other engines' `readFrom(range)` overrides; no measurement of how
often real clients send F1 to F4 inputs (H: F1 and F2 are rare, F4 shows up with
media players probing empty files).

## The theorem worth proving

Scope: the resolution and merge core, on the verbatim upstream bodies. Not the
parser, not the plugin, not HTTP security in general.

Assumptions, each established by a named caller:

- A1, validity: every element satisfies `isValid`'s per-range condition.
  `parseRangesSpecifier` returns a specifier only after `isValid()` holds with
  the default unit predicate.
- A2, length: `0 <= length`. `processRange` requires it.
- A3, unaliased list: `ranges` is not mutated during `merge`. The parser builds
  it with `map` and exposes it only as `List`. A caller that constructs
  `RangesSpecifier` from a `MutableList` it later mutates is outside the theorem.
- A4, `maxRangeCount > 0`. `PartialContentConfig` enforces it.

For `out = spec.merge(length, maxRangeCount)` under A1 to A4:

- T1 No exception and no `Long` overflow, for all `Long` values.
- T2 Bounds: every `r` in `out` has `0 <= r.first <= r.last <= length - 1`. This is
  exactly the `require` of `File.readChannel`, and makes
  `Single.contentLength` lie in `1..length`.
- T3 Canonical: for `i != j`, `out[i].last + 1 < out[j].first` or
  `out[j].last + 1 < out[i].first`. With T2 this gives `sum of sizes <= length`,
  which bounds the multipart `Content-Length`.
- T4 Coverage, when `ranges.size <= maxRangeCount`: a byte `b` is in some
  `out[i]` iff it is selected by some range-spec under §14.1.2.
- T5 Order, same case: `out` is ordered by the least index of a request range
  each part contains.
- T6 Count: `out.size <= min(ranges.size, maxRangeCount)`; when
  `ranges.size > maxRangeCount`, `out` is empty or the single hull.
- T7 Emptiness: `out` is empty iff no range-spec is satisfiable, except that a
  non-zero suffix on a zero-length body counts as unsatisfiable (F4).

For `mergeToSingle(length)`: `null` iff nothing is satisfiable, else the hull of
the selected bytes, with T2.

What the theorem does not say: anything about `If-Range`, HEAD, compression,
the multipart framing, a file whose length changes between `contentLength` and
the read, or parsing.

## What SnaKt needs

Read against SPECIFICATIONS.md and docs/soundness.md as of this note. The
fixture compiles `ContentRange`, `RangesSpecifier` and the two `Ranges.kt`
functions as sources, so their own bodies are verified; library calls need models.

| # | Change | Why | Size | Reuse |
|:--|:-------|:----|:-----|:------|
| V1 | External spec for `kotlin.ranges.LongRange`: `first`, `last` as ownership-free `@Pure` observers (the `Map.Entry` pattern in `external-specs/Iterable.kt`), `isEmpty`, `contains(Long)`; `Long.rangeTo`, `Long.until`, `coerceAtMost`, `coerceAtLeast`, `kotlin.math.max(Long, Long)` | every body | sol | any code using ranges |
| V2 | `filterNot` template, and completeness and order for an element-dependent `filter`/`filterNot` (the result is the subsequence of matching elements) | `toLongRanges`; T4 needs completeness, which SPECIFICATIONS.md, "Collection iteration", says is not modelled | opus design, sol implementation | all filter users |
| V3 | `sortedBy` with a pure selector: same size, sorted by key, every element `===` some source element and back | `mergeRangesKeepOrder` | opus design (permutation over references: `Multiset` is `Int`-only), sol implementation | collections, ArrayDeque |
| V4 | `ArrayList(capacity)` constructor as an empty owned `MutableList`; `fold` whose accumulator is an owned `MutableList` changed in the lambda | the merge fold | sol, if `fold` already carries owned accumulators; check first | general |
| V5 | `Array<T?>` model: `arrayOfNulls`, indexed get and set, `filterNotNull` | the keep-order step | opus design, two sol changes | ArrayDeque's `elementData` is the same shape |
| V6 | `for (x in list)` over an owned `List` and `for (i in indices)` with `break` | the keep-order step; SPECIFICATIONS.md lists iterator `for` loops as unsupported | opus design (loop over iteration observer), sol implementation | general |
| V7 | `minByOrNull`, `maxByOrNull` templates | `mergeToSingle` | sol | general |
| V8 | Fixture tooling: vendor the pinned files, add specification blocks and ownership annotations, and a check that removing them restores the upstream bytes (sha256 above) | ties the proof to upstream bodies | sol | every external-library target |

V8 is the only change specific to this target. V5 and V6 are shared with the
ArrayDeque target, so choosing ArrayDeque first makes this one cheaper.

Not needed for T1 to T7, needed for a parser proof (deeper option below):
`try` in a function with owned locals (rejected, SPECIFICATIONS.md, "Rejected
constructs"), models of `String.split`, `trim(vararg Char)`, `startsWith`,
`removePrefix`, `toLong`, and the inline `chomp` with a non-local `return`.

### Opus design decisions

- D1, attaching specifications to upstream bodies. Option (a): edit the vendored
  copy, adding `preconditions`/`postconditions` blocks and `@Unique`/`@Borrowed`
  type annotations, and check that stripping them restores the pinned file
  (V8). Option (b): a source-overlay feature binding specifications to source
  declarations from a separate file, like external specifications but for code
  SnaKt verifies. `@SpecOf` covers members and top-level external models only,
  so top-level source functions such as `toLongRanges` need (a) or (b).
  Recommend (a): no verifier change, and the strip check makes the claim
  inspectable. Revisit (b) once a second external-library target needs it.
- D2, ownership of `RangesSpecifier.ranges`. It is a shared `val` of type
  `List`, so calls on it are opaque library calls. Option (a): annotate the
  vendored property `@Unique` and the members `@Unique @Borrowed`, which states
  A3 in the types; the parser's `map` result is owned, so its constructor call
  still checks. Option (b): state the theorem only for the internal extension
  functions on an owned list and treat the two-line `merge` members as
  inspected. Recommend (a), which keeps `merge` and `mergeToSingle` in the proof.
- D3, coverage encoding. T4 quantifies over `Long` bytes with an `exists` over
  list indices on both sides. Option: a recursive `@Pure covers(list, n, b)` with
  `decreases(n)`. Expect trigger work; this is the main proof-engineering risk.
- D4, the permutation fact for V3. A two-way `===` membership is enough for T4
  and T5; a multiset of references is not needed.

## Stages

Each stage is one landing. Sol-sized stages carry a precise spec from the
preceding opus decision. Every stage adds fixtures that call the public entry
point being specified, and at least one fixture that must fail verification.

1. V8 tooling plus the vendored files with no specifications, all verification
   skipped. Test: the strip check passes, and fails after a one-character edit to
   a vendored body. (sol)
2. V1. Fixtures: `(a until b).isEmpty() == (a >= b)` verifies; a claim
   `(a until b).last == b` fails; `Long.MIN_VALUE` endpoints. (sol)
3. D3 and V2, then specify `toLongRanges` with T1, T2 and per-element coverage.
   Fixtures: the verbatim `toLongRanges` verifies; mutants "missing clamp" and
   "inclusive `until`" fail. (opus, then sol)
4. D4 and V3, V4: specify the sort-and-fold half of `mergeRangesKeepOrder` through a
   fixture that calls it, for T2, T3, T4. Mutants "adjacent" and "missing
   `max`" fail. (opus, then two sol)
5. V5, V6: the keep-order step, giving T5 and T6. Mutant "missing `break`"
   fails. (opus, then two sol)
6. D2, V7: `merge`, `mergeToSingle`, T7. A fixture function takes a
   `RangesSpecifier` meeting A1 and A2 and runs `processRange`'s branch
   selection (`merge`, then `isAscending` and the `mergeToSingle` hull) on it,
   asserting T2 for the range each branch serves. (sol)

Minimal useful slice: stages 1 to 3. It proves bounds and no overflow for the
resolution step on the verbatim body, and delivers V1, V2 and V8, which other
targets reuse. Full core: stages 1 to 6, roughly eight sol changes and four
opus decisions. Deeper connected option: verify `isValid` (with D2 (a)) and
`parseRangesSpecifier`, discharging A1 instead of assuming it. That adds the
parser prerequisites above, including a soundness change to `try`, and proves a
fact the parser already establishes by its last line.

## Definition of done

Executable checks, each run from the repository root:

- `./agent-scripts/test.sh --verify ktor_ranges` verifies every function
  carrying T1 to T7.
- `./agent-scripts/test.sh --vacuity ktor_ranges` lists nothing.
- The V8 strip check exits 0 against the sha256 values above.
- Each mutant fixture in stages 3 to 5 records a verification failure in its
  golden.
- The trust report for the fixture lists only V1 to V7 models and `bundled spec`
  entries already present.

Real-life value is a separate test, and this target is weak on it: the core
already passes the differential test, so T1 to T7 are unlikely to find a ktor
defect. Value counts when either a ktor change lands that this work caused, or a
later ktor release changes `Ranges.kt` and the pin-bump check flags a property
break that ktor's tests miss.

## Maintenance

- The three files change rarely: their history to 3.6.0 is mostly copyright and
  lint updates, plus one parser change. A pin bump is a sha256 update when the
  bodies are unchanged and a re-proof when they change.
- The stdlib models (V1, V3, V7) are trusted (docs/soundness.md, "External
  specifications"), and each is audited against the Kotlin version on the
  classpath.
- Risks: quantifier triggers for T4 (D3) make the proof slow or brittle; A3 is
  an assumption about ktor users that no Kotlin type enforces; the strip check
  proves the bodies match, not that the specifications describe what ktor's
  plugin relies on.

## Upstream contribution

Assume no maintainer interest in verification. ktor accepts small RFC 9110
conformance fixes to this file with unit tests (ktorio/ktor#5814, list
whitespace, is in 3.6.0). Plan:

1. Open one ktor YouTrack issue for F1 to F4 with the reproduction inputs and
   RFC sections. F2 reverses an existing test, so ask before sending a PR.
2. Offer separate small PRs: F1 (saturate an overflowing last-pos to "to the
   end"; treat an overflowing first-pos as unsatisfiable and an overflowing suffix-length as selecting the whole representation), F3 (case-insensitive unit), F4 (ignore `Range` on a zero-length
   body). Each PR carries plain unit tests in `RangesTest` and
   `PartialContentTest`, and mentions no tool.
3. Contribute the differential test only if a maintainer wants it.
4. Report the proof, if done, as an external note, not as a PR.

## When to revisit

Select this target if ArrayDeque is chosen first (V5, V6 become free), if a
second external-library target needs D1 (b), or if the program needs a small
first demonstration of a verbatim external body. Stages 1 to 3 suit that.

## Reproduction

Built against the pinned files with `kotlinc` and JDK 21. Copy `Ranges.kt`,
`RangesSpecifier.kt` and `ContentRange.kt` verbatim, add a file in package
`io.ktor.util` holding the verbatim `chomp` and `LongRange.contains(LongRange)`,
and compile with this `Main.kt`:

```kotlin
package repro

import io.ktor.http.*

// Verbatim from PartialContentUtils.kt.
internal fun List<LongRange>.isAscending(): Boolean =
    fold(true to 0L) { acc, e -> (acc.first && acc.second <= e.first) to e.first }.first

// processRange's branch selection, without I/O.
fun decide(header: String, length: Long, max: Int): String {
    val spec = parseRangesSpecifier(header) ?: return "200 (Range ignored)"
    val merged = spec.merge(length, max)
    if (merged.isEmpty()) return "416 bytes */$length"
    return when {
        merged.size != 1 && !merged.isAscending() -> "206 single ${spec.mergeToSingle(length)!!} (hull)"
        merged.size == 1 -> "206 single ${merged.single()}"
        else -> "206 multipart $merged"
    }
}

// RFC 9110 §14.1.2 byte selection for one valid range-spec.
fun rfcBytes(r: ContentRange, len: Long): Set<Long> = when (r) {
    is ContentRange.Bounded -> if (r.from >= len) emptySet() else (r.from..minOf(r.to, len - 1)).toSet()
    is ContentRange.TailFrom -> if (r.from >= len) emptySet() else (r.from until len).toSet()
    is ContentRange.Suffix -> if (r.lastCount == 0L) emptySet() else (maxOf(0L, len - r.lastCount) until len).toSet()
}

fun main() {
    val cases = listOf(
        "bytes=0-499,,1000-1499" to 2000L, "Bytes=0-1" to 10L,
        "bytes=0-99999999999999999999" to 10L, "bytes=+5-7" to 10L,
        "bytes=-5" to 0L, "bytes=-0" to 10L, "bytes=10-" to 10L,
        "bytes=999-999,0-0" to 1000L, "bytes=0-0,2-2" to 100L,
    )
    for ((h, len) in cases) println("$h len=$len -> ${decide(h, len, 10)}")
    println(RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(-5, 3))).merge(10))

    val rnd = java.util.Random(42)
    repeat(200_000) {
        val len = rnd.nextInt(12).toLong()
        val rs = List(1 + rnd.nextInt(5)) {
            when (rnd.nextInt(3)) {
                0 -> { val a = rnd.nextInt(14).toLong(); ContentRange.Bounded(a, a + rnd.nextInt(6)) }
                1 -> ContentRange.TailFrom(rnd.nextInt(14).toLong())
                else -> ContentRange.Suffix(rnd.nextInt(14).toLong())
            }
        }
        val spec = RangesSpecifier(RangeUnits.Bytes, rs)
        val out = spec.merge(len)
        val want = rs.flatMap { rfcBytes(it, len) }.toSet()
        val got = out.flatMap { it.toList() }
        check(out.all { 0 <= it.first && it.first <= it.last && it.last <= len - 1 }) { "$rs $len $out" }
        check(got.toSet() == want && got.size == got.toSet().size) { "$rs $len $out" }
        for (i in out.indices) for (j in out.indices) if (i != j)
            check(out[i].last + 1 < out[j].first || out[j].last + 1 < out[i].first) { "$rs $len $out" }
        val firstIdx = out.map { m ->
            rs.indexOfFirst { r -> rfcBytes(r, len).let { it.isNotEmpty() && it.all { b -> b in m } } }
        }
        check(firstIdx == firstIdx.sorted() && firstIdx.none { it < 0 }) { "$rs $len $out" }
        val single = spec.mergeToSingle(len)
        check((single == null) == want.isEmpty())
        if (single != null) check(single.first == want.min() && single.last == want.max()) { "$rs $len $single" }
    }
    println("differential: agrees with RFC byte selection")
}
```
