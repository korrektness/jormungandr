# ArrayDeque final execution audit

This report records independent command executions. For the delivered proof
coverage, open minimum-target criteria, trusted assumptions and residual trust,
use the [acceptance audit](arraydeque-results.md#acceptance-audit),
[coverage and trust boundary](arraydeque-results.md#coverage-and-trust-boundary),
and [source provenance](../../formver.compiler-plugin/testData/upstream/kotlin-v2.3.0/PROVENANCE.md).
Minimum-target completion is in progress through the workflow's `close-*` stages,
which own refreshing this report with their acceptance evidence.

## Commands and outcomes

Run from the repository root. Seconds are Bash wall time including startup.
No golden regeneration or timeout override is used.

| Command | Exit | Seconds | Observed outcome |
| --- | ---: | ---: | --- |
| `./agent-scripts/test.sh --verify arraydeque` | 0 | 330.323 | 5 tests pass, including expected-failure mutants |
| `./agent-scripts/test.sh --verify --cold --timings "$REPORTS" arraydeque` | 0 | 468.515 | 5 tests pass; 79 declaration attempts, cache off |
| `./agent-scripts/test.sh PortDeviationTest` | 0 | 24.214 | 12 tests pass |
| `./agent-scripts/test.sh ArrayDequeSpecEqualityTest` | 0 | 27.953 | 2 tests pass |
| `./agent-scripts/test.sh --vacuity arraydeque` | 0 | 515.067 | 5 tests pass; no contradictory context |
| `./agent-scripts/check-gradle-consumer.sh` | 0 | 340.109 | Consumer build and trust assertions pass |
| `./agent-scripts/test.sh --vacuity` | 1 | 1446.153 | 487 tests run, 486 pass; `testCounterexamples` throws an internal assertion |
| `./agent-scripts/check-all.sh` | 0 | 2100.659 | All four checks pass |
| `./agent-scripts/test.sh --vacuity verification/counterexamples.kt` | 1 | 31.460 | Isolated reproduction of the full-vacuity failure |

These runs supply execution evidence for minimum criteria 1–6. Their acceptance
interpretation belongs to the linked audit. For criterion 7, independent byte
comparisons of the vendored source, license and notice against the provenance
pin all match. Compare `git show <provenance-tag>:<upstream-path>` with the
corresponding vendored file; paths and hashes belong to the provenance document.
For criterion 8 and the landed implementation commits, inspect
`git log --format='%h %s%n%b'`; stage-timing certification belongs to the
acceptance audit.

The full-vacuity failure is `check(hasModel(it))` in
`formver.compiler-plugin/test-fixtures/org/jetbrains/kotlin/formver/plugin/services/VerificationFacade.kt::TestVerifier.verify`.
The assertion requires a counterexample on a verifier failure. The full command
prints 18 contradictory-context entries in other fixtures and none for
ArrayDeque or its binding consumer. The scoped command independently passes.
The orchestrator authorizes this documentation-only landing with the
failure outside ArrayDeque explicit; harness repair is separate.

Per-file JUnit durations from the `--verify` XML, excluding driver overhead:

| File under `formver.compiler-plugin/testData/diagnostics/` | Seconds |
| --- | ---: |
| `targets/arraydeque/ArrayDeque.kt` | 239.922 |
| `targets/arraydeque/indexOfPre80390.kt` | 294.931 |
| `targets/arraydeque/lastIndexOfPre80661.kt` | 94.671 |
| `external_specs/arraydeque_binding.kt` | 12.374 |

File times overlap because tests run concurrently. The independent cold run uses
`REPORTS=/tmp/arraydeque-final-1792665360/declaration-reports`. All 79 rows report
cache `off` and an effective timeout of 300 seconds, with only verified or
expected-failure outcomes. Its slowest retained-port declaration is
`ArrayDeque.lastIndexOf` at 80.730 seconds; the slowest overall attempt is
`SearchDeque.lastIndexOfPre80661` at 86.845 seconds. Declaration-level evidence
and its retained-port boundary are in
[cold per-declaration measurements](arraydeque-results.md#cold-per-declaration-measurements).
Those measurements establish headroom for the retained port; they provide no
evidence for absent members.

## Independent patch checks

Patch status, submission authorization, guarantee analysis and test scope have
one home in [the patch report](../upstream/arraydeque-removeall/REPORT.md).
The two source selections below are the report's pinned release and recorded
master selection. Fresh scratch copies accept `git apply --check` and the patch.
For each patched scratch checkout, run:

```sh
bash docs/upstream/arraydeque-removeall/run-tests.sh \
  "$SCRATCH_CHECKOUT" "$SCRATCH_OUTPUT" "$JUNIT4" "$HAMCREST"
```

| Source selection | Exit | Seconds including compilation | Patched / unpatched |
| --- | ---: | ---: | --- |
| Pinned release | 0 | 19.639 | 34 pass / 34 run, only `bulkFilterContainsThrows` fails |
| Recorded master | 0 | 13.075 | 34 pass / 34 run, only `bulkFilterContainsThrows` fails |

Both negative siblings observe expected `[-1]`, actual `[null]`.

## Scope decisions and evidence location

For what is not implemented and why, use the
[acceptance audit](arraydeque-results.md#acceptance-audit) and
[exceptional-exit decision](arraydeque-plan.md#decision-f3-is-not-justified).
Operator authorization for any public submission is handled by the patch report;
this execution audit performs no public action.

Raw command output, XML and scratch patch checkouts are in
`/tmp/arraydeque-final-1792665360/`. The commands and outcomes above remain
inspectable without retaining that directory.
