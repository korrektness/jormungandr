# kotlinx-datetime: verified clamp coverage, unproved saturation

This partial-evidence pilot verifies `Long.clampToInt` over its full domain.
Universal `safeMultiplyOrClamp` saturation is unproved: the root and its bounded
generic guidance helper each reach the 300-second limit. Neither timeout is an
acceptance result. Both frozen executable algorithms and their domains remain
intact; there is no additional semantic feature, trusted lemma or cap increase.

## Source and adaptation

[Source pin, license and fetch disclosure](../../formver.compiler-plugin/testData/upstream/kotlinx-datetime-d79ac8a/PROVENANCE.md).
All source inspection uses the frozen artifacts described there.

Fixture paths below are relative to `formver.compiler-plugin/testData/`:

- `programs/ports/datetime/LongClamp.kt`: unchanged clamp expression in a
  block-body contract shell, independent threshold predicate, nine boundary
  clients, and an intentional wrong-positive-endpoint implementation.
- `experiments/datetime/LongSaturation.kt`: both frozen bodies and complete
  saturation contracts; multiplication uses the declaration-local wrapping opt-in.
- `experiments/datetime/ProductDivisionGuidance.kt`: body-bearing generic helper
  attempting quotient/remainder reconstruction, remainder sign/magnitude bounds
  and equivalence between quotient recovery and independent thresholds on the
  nonsingular division path. Its unproved postcondition is not assumed by a root.

The intended multiplication equation is mathematical signed-Long saturation of
`a*b`. Upstream's KDoc states only multiplication by nonzero `b`; the complete
saturation equation is a pilot specification. `productFitsLong` defines the
independent threshold formula. Its verified body establishes checked division
safety, not machine-checked equivalence to unbounded representability.

Adaptation: two root contracts, one block shell, one wrapping annotation and one
threshold-predicate definition (copied into independently compiled experiments).
There is no executable rewrite or overflow-excluding restriction. Multiply
excludes only `b == 0`; clamp excludes nothing. Full endpoints remain supported.

`DatetimeArithmeticTest` pins the snapshot SHA-256, selected declarations,
helper inventory/body and labelled shell-only deviations through
`PortDeviationChecker`. It rejects executable, extra-declaration and helper
mutations. The active clamp body/predicate must match the archived port.

## Original public callers inspected

Paths are relative to the pinned upstream repository:

- `core/common/src/{LocalDateRange,YearMonthRange}.kt`: progression `step(value:
  Long, unit)` multiplies the magnitude by day/month counts before delegating to
  `LongProgression.step`. KDoc requires positive magnitude and preserves direction.
  Internal `fromClosedRange` also uses multiplication.
- `core/common/src/DateTimeUnit.kt`: `DayBased` and `MonthBased` require positive
  Int counts. These callers have positive nonzero multipliers; the root's pilot
  contract still covers every nonzero Long multiplier.
- `core/common/src/Instant.kt`: `daysUntil`, `monthsUntil`, `yearsUntil` clamp
  `until` results; KDoc explicitly specifies signed Int saturation.
- `core/jvm/src/LocalDate.kt`: `daysUntil` and `monthsUntil` clamp Java temporal
  differences. `math.kt::LongProgression.sizeUnsafe` also clamps computed sizes.

Caller source inspection does not verify calendar/progression dependency closures,
exceptional behavior or platform implementations.

## Reproduction and actual results

```
./agent-scripts/test.sh --verify --cold --timings /tmp/datetime-clamp LongClamp
./agent-scripts/test.sh --vacuity --cold LongClamp
./agent-scripts/test.sh --verify DatetimeArithmeticTest
./agent-scripts/test.sh --verify PortDeviationTest
```

All four focused commands pass. Cold declaration measurements from
`/tmp/jor-wf3-3-scratch/clamp-verification-timings/`:

| Declaration | Outcome | Seconds | Cache |
|---|---|---:|---|
| productFitsLong | verified | 2.056 | off |
| clampToInt | verified | 1.827 | off |
| clampBoundaryClients | verified | 1.868 | off |
| wrongPositiveClamp | expected postcondition failure | 1.868 | off |

Aggregate declaration time: 7.619 seconds. Clients cover MIN64, MAX64, MIN32-1,
MIN32, MIN32+1, zero, MAX32-1, MAX32 and MAX32+1. Dedicated vacuity reports no
contradictory context; the clients also contain `refute(false)`. The trust golden
reports three verified declarations and no trusted assumptions.

The two runtime/correspondence tests pass. The runtime test compiles the selected
snapshot and specification-stripped port with the test classpath's Kotlin/JVM
compiler. Both implementations agree with an independent BigInteger saturation
oracle on 932 legal pairs: a 21-value endpoint/threshold cross-product excluding
zero multipliers, plus 512 fixed-seed samples. The same inputs check Int clamping
and the threshold predicate. This is runtime evidence, separate from verification.
It covers both multiplier signs, zero multiplicands, endpoint products, MIN/-1,
MIN/MIN, and neighbors of division-by-two thresholds; it is not exhaustive.

Failed experiments are outside registered acceptance fixtures. To reproduce in
a disposable worktree, copy the desired archived `.kt` to
`testData/programs/ports/datetime/`, then run
`./agent-scripts/test.sh --update-goldens --cold <stem>`. Outcome recording is
unnecessary: the runner refuses to write goldens for the timeout. Keep the
300-second limit. No further multiplication run belongs to this pilot.

Root evidence: a 300.391-second Silicon timeout and two cached dependency
programs. Generic-helper evidence: a 300.484-second timeout, cache off. The
unresolved bridge connects modular product/quotient recovery with independent
thresholds. A group timeout cannot identify a single failing assertion. No root
run assumes the unproved helper.

Raw logs: `/tmp/jor-wf3-3-scratch/{initial,guidance-final,clamp-verification,clamp-vacuity,correspondence-runtime-final,port-checker}.log`.
The helper's timing TSV is in `guidance-timings/`. Regeneration timing output
can differ from the requested `--timings` directory; direct `--verify` output
matches its requested directory. Trust rests on Kotlin-to-Viper conversion,
exact finite-width semantics, Silicon/Z3, modular call verification and the
local Kotlin/JVM runtime.

## Four conclusions

1. Proof capability: full-domain Int clamping verifies; a meaningful endpoint
   mutant fails its contract. Universal multiplication saturation is unproved.
2. Concrete defects: none established. No legal-input original-library defect
   witness exists; the incorrect clamp is an intentional control. Sampling
   agreement and solver timeouts establish no library defect or universal theorem.
3. Demo readiness: a reproducible narrow clamp proof/control and arithmetic
   evaluation is available. This is not a flagship saturation demo or evidence
   of broad-library feasibility; public calendar/progression APIs are unverified.
4. Publication evidence: source-correspondence guards, independent runtime checks,
   cold narrow proof/control, trust/vacuity evidence and two measured proof
   barriers. This supports a bounded tool/case-study account, not full-library
   verification or a publication-novelty claim.

Effort accounting: 412 repository lines (all fixture text counted,
including copied root bodies), plus 35 workflow-artifact lines and
one changed checker line removed: 448 conservatively charged lines.
The full upstream snapshot/license and generated goldens/test registration are
excluded. This remains below the independent 600 handwritten-line pilot cap.
