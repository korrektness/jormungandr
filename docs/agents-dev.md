# The agent scripts

Depth behind AGENTS.md, which has the commands themselves.

`agent-scripts/` wraps the Gradle test tasks so the loop does not have to be
rediscovered from the build files each time. Nothing in the build depends on
them.

- `test.sh` — the test driver. Conversion only by default (`untilConversion`
  plus the locality tests, which have no verification stage), `--verify` for the
  full pipeline, `--verify-changed` for `./gradlew update` (convert everything,
  verify only where conversion output changed), `--update-goldens` to regenerate
  goldens and report what they now say, `--vacuity` for the vacuity check of
  `docs/soundness.md`.
- `check-all.sh` — `check`, the Gradle consumer check, `pre-commit` and the testData checks together.
  `--rerun` re-executes tests Gradle considers current.
- `check-gradle-consumer.sh` — publishes the plugin to a private Maven repository and
  compiles a tiny Gradle project with it, so a defect that only the shadow jar has
  (an unrelocated dependency) fails. The project also verifies calls through an
  external spec and the bundled specs, and fails if a spec reaches the compiled
  output, if a contradictory spec is accepted, if a default build does not show
  the trust summary, if a spec binding to a declaration that another module
  verified is not reported, or if a caller stays verified after its spec is
  edited. A multiplatform project builds the
  bundled specs under explicit API mode and warnings as errors. Needs network access and Z3; exits 2 without Z3.
- `check-testdata.sh` — golden files with no source, and empty golden files.

`test.sh` and `check-all.sh` take `--help`, and need `python3` on PATH because
the test results they report from are XML; `check-testdata.sh` needs nothing but
a checkout.

## Verification cache

Verification modes in `test.sh` use the shared success cache. Conversion-only
runs do not use it. See the outcome cache note for
[store selection](targets/viper-outcome-cache.md#store),
[cold-run controls](targets/viper-outcome-cache.md#forcing-a-cold-run),
[reporting](targets/viper-outcome-cache.md#reporting) and
[key inputs](targets/viper-outcome-cache.md#key). Goldens are checked on hits too.

## Declaration timings

`./agent-scripts/test.sh --verify --cold --timings /absolute/report/directory <pattern>`
records verification attempts outside goldens. Use an empty directory for each run.
Each attempt writes a separate TSV with source file, declaration label, source
start offset, outcome, elapsed seconds, effective timeout seconds and cache status.
The offset distinguishes overloads. Outcomes are `verified`, `failed`, `timeout`,
`invalid` or `error`; a failed negative fixture still passes its golden test.
Cache status is `off`, `miss`, `hit` or `unavailable`, listed in program order.

The elapsed interval encloses the same declaration group as `VerificationProgress`:
its main program and any selected lambda programs, including frontend work and
cache lookup. Queue wait and verifier construction are outside the interval.
Groups retain their full proof context. Counterexample fixtures include their
ordinary-mode checks in elapsed time; cache statuses describe the main passes.
A group's timeout applies separately to each program. Only declarations selected for verification produce rows;
conversion-only and skipped declarations produce none. The environment variable
`SNAKT_VERIFICATION_TIMINGS` also enables reporting directly from Gradle.

## Gate measurements

`gate-test.sh` prints its fresh external artifact directory. Set
`SNAKT_GATE_ARTIFACTS` to an existing external parent directory to choose its location.
`gate_measure.py` emits command, cwd, monotonic elapsed nanoseconds and shell exit
status as JSON on stderr and in per-boundary JSON files. Gate totals enclose the
checks; individual consumer Gradle boundaries include expected failures.
Provenance files record candidate, dirty state, tool versions, build configuration
and relevant environment. `cache/` holds the existing raw verification cache reports;
`declarations/` holds [declaration timings](#declaration-timings), unless
`SNAKT_VERIFICATION_TIMINGS` selects another location. These reports come from the
test harness; consumer compilation does not emit them. Up-to-date tasks produce
no verification attempts. Gradle command records carry cache sampling and daemon flags.

## Exit codes

`check-all.sh` reports each of its four checks as passed, failed or skipped. A
skip means the run covered less than the command promises, and the gap is in the
setup rather than in the code, so it gets its own exit code rather than being
folded into either 0 or 1.

## The kept test JVM

`test.sh` does not run the Gradle test tasks. Gradle compiles and, through each
task's `RunnerSpec` task, writes what the task would run; the persistent runner
of `formver.test-runner` then runs it in a JVM that the next run reuses. The
compiler libraries and Silicon stay loaded between runs. The project's classes
and the tests stay loaded, with the verifier pool and its Z3 processes, while
their content and the mode properties, such as the test mode and
`--update-goldens`, are unchanged. A change to either closes what the loader
registered with `Lifetime` (in the bridge) and loads anew, so a run always sees
what Gradle just compiled.
Anything else the JVM was started with, changed, starts a new one and says what
changed; `Client` in `formver.test-runner` lists what that covers. The server
gets only the environment variables `testRunnerEnvironment` in `buildSrc` names.

There is one JVM per module per checkout, and it exits after idling for the
time `registerTestRunnerSpec` in `buildSrc` gives it. `--fresh-jvm` starts a new
one for a run, `--stop` stops this checkout's, and `SNAKT_TEST_RUNNER=off` runs
the Gradle test tasks instead.

## Recovering the diff

Gradle's cross-JVM result serialization strips the expected/actual values off a
golden-file assertion before they reach the console.
`DumpAssertionDiffExtension`, registered on the compiler-plugin test classpath
by `formver.compiler-plugin/test-resources/`, catches them inside the test JVM
instead. It is registered for every run and stays inert unless
`SNAKT_TEST_DUMP_DIR` is set, which `test.sh` does. The locality module has no
test fixtures on its classpath, so `test.sh` prints its first failure from the
JUnit XML instead.

Only golden-file assertions carry values to recover. For a thrown exception
`test.sh` prints the failure from the JUnit XML.

Diffs are printed with source-position offsets replaced by `(_,_)`, so a method
that only moved because of an edit earlier in the file drops out.

## Patterns

`GenerateTestsKt` capitalizes a testData file's stem and turns dashes into
underscores to form the method name, so `assign_local.kt` backs
`testAssign_local` and `non-local-returns.kt` backs `testNon_local_returns`.
Gradle's `--tests` filter is case-sensitive, and the scripts convert for you.

## Regenerating

`--update-goldens` regenerates and then prints what each golden now says.
Regeneration records whatever the run produced, except a change in a function's
verification outcome.

A function's outcome is failed when a verifier diagnostic lies inside it,
skipped when it carries `VERIFICATION_SKIPPED`, `UNSUPPORTED_FEATURE` or
`INTERNAL_ERROR`, and clean otherwise; clean
covers both verified and not selected for verification. The expected outcome is
read from the markers the `.kt` already has. When any function's outcome
differs, the test writes no golden at all and fails with
`OutcomeChangeRefused`, naming each function and quoting the new diagnostics;
`test.sh` lists these under "not regenerated" and exits 1. A failure that
starts verifying is a change too: a negative test that stops failing has lost
what it tests. The check is `VerificationOutcomes` in
`formver.compiler-plugin/test-fixtures/org/jetbrains/kotlin/formver/plugin/services/`.

`--update-goldens --record-outcomes <pattern>` lets outcomes change, for the
one test that is meant to record a new failure, skip or fix. It takes a pattern
so that it covers only that test, and prints the marker changes uncut.
