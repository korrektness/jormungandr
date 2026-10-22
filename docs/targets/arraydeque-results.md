# ArrayDeque minimum-target verification

The minimum target is not met. The source port omits `ArrayDeque(Int)`, and the
consumers exercise `enqueueOnce` rather than the required compressed-sparse-row
breadth-first search. The acceptance criteria remain in
[arraydeque-plan.md](arraydeque-plan.md#minimum-target).

## Source and correspondence

The provenance file listed below owns the upstream version, commit, source hash,
license and residual trust labels. Byte comparisons against its upstream tag
match for the vendored source and the copied license and notice files.

Paths below are relative to `formver.compiler-plugin/`:

- Provenance and residual trust:
  `testData/upstream/kotlin-v2.3.0/PROVENANCE.md`.
- Deviations D-1 through D-8:
  `testData/diagnostics/targets/arraydeque/ArrayDeque.port.diff`.
  This committed diff is the deviation list; its bodies are not reproduced here.
- Correspondence and guard-only mutant checks: `PortDeviationTest`, using its
  fixed specification-stripping rule and diagnostic-markup normalization.
- Public-contract projection: `ArrayDequeSpecEqualityTest`, comparing
  `plugin/resources/external-specs/ArrayDeque.kt` with `DequeSpec` and the port
  under the plan's [bundled-model projection](arraydeque-plan.md#bundled-model-shape).

## Coverage and trust boundary

The retained port covers `ArrayDeque()`, `isEmpty`, `get`, `set`, `first`, `last`,
`firstOrNull`, `lastOrNull`, `addFirst`, `addLast`, `add(e)`, `removeFirst`,
`removeLast`, their `OrNull` variants, `indexOf`, `lastIndexOf`, `contains`,
`clear`, and their private helpers. `ArrayDeque(Int)` is absent under the
[G12 fallback](arraydeque-gaps-design.md#g12-preconditions-of-secondary-constructors-s5g).
Deliverable scope is defined in [the target note](kotlin-arraydeque.md#deliverables).

The proved contracts cover the representation bounds, access results, size
changes, pointwise element frames, end insertion/removal, search results and
clearing on normal return, subject to the claim restrictions in the plan's
[Feasibility section](arraydeque-plan.md#feasibility).

The port trust report is
`testData/diagnostics/targets/arraydeque/ArrayDeque.trust.txt`. Its distinct
registered dependencies are:

- Bundled spec: `kotlin.ranges.coerceAtLeast(this: Int, Int): Int`.
- `@TrustedElementEquality`: `ArrayDeque.indexOf(E): Int` and
  `ArrayDeque.lastIndexOf(E): Int`, plus `ArrayDeque.indexOf(Int): Int` and
  `ArrayDeque.lastIndexOf(Int): Int` for `searchAndClear`.
- Wrapping-arithmetic functions: `ArrayDeque.registerModification(): Unit` and
  `newCapacity(Int, Int): Int`; their interpretation is owned by the plan's
  [D3 decision](arraydeque-plan.md#d3-wrapping-arithmetic).

For what the equality annotation assumes about executed equality and deque
reachability, and the `Int` consumer's residual equality trust, see the plan's
[D4 decision](arraydeque-plan.md#d4-and-v5-element-equality). The erased cast in
`internalGet` rests on [Type invariants](../soundness.md#type-invariants).

For proof scope and residual trust, see the provenance file above and [the target note](kotlin-arraydeque.md). For distinct-array copying, see the plan's [V2 decision](arraydeque-plan.md#v2-copyinto-aliasing-and-permissions). There is no claim of deployment or upstream acceptance. Nothing is filed upstream.

## Measurements

[Command transcript](arraydeque-results.txt) contains the exact test commands,
combined stdout/stderr and Bash wall times. All five commands exit 0:

| Command | Wall seconds | Output |
|---|---:|---|
| `./agent-scripts/test.sh --verify arraydeque` | 322.841 | 5 tests, 5 passed, 0 failed |
| `./agent-scripts/test.sh PortDeviationTest` | 11.961 | 12 tests, 12 passed, 0 failed |
| `./agent-scripts/test.sh ArrayDequeSpecEqualityTest` | 9.221 | 2 tests, 2 passed, 0 failed |
| `./agent-scripts/test.sh --vacuity arraydeque` | 425.279 | 5 tests passed; no contradictory context |
| `./agent-scripts/check-gradle-consumer.sh` | 260.328 | consumer build passed |

JUnit reports these per-file wall durations within the `--verify` run; they
exclude driver overhead and overlap because tests run concurrently:

| File under `testData/diagnostics/` | Seconds |
|---|---:|
| `targets/arraydeque/ArrayDeque.kt` | 306.552 |
| `targets/arraydeque/indexOfPre80390.kt` | 149.955 |
| `targets/arraydeque/lastIndexOfPre80661.kt` | 77.585 |
| `external_specs/arraydeque_binding.kt` | 9.493 |

No timeout override is used in the recorded runs.
The diagnostics harness discards declaration progress messages. Whole-file
measurements do not establish that every declaration is well under the default limit;
that timing criterion is unmeasured.

The mutant test outcomes match their expected failures. `indexOfPre80390` has
two diagnostics, both on `result < size`. `lastIndexOfPre80661` has one, on
`(result == -1) == !contains(element)`, expressed through its existential
specification expansion. `PortDeviationTest` checks these exact failing clauses
and confirms that each mutant search function differs from the port only in the
wrapped-branch guard. The upstream-shaped port searches have no diagnostics.
The real-port correspondence test passes against the committed diff, including
its D-label requirement and retained storage-assignment pins.

The scoped vacuity run includes both the port and the binding consumer; its
probe-report directory has no TSV entries. The unfiltered
`./agent-scripts/test.sh --vacuity` is not run in this stage. The managed land
workflow assigns the full suite, including `./agent-scripts/check-all.sh`, to
the gate; there is no fresh `check-all.sh` result here.

The published-consumer script passes its trust-report assertions for
`bundled spec: kotlin.collections.ArrayDeque.ArrayDeque(): ArrayDeque<E>` and
`bundled spec: kotlin.collections.ArrayDeque.addLast(E): Unit`. The script's
successful stdout does not print the report itself. Its consumer constructs a
queue and runs conditional insertion; it does not implement BFS.

## Acceptance audit

| Minimum item | Measured status |
|---|---|
| 1: M coverage and clean in-scope diagnostics | Partial: the retained members verify; the capacity constructor is absent. |
| 2: mutants and upstream searches | Pass, including intended clauses and guard-only comparison. |
| 3: vacuity | Clean for both relevant files in the scoped run; unfiltered run unmeasured. |
| 4: labelled deviation check | Pass. |
| 5: bundled-model contract projection | Pass. |
| 6: BFS consumers and bundled binding | Not met: the bundled binding works for conditional insertion; both BFS consumers and the skipped-successor negative are absent. |
| 7: provenance | Source hash, tag commit, license/notice bytes and residual D-labels match. |
| 8: land-message verification timings | Not certified: the audit does not establish per-file `--verify` wall times for every verifying stage. |

A source scan for `VERIFICATION_SKIPPED`, `UNSUPPORTED_FEATURE`, `INTERNAL_ERROR`,
`UNTRACKED_WRITE`, and exclusion directives finds none in the port. In
`external_specs/arraydeque_binding.kt`, only `unboundCapacity` and `unboundMember`
carry skipped/unsupported markers: they are explicit out-of-scope API rejection
negatives. `enqueueOnce` has none. Neither file has an exclusion directive.
The absent capacity constructor remains the item-1 coverage gap; this scan does
not turn it into a proved declaration.

Timing-report audit command: `git log --format='%s%n%b'`.
