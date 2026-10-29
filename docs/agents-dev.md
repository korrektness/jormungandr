# The agent scripts

Use this reference for script controls, regeneration, persistent runners and
landing coverage. [Developing the plugin](developing.md#tests) owns test tiers,
fixture modes, source paths and local task selection.

`agent-scripts/` wraps the Gradle test tasks so the loop does not have to be
rediscovered from the build files each time. Nothing in the build depends on
them.

- `test.sh` — the generated compiler/locality fixture driver; see
  [fixture modes](developing.md#fixture-modes), [regeneration](#regenerating),
  and the `--vacuity` check in [soundness](soundness.md).
- `check-all.sh` — aggregate coverage; see [Landing and nightly](#landing-and-nightly).
  `--rerun` re-executes tests Gradle considers current.
- `gate-test.sh` — what the land gate runs on each candidate; see [Landing and nightly](#landing-and-nightly).
- `check-gradle-consumer.sh` — the [published-plugin consumer driver](developing.md#published-plugin-consumers).
- `check-testdata.sh` — missing runnable roots, golden files outside discovery,
  golden files with no source, empty goldens and unstable identity hashes.

`test.sh` and `check-all.sh` take `--help`, and need `python3` on PATH because
the test results they report from are XML; `check-testdata.sh` needs nothing but
a checkout.

## Handwritten tiers

See [Tiers and local selection](developing.md#tiers-and-local-selection) for
source sets and task commands.

Compiler handwritten tasks also expose `<task>RunnerSpec`. Run the generated
`build/test-runner/<task>/client run --spec build/test-runner/<task>/spec.properties`
from the compiler module directory to use a persistent JVM. Each tier has its own
slot; the fixture tasks share the `compiler` slot. The fixture driver selects
only fixture specs in every mode.

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

## Landing and nightly

`gate-test.sh` runs `check-testdata.sh`, detekt, `apiCheck`, handwritten unit and small
integration tiers, compiler `untilConversion` and locality fixture tests. It verifies a
compiler fixture only when a file under `testData/diagnostics` or
`testData/programs` with that test's stem differs from `origin/HEAD`'s merge base, using the shared outcome cache with the cold sample. A
change whose goldens do not move gets no additional fixture verification.

Before landing a change that can alter verification without changing a golden
(the Viper module, compiler test-fixtures, Silicon or Z3 versions, outcome cache,
Gradle plugin, publishing or shading), run verification covering the affected
boundary and name the command and result in the commit message. Use
`agent-scripts/test.sh --verify <pattern>` for affected fixtures,
`agent-scripts/check-gradle-consumer.sh` for packaging, or
`agent-scripts/check-all.sh` for comprehensive coverage.

Root `./gradlew check` runs detekt, `apiCheck` and module checks. Compiler `check`
aggregates handwritten tiers, full fixture verification and conversion.
`check-all.sh` adds the published-plugin consumers, pre-commit and
structural checks. Auxiliary program checks, the consumer check and verification
of everything else run nightly: the host's nightly-tests runner runs the root
`test.sh`, which is `check-all.sh --rerun` without the outcome cache, and pages the
operator when it fails. Keep published-plugin and other end-to-end checks in
`check-all.sh`; gate selection stays at unit, small integration, conversion and
changed-fixture verification boundaries.

## Gate measurements

`gate-test.sh` prints its fresh external artifact directory. Set
`SNAKT_GATE_ARTIFACTS` to an existing external parent directory to choose its location.
`gate_measure.py` emits command, cwd, monotonic elapsed nanoseconds and shell exit
status as JSON on stderr and in per-boundary JSON files. Gate totals enclose the
checks. `check-gradle-consumer.sh` records each of its Gradle boundaries the same way,
expected failures included, into `SNAKT_GATE_ARTIFACTS` when that is set.
Provenance files record candidate, dirty state, tool versions, build configuration
and relevant environment. `cache/` holds the existing raw verification cache reports;
`declarations/` holds [declaration timings](#declaration-timings), unless
`SNAKT_VERIFICATION_TIMINGS` selects another location. These reports come from the
test harness; consumer compilation does not emit them. Up-to-date tasks produce
no verification attempts. Gradle command records carry cache sampling and daemon flags.

## Exit codes

`check-all.sh` reports each of its four checks as passed, failed or skipped.
Exit 0 means success, 1 means failure, and 2 means a check did not run. For a
skip, resolve the missing prerequisite and run again; the command's promised
coverage is incomplete.

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

There is one JVM per runner slot per checkout, and it exits after idling for the
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

`--update-goldens` runs the full pipeline, including Silicon, regenerates, and
then prints what each golden now says. It is a separate mode, mutually exclusive
with `--verify`, `--verify-changed` and `--vacuity`; it does not modify the default
conversion-only mode. See the mode-to-task mapping in
[`agent-scripts/test.sh`](../agent-scripts/test.sh).

Full-pipeline regeneration records whatever the run produced, except a change in
a function's verification outcome.

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
