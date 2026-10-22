# ArrayDeque minimum-target verification

The functional port and consumer checks pass. The minimum target remains unmet:
item 3 lacks full-command certification for the complete consumer set, and item 8
lacks the required per-file verification timings in each verifying stage's land commit. The acceptance criteria belong to
[arraydeque-plan.md](arraydeque-plan.md#minimum-target).

## Source and correspondence

The upstream pin is Kotlin v2.3.0. The
[provenance file](../../formver.compiler-plugin/testData/upstream/kotlin-v2.3.0/PROVENANCE.md)
owns its tag commit, source hash, license and residual trust labels. Independent
byte comparisons with `git -C ~/dev/kotlin/master show v2.3.0:<path>` match the
vendored source, LICENSE.txt and NOTICE.txt. The external checkout is read-only.

Paths below are relative to `formver.compiler-plugin/`:

- Deviations D-1 through D-8:
  `testData/diagnostics/targets/arraydeque/ArrayDeque.port.diff`.
  This committed diff is the deviation list.
- Correspondence and guard-only mutant checks: `PortDeviationTest`, using its
  fixed specification-stripping rule and diagnostic-markup normalization.
- Public-contract projection: `ArrayDequeSpecEqualityTest`, comparing
  `plugin/resources/external-specs/ArrayDeque.kt` with `DequeSpec` and the port
  under the plan's [bundled-model projection](arraydeque-plan.md#bundled-model-shape).

## Coverage and trust boundary

The retained port covers `ArrayDeque()`, `ArrayDeque(Int)`, `isEmpty`, `get`, `set`,
`first`, `last`, `firstOrNull`, `lastOrNull`, `addFirst`, `addLast`, `add(e)`,
`removeFirst`, `removeLast`, their `OrNull` variants, `indexOf`, `lastIndexOf`,
`contains`, `clear`, and their private helpers. The capacity constructor uses the
parameter precondition of [G12](arraydeque-gaps-design.md#g12-preconditions-of-secondary-constructors-s5g).
Public-entry capacity clients cover zero and positive capacities and reject a
negative capacity. The bundled `(Int)` overload remains unbound; the binding
consumer explicitly rejects it. The bundled constructor claim covers `()` only.

The contracts cover representation bounds, access results, size changes,
pointwise element frames, end insertion/removal, search and clearing on normal
return, subject to the plan's [claim restrictions](arraydeque-plan.md#feasibility).
There is no exceptional-exit proof.

The port trust report is
`testData/diagnostics/targets/arraydeque/ArrayDeque.trust.txt`. Its distinct
registered dependencies are:

- Bundled spec: `kotlin.ranges.coerceAtLeast(this: Int, Int): Int`.
- `@TrustedElementEquality`: `ArrayDeque.indexOf(E): Int` and
  `ArrayDeque.lastIndexOf(E): Int`, plus their `Int` instantiations for `searchAndClear`.
- Wrapping-arithmetic functions: `ArrayDeque.registerModification(): Unit` and
  `newCapacity(Int, Int): Int`; interpretation belongs to the plan's
  [D3 decision](arraydeque-plan.md#d3-wrapping-arithmetic).

The scoped executed-equality assumption, its reachability restriction and the
`Int` consumer's equality interpretation belong to
[D4](arraydeque-plan.md#d4-and-v5-element-equality). No global equivalence-relation
assumption is made for arbitrary executable `equals`. The erased `internalGet`
cast rests on [Type invariants](../soundness.md#type-invariants). Distinct-array
copying follows [V2](arraydeque-plan.md#v2-copyinto-aliasing-and-permissions).

The proof is of a port. Mechanical correspondence guards drift; it does not prove
behavioural equivalence to the shipped class. Residual D-label and specification
erasure trust belongs to the provenance file. Bundled contracts remain external
specification assumptions, with their projection checked against the port.

## Measurements

[Command transcript](arraydeque-results.txt) records exact commands, combined
stdout/stderr and Bash wall times. Commands run from the repository root with
`SNAKT_VERIFICATION_CACHE=off`. No golden regeneration or timeout override is used.
The timing directory is empty at launch.

| Command | Wall seconds | Result |
|---|---:|---|
| `./agent-scripts/test.sh --verify --cold --timings /tmp/close-final-scratch/reports arraydeque` | 425.293 | 7 tests pass; cache off |
| `./agent-scripts/test.sh PortDeviationTest` | 18.308 | 12 tests pass |
| `./agent-scripts/test.sh ArrayDequeSpecEqualityTest` | 16.908 | 2 tests pass |
| `./agent-scripts/test.sh --vacuity --cold arraydeque` | 450.635 | 7 tests pass; no contradictory context |
| `./agent-scripts/check-gradle-consumer.sh` | 739.830 | Consumer build and trust assertions pass |

JUnit per-file wall durations from the cold `--verify` run exclude driver overhead
and overlap because tests run concurrently. Paths are under
`formver.compiler-plugin/testData/diagnostics/`:

| File | Seconds |
|---|---:|
| `targets/arraydeque/ArrayDeque.kt` | 321.581 |
| `targets/arraydeque/indexOfPre80390.kt` | 296.776 |
| `targets/arraydeque/lastIndexOfPre80661.kt` | 109.653 |
| `external_specs/arraydeque_binding.kt` | 9.844 |
| `external_specs/arraydeque_bfs.kt` | 196.934 |
| `external_specs/arraydeque_bfs_negative.kt` | 109.652 |

The search mutants have exactly their expected failures: two `result < size`
diagnostics for `indexOfPre80390`, and the contains-absence equivalence diagnostic
for `lastIndexOfPre80661`. Guard-only comparisons and retained storage-assignment
pins pass. The upstream-shaped searches have no diagnostics.

The scoped vacuity command covers the port, binding, BFS and negative fixtures.
It prints no contradictory context. An empty probe directory need not exist;
the command's reported outcome is the evidence. The full unfiltered vacuity
command has no fresh execution here; its independent failure and scoped relevance
are recorded in [the execution audit](arraydeque-final.md#full-vacuity-gap).
The land gate owns the suite and `check-all.sh` for this documentation-only stage.

## Cold per-declaration measurements

[All 87 attempt rows](arraydeque-declaration-times.tsv) retain names, offsets,
outcomes, elapsed seconds, effective timeouts and cache status. The
[report format](../agents-dev.md#declaration-timings) defines the measured interval.
Complete files retain their proof contexts; declarations are not extracted.

| File | Verified / expected failure | Slowest declaration | Seconds |
|---|---:|---|---:|
| `ArrayDeque.kt` | 47 / 11 | `ArrayDeque.lastIndexOf` | 62.039 |
| `indexOfPre80390.kt` | 7 / 1 | `SearchDeque.indexOfPre80390` | 71.216 |
| `lastIndexOfPre80661.kt` | 7 / 1 | `SearchDeque.lastIndexOfPre80661` | 50.744 |
| `arraydeque_binding.kt` | 2 / 5 | `enqueueOnce` | 3.310 |
| `arraydeque_bfs.kt` | 3 / 0 | `scanSuccessors` | 86.782 |
| `arraydeque_bfs_negative.kt` | 0 / 3 | `scanSkipLast` | 99.828 |

All rows use an effective timeout of 300 seconds and cache `off`. There are 66
verified and 21 expected-failure outcomes, with no timeout, invalid or error
outcomes. Every selected declaration is below 150 seconds in this run. The
slowest port declaration has 87.961 seconds to that threshold and 237.961 seconds
to its timeout. This establishes measured headroom for the complete retained
port, including the capacity constructor, and the consumers in this run. It is
not a guarantee about future solver runs or absent members.

## Acceptance audit

| Minimum item | Measured status |
|---|---|
| 1: M coverage and clean in-scope diagnostics | Pass: both constructors and all retained M members verify; only intended negative clients carry failures. |
| 2: mutants and upstream searches | Pass: intended clauses fail in guard-only mutants; upstream-shaped bodies verify. |
| 3: vacuity | Scoped pass; full-command certification unmet: the unfiltered run fails in the counterexamples harness and does not include the BFS fixtures. No fresh unfiltered run establishes the complete consumer set. |
| 4: labelled deviation check | Pass: all 12 correspondence tests pass. |
| 5: bundled-model contract projection | Pass: both projection tests pass. |
| 6: BFS consumers and bundled binding | Pass: testData and published-plugin BFS verify; skipped-successor closure and unconditional enqueue-growth negatives fail as intended. |
| 7: provenance | Pass: tag commit, source hash, license/notice bytes and residual D-labels match. |
| 8: land-message verification timings | Unmet: the BFS proof land commit and capacity-constructor restoration land commit contain no numeric per-file `--verify` wall times. Fresh audit timings cannot satisfy a requirement on those land commits. |

Inspect item 8 with `git log --format='%h %s%n%b'`, selecting subjects
`Verify the CSR breadth-first search through the bundled ArrayDeque model` and
`Restore the restricted ArrayDeque capacity constructor in the port`. Stage
artifacts contain execution timings, but those are not the required land commits.
No complete certification of every other verifying stage is asserted.

A source scan finds no `VERIFICATION_SKIPPED`, `UNSUPPORTED_FEATURE`,
`INTERNAL_ERROR`, `UNTRACKED_WRITE` or verification exclusions in the port or
positive BFS. In `arraydeque_binding.kt`, only `unboundCapacity` and
`unboundMember` are explicitly skipped API-rejection negatives.

The BFS proves the rank-decreasing parent-edge witness and successor closure
specified by [the consumer design](arraydeque-bfs-design.md), under its owned-array,
CSR-index and vertex-bound preconditions. The discovery step uses a runtime
`check(f < rank.size)`; the claim is on normal return and does not prove that this
check cannot throw. The published
consumer compiles the same markup-stripped positive source, rejects `scanSkipLast`
on its closure return, and requires bundled `removeFirst` and `isEmpty` trust
entries. `enqueueOnce` and its false-growth sibling also pass their golden checks.

## Patch status and remaining gaps

Patch status: prepared, NOT submitted. The
[patch report](../upstream/arraydeque-removeall/REPORT.md) owns its guarantee
analysis and executable layout tests. No issue or PR is filed, and there is no
claim of deployment or upstream acceptance. `addAll(index, this)` is unspecified
aliasing and is not classified as a defect.

Remaining gaps are items 3 and 8, the unbound bundled
capacity overload, port-to-shipped-class residual trust, and the out-of-scope
members and exceptional exits in the [plan](arraydeque-plan.md#out-of-scope).
The [F3 decision](arraydeque-plan.md#decision-f3-is-not-justified) remains separate.
The briefing's advisory memo has no supplied path and is absent from the inspected
repository and workflow artifacts; no recommendation from it is treated as a decision.
