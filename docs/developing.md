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

We use the test framework built for kotlinc. A test is a `.kt` file under
`formver.compiler-plugin/testData/diagnostics/` annotated with expected
diagnostics, alongside golden files holding the diagnostic text:

- `.fir.diag.txt` — the conversion output, including the generated Viper code.
- `.viper.diag.txt` — verification diagnostics. Present only where verification
  reported something.

The test runners are generated from the testData tree as part of
`compileTestKotlin`, so a new file is picked up on the next build.

The pipeline splits into conversion (uniqueness checking, conversion, purity
checking) and verification (Viper consistency checking and verification):

| Task                       | Conversion | Verification                |
|:---------------------------|:-----------|:----------------------------|
| `./gradlew test`           | every test | every test                  |
| `./gradlew update`         | every test | where conversion changed    |
| `./gradlew untilConversion`| every test | never                       |

Use `untilConversion` as much as possible while developing, and `test` last,
before opening a PR.

All three only check the goldens. Regenerating them is a separate thing, run by
passing `-Pkotlin.test.update.test.data=true` to `test`: it rewrites the golden
files and writes the diagnostic markers into the `.kt`, which a new test needs.
Verification runs, because the goldens include its output. A change in a
function's verification outcome is refused unless
`-Pformver.recordOutcomes=true` is passed as well; docs/agents-dev.md, under
Regenerating, says what counts as one.

### Negative tests

A test whose functions verify also holds functions that must fail, in the same
file and over the same classes:

- A sibling that breaks one thing the verified function relies on, such as
  writing through a shared root instead of an owned one.
- A vacuity probe for each function with a precondition or a loop invariant: a
  copy named `<function>Probe<Point>` that adds `verify(false)` at one point.
  Probes go at the start of a function with a precondition, at the end of every
  loop body, and after the last loop of a function. If a probe verifies, the
  specifications or the permissions the plugin adds are inconsistent there, and
  whatever verifies past that point proves nothing.

### Directives

Test files support directives that control how they run, written as `// NAME` at
the top of the file. `FULL_JDK` and `WITH_STDLIB` come from the Kotlin test
framework; ours are declared in `FormVerDirectives`, in
`formver.compiler-plugin/test-fixtures/org/jetbrains/kotlin/formver/plugin/services/ExtensionRegistrarConfigurator.kt`.

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

`./gradlew check` runs detekt, `apiCheck` and every module's tests.

A separate CI workflow runs `pre-commit`; install the hook locally with
`pre-commit install`. Besides the formatting hooks it runs two checks that need
no build: one over the testData tree, and the tests for the repository's own
scripts.
