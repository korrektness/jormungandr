# Developing the plugin

Publishing a new Silicon build: publish-silicon.md.

## Ownership

SPECIFICATIONS.md, under Ownership, describes what `@Unique`, `@Borrowed` and
`@Manual` mean to users and which constructs are rejected. A function with a
uniqueness or locality error, or with a rejected ownership construct
(`UNSUPPORTED_OWNERSHIP`), is converted but not verified, and reports
`VERIFICATION_SKIPPED`. A `var` write or array store through a local holding a
shared constructor result is dropped and reports `UNTRACKED_WRITE`.

## Tests

Choose a tier by the boundary under test; choose a fixture mode by the work
needed on that boundary. Tier and mode are independent: both small feature
fixtures and complete programs support conversion and verification.

### Tiers and local selection

| Boundary | Source home | Local entry point |
|:---------|:------------|:------------------|
| Unit: in-process logic with controlled dependencies | Compiler, core and uniqueness `test/unit` | `:formver.compiler-plugin:unitTest`, `:formver.compiler-plugin:core:unitTest`, `:formver.compiler-plugin:uniqueness:unitTest` |
| Integration: compiler/PSI services, Silicon protocol, runner processes | Compiler and runner `test/integration` | `:formver.compiler-plugin:integrationTest`, `:formver.test-runner:integrationTest` |
| Feature fixtures: one language, ownership, specification or library feature | Compiler `testData/diagnostics/` | `agent-scripts/test.sh <pattern>` |
| Programs: complete algorithms, ports and auxiliary correspondence/arithmetic checks | Compiler `testData/programs/` and `test/programs` | `agent-scripts/test.sh <pattern>` for fixtures; `:formver.compiler-plugin:programTest` for auxiliary checks |
| Published-plugin consumers: packaging and application in independent Gradle builds | Gradle plugin `testData/consumer/` | `agent-scripts/check-gradle-consumer.sh` |

Prefix task paths with `./gradlew`. Handwritten tasks accept
`--tests '<class or method pattern>'`, `--test-dry-run` for discovery and
`--rerun`. Integration provisions Z3 for Silicon checks. Auxiliary program tasks
include integration output for the PSI correspondence checker; discovery uses
only their own output. Core, uniqueness and runner `:test` tasks execute their
module's complete handwritten tier.

Use the smallest relevant local entry point. For fixture changes, start with
conversion, then verify the affected fixture. Aggregate selection and the
requirement for changes without golden diffs are owned by
[Landing and nightly](agents-dev.md#landing-and-nightly).

### Fixture modes

We use the test framework built for kotlinc. A runnable fixture is a `.kt`
file in the [fixture layout](#fixture-layout), annotated with expected diagnostics,
alongside golden files holding the diagnostic text:

- `.fir.diag.txt` — the conversion diagnostics.
- `.viper.txt` — the generated Viper code.
- `.viper.diag.txt` — verification diagnostics. Present only where verification
  reported something.

The test runners are generated from the testData tree as part of
`compileTestKotlin`, so a new file is picked up on the next build.

The pipeline splits into conversion (uniqueness checking, conversion, purity
checking, Viper consistency checking) and verification:

| Fixture driver | Compiler task | Conversion | Verification |
|:---------------|:--------------|:-----------|:-------------|
| `agent-scripts/test.sh [pattern]` | `:formver.compiler-plugin:untilConversion` | selected fixtures | never |
| `agent-scripts/test.sh --verify [pattern]` | `:formver.compiler-plugin:test` | selected fixtures | selected fixtures |
| `agent-scripts/test.sh --verify-changed [pattern]` | `:formver.compiler-plugin:update` | selected fixtures | where conversion changed |

Without a pattern, selection covers all generated fixtures. These modes check
goldens; they do not regenerate them. The driver also selects locality fixtures,
which have no verification stage. `sourceSets.test` contains generated runners
in `test-gen`; handwritten tiers have separate tasks and discovery roots.

For regeneration and its outcome-change safeguard, see
[Regenerating](agents-dev.md#regenerating).
Regeneration writes golden files and diagnostic markers into the `.kt`, which a
new test needs.

For shared verification caching and cold runs, see
[the agent script cache controls](agents-dev.md#verification-cache).

### Fixture layout

Under `formver.compiler-plugin/testData/`:

| Path | Boundary |
|:-----|:---------|
| `diagnostics/language/<feature>/` | Small language/conversion examples |
| `diagnostics/ownership/<feature>/` | Ownership checker examples |
| `diagnostics/specifications/<feature>/` | Specification examples, including `external/` bindings and loading |
| `diagnostics/stdlib/<API>/` | Small standard-library API examples |
| `programs/algorithms/<algorithm>/` | Complete algorithms and their negative variants |
| `programs/ports/<library>/` | Ported implementations and correspondence artifacts |
| `support/libraries/` | Helper library inputs, including correspondence-check inputs |
| `support/specifications/` | Helper external specification inputs |
| `upstream/` | Vendored reference sources |
| `experiments/` | Exploratory sources |

`GenerateTests.kt` discovers only `diagnostics/` and `programs/`, recursively.
Keep each runnable source and its goldens together. Keep existing subject groups
intact; classify a fixture by its boundary as a whole. Helper inputs are resolved
by explicit directives or auxiliary checks and have no generated fixture runner.
Choose handwritten tasks from [Tiers and local selection](#tiers-and-local-selection).

### Published-plugin consumers

`formver.gradle-plugin/testData/consumer/` contains independent Gradle projects:

- `external-specifications-jvm/`: stand-in and verified library modules, external
  and bundled spec callers, trust reporting, spec erasure, and rejection of spec
  references, missing bindings, contradictory specs and false claims. Optional
  source sets and spec files select negative variants. A rebuild followed by a
  spec-only edit checks caller invalidation.
- `production-batch/`: the small `language/soundness/file_batch.kt` fixture through
  the production registrar, with positive and negative variants in declaration,
  one-worker, two-worker and default modes. The driver compares diagnostics and
  checks suppression, declaration ownership and independent proof contexts.
- `bundled-specifications-multiplatform/`: common, JVM and JVM-test callers of
  bundled collection specs, compiled with explicit API mode, extra warnings and
  warnings as errors; compiled output is checked for spec leakage.

`agent-scripts/check-gradle-consumer.sh` publishes to a private Maven repository,
then copies each project into isolated temporary storage. Settings and build
scripts read the repository from `-Dformver.repo`. The driver supplies the daemon
JVM criteria and uses the repository wrapper. In-process Kotlin compilation in
fixture `gradle.properties` passes this run's Z3 environment to the plugin.
The driver derives positive BFS and negative successor-scan sources from the
`testData/programs/algorithms/bfs/` program fixtures, strips diagnostic markup, and
edits only the scratch copy for caller invalidation. Assertions and expected diagnostic locations
stay in the driver. These consumer sources have no generated fixture runners or
goldens.

Run `./agent-scripts/check-gradle-consumer.sh` for this end-to-end boundary. It
needs network access for dependencies and an executable Z3: `Z3_EXE`, `z3` on
PATH, or the copy provisioned by verification tasks under the Gradle user home.

### Verification timeout

The test facade verifies each declaration with the plugin's default verification
timeout, described in [Timeouts](configuration.md#timeouts). A test that runs
out of time reports `VERIFICATION_TIMEOUT`, which counts as a failed outcome when
goldens are regenerated.

### Negative tests

A test whose functions verify also holds functions that must fail, in the same
file and over the same classes:

- A sibling that breaks one thing the verified function relies on, such as
  writing through a shared root instead of an owned one.
- Vacuity probes inside positive impure functions: put `refute(false)` after
  the specification blocks when there is a precondition, at the end of every
  loop body, and after the last loop. An unreachable point or inconsistent
  assumptions make the probe fail; success is a reachability check in the
  verifier's state, not a proof of concrete execution. A probe after an `if`
  or `when` covers only the paths that reach it, so a dead branch needs a probe
  of its own. The builtin's public contract is in
  `formver.annotations/src/org/jetbrains/kotlin/formver/plugin/Builtins.kt`.
  `./agent-scripts/test.sh --vacuity` adds the entry and exit probes of every
  method itself; `docs/soundness.md` describes the check.

### Directives

Test files support directives that control how they run, written as `// NAME` at
the top of the file. `FULL_JDK` and `WITH_STDLIB` come from the Kotlin test
framework; ours are declared in `FormVerDirectives`, in
`formver.compiler-plugin/test-fixtures/org/jetbrains/kotlin/formver/plugin/services/ExtensionRegistrarConfigurator.kt`.

Every test compiles as the Gradle plugin compiles user code: against the full JDK and the standard library, with
the bundled specifications of `formver.compiler-plugin/plugin/resources` loaded. A test records diagnostics in a
specification file only when it names that file with `EXTERNAL_SPEC`.

Which checks run:

Locality and uniqueness checking run in every mode that converts.

- `NEVER_VALIDATE` — convert but do not verify. Consistency checking still runs.
  This is how a test that is not meant to reach the verifier says so.
- `UNIQUE_CHECK_ONLY` — locality and uniqueness checking only. No conversion.
- `LOCALITY_CHECK_ONLY` — locality checking alone, uniqueness off. No conversion.
- `ALWAYS_VALIDATE` — verify every target. Verification is already the default,
  so this changes nothing on its own; it earns its place by overriding the two
  `*_CHECK_ONLY` directives above.

What the diagnostic contains:

- `FULL_VIPER_DUMP` — the whole Viper program.
- `RENDER_PREDICATES` — class predicates. Cannot be combined with the above.
- `DUMP_UNIQUENESS_CFG` — the control-flow graph with flow information.

And `REPLACE_STDLIB_EXTENSIONS` substitutes stdlib functions such as `run` with
versions whose bodies the plugin can see.

## Checks

For aggregate `check`, gate and nightly task selection, see
[Landing and nightly](agents-dev.md#landing-and-nightly).

A separate CI workflow runs `pre-commit`; install the hook locally with
`pre-commit install`. Besides the formatting hooks it runs two checks that need
no build: one over the testData tree, and the tests for the repository's own
scripts.
