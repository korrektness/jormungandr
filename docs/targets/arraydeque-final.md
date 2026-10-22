# ArrayDeque final execution audit

The cold scoped checks pass for the complete retained port, both search mutants,
the bundled binding and CSR BFS consumers. Minimum-target completion remains
unmet for the exact reasons in [acceptance items 3 and 8](arraydeque-results.md#acceptance-audit).
The [acceptance audit](arraydeque-results.md) owns command timings, per-file
measurements, declaration headroom, coverage, assumptions, residual trust and
remaining gaps. Its [transcript](arraydeque-results.txt) records the command output.

## Commands and outcomes

All five commands in the acceptance audit exit 0 with verification caching disabled.
The cold verification and scoped vacuity commands each pass seven tests. The
correspondence check passes twelve tests; contract projection passes two. The
published-plugin consumer verifies the same markup-stripped BFS source and rejects
its skipped-successor negative. These runs use no timeout override or golden
regeneration. All 87 declaration attempts have cache `off` and meet the measured
headroom criterion.

Byte comparisons against the provenance pin match the vendored source and copied
license and notice. Source provenance and the limits of the correspondence check
belong to [PROVENANCE.md](../../formver.compiler-plugin/testData/upstream/kotlin-v2.3.0/PROVENANCE.md).

## Full-vacuity gap

The unfiltered `./agent-scripts/test.sh --vacuity` evidence is an independent run
with exit 1 after 1446.153 seconds: 487 tests run, 486 pass, and
`testCounterexamples` throws `check(hasModel(it))` in
`formver.compiler-plugin/test-fixtures/org/jetbrains/kotlin/formver/plugin/services/VerificationFacade.kt::TestVerifier.verify`.
The isolated command `./agent-scripts/test.sh --vacuity verification/counterexamples.kt`
exits 1 in 31.460 seconds. The assertion requires a counterexample on a verifier
failure. The full output lists 18 contradictory contexts in other fixtures and
none for ArrayDeque or its binding consumer.

These full-command observations do not cover the BFS fixtures. The fresh scoped
cold vacuity run covers them and the port and reports no contradictory context.
There is no fresh unfiltered execution or claim that the full harness passes.
Harness repair is outside this documentation-only stage. The land gate owns the
suite and `check-all.sh`.

## Independent patch checks

Patch status and remaining gaps belong to the
[acceptance audit](arraydeque-results.md#patch-status-and-remaining-gaps).
Guarantee analysis and test scope belong to
[the patch report](../upstream/arraydeque-removeall/REPORT.md).
Its pinned release and recorded master selections accept `git apply --check` and
the patch in scratch copies. The independent test evidence uses:

```sh
bash docs/upstream/arraydeque-removeall/run-tests.sh \
  "$SCRATCH_CHECKOUT" "$SCRATCH_OUTPUT" "$JUNIT4" "$HAMCREST"
```

| Source selection | Exit | Seconds including compilation | Patched / unpatched |
| --- | ---: | ---: | --- |
| Pinned release | 0 | 19.639 | 34 pass / 34 run, only `bulkFilterContainsThrows` fails |
| Recorded master | 0 | 13.075 | 34 pass / 34 run, only `bulkFilterContainsThrows` fails |

Both negative siblings observe expected `[-1]`, actual `[null]`. These patch
measurements are separate from the fresh scoped verifier audit. There is no
public submission, deployment or upstream-acceptance claim.

## Evidence storage

Fresh command output, JUnit XML and declaration reports are in
`/tmp/close-final-scratch/`. The committed transcript and declaration TSV retain
the fresh measurements without that scratch directory. Independent full-vacuity
and patch output is in `/tmp/arraydeque-final-1792665360/`; its commands and
outcomes above remain inspectable without retaining the directory.
