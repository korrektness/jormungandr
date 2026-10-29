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

| Task                       | Conversion | Verification                |
|:---------------------------|:-----------|:----------------------------|
| `./gradlew test`           | every test | every test                  |
| `./gradlew update`         | every test | where conversion changed    |
| `./gradlew untilConversion`| every test | never                       |

Use `untilConversion` as much as possible while developing, and `test` last,
before opening a PR.

All three only check the goldens. For regeneration, see
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
The handwritten tiers and their commands are listed in
[the agent scripts](agents-dev.md#handwritten-tiers).

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

`./gradlew check` runs detekt, `apiCheck`, every module's tests and `untilConversion`.

A separate CI workflow runs `pre-commit`; install the hook locally with
`pre-commit install`. Besides the formatting hooks it runs two checks that need
no build: one over the testData tree, and the tests for the repository's own
scripts.
