# Configuring SnaKt

Use this reference when selecting compiler checks or translating a Gradle setting
into a `kotlinc` option. For specification syntax, see
[Writing specifications](../SPECIFICATIONS.md).

## Gradle and command-line options

Put Gradle settings in `formver { ... }`. With `kotlinc`, pass each setting as
`-P plugin:org.jetbrains.kotlin.formver:OPTION=VALUE`.

| Gradle setting | Compiler option | Values and purpose |
|:--|:--|:--|
| `logLevel("full_viper_dump")` | `log_level` | `only_warnings`, `short_viper_dump`, `full_viper_dump`; controls generated Viper diagnostics. |
| `errorStyle("both")` | `error_style` | `user_friendly`, `original_viper`, `both`; selects the rendering of proof failures. |
| `conversionTargetsSelection("all_targets")` | `conversion_targets_selection` | `no_targets`, `all_targets`; selects Viper conversion. |
| `verificationTargetsSelection("no_targets")` | `verification_targets_selection` | `no_targets`, `all_targets`; selects backend verification. |
| `counterexamples(true)` | `counterexamples` | `true`, `false`; requests [counterexample models](../SPECIFICATIONS.md#counterexamples). |
| `verificationTimeout(60)` | `verification_timeout` | Nonnegative integer seconds; see [Timeouts](#timeouts). |
| `verificationCacheDirectory("build/verification-cache")` | `verification_cache_directory` | Nonblank directory path; see [Verification cache](#verification-cache). |
| `dumpUniquenessCFG(true)` | `dump_uniqueness_cfg` | `true`, `false`; dumps the uniqueness control-flow graph with flow information. |
| `externalSpec("specs/Library.kt")` | `external_spec` | Existing `.kt` source file; see [External specification inputs](#external-specification-inputs). |
| Managed by the Gradle plugin | `trust_report` | Nonblank output file path; see [Trust reports](#trust-reports). |

The option registry and parsers are
[`FormalVerificationCommandLineProcessor`](../formver.compiler-plugin/cli/src/org/jetbrains/kotlin/formver/cli/FormalVerificationCommandLineProcessor.kt).
The Gradle API is
[`FormVerExtension`](../formver.gradle-plugin/src/org/jetbrains/kotlin/formver/gradle/FormVerExtension.kt);
[`FormVerGradleSubplugin`](../formver.gradle-plugin/src/org/jetbrains/kotlin/formver/gradle/FormVerSubplugin.kt)
translates those settings and resolves paths. Boolean option values are lowercase.
Only `external_spec` accepts repeated occurrences.

## Selecting checks

By default, application declarations are selected for conversion and verification.
To inspect conversion without invoking the verifier:

```kotlin
formver {
    verificationTargetsSelection("no_targets")
    logLevel("full_viper_dump")
}
```

The corresponding compiler options are:

```sh
-P plugin:org.jetbrains.kotlin.formver:verification_targets_selection=no_targets \
-P plugin:org.jetbrains.kotlin.formver:log_level=full_viper_dump
```

Conversion diagnostics and generated Viper are evidence about translation, not a
proof that the Kotlin program satisfies its specifications. External-specification
contradiction checks also require verification to be selected. Declaration-level
selection and unsupported constructs are described under
[Verification control](../SPECIFICATIONS.md#verification-control).

To disable Viper conversion, set both selections to `no_targets`. Selecting
verification while disabling conversion is an invalid configuration; the invariant
is enforced by
[`PluginConfiguration`](../formver.common/src/org/jetbrains/kotlin/formver/common/PluginConfiguration.kt).

## Diagnostics and progress

Viper dumps are compiler INFO messages. Run Gradle with `--info` to see them.
The default log level is `only_warnings`; the default error style is
`user_friendly`. Diagnostic meanings are described under
[Diagnostics](../SPECIFICATIONS.md#diagnostics).

The compiler drops warnings from a compilation that has errors, including warnings
about unverified declarations. The Gradle plugin passes `-Xreport-all-warnings`;
pass it explicitly when invoking `kotlinc`.

The plugin logs the start and duration of each declaration's verification at INFO.
The compiler buffers these messages until it reports diagnostics, so they identify
slow declarations after completion rather than providing live progress.

## Timeouts

`verification_timeout` bounds Silicon's verification of each declaration,
separately for each specified lambda. Its default is defined by
[`PluginConfiguration.DEFAULT_VERIFICATION_TIMEOUT_SECONDS`](../formver.common/src/org/jetbrains/kotlin/formver/common/PluginConfiguration.kt).
Zero disables the limit. A declaration that exhausts the limit gets
`VERIFICATION_TIMEOUT`, and verification continues with the next declaration.
The limit covers a single SMT query: Silicon stops waiting, and the plugin then
stops Silicon, which kills its Z3 processes.

## Verification cache

Omitting `verification_cache_directory` bypasses the persistent success cache.
Gradle resolves a configured directory relative to the project; direct compiler
invocations resolve relative paths from their working directory.

Treat the directory as trusted build state. Custom Silicon plugins and obligation
filters require bypassing the cache. Cache keys, hit checks and damaged-entry
handling are described in the [cache design](targets/viper-outcome-cache.md).
The repository test driver's cache is configured separately; see
[test cache controls](agents-dev.md#verification-cache).

## External specification inputs

`externalSpec` accepts the path inputs supported by Gradle's `Project.files` and
may name several files in one call. Gradle tracks these files as compilation
inputs. With `kotlinc`, repeat the option for each file:

```sh
-P plugin:org.jetbrains.kotlin.formver:external_spec=specs/Library.kt \
-P plugin:org.jetbrains.kotlin.formver:external_spec=specs/OtherLibrary.kt
```

These are analysis-only sources. Their binding rules and trust boundary are in
[External specifications](../SPECIFICATIONS.md#external-specifications).

## Trust reports

The Gradle plugin writes a report for each platform compile task to
`build/formver/<task>/trust-report.txt` and prints its summary at the default
logging level. Metadata compilations do not write a report.

For direct compiler use, `trust_report` selects the output file. Without it, the
compiler reports the summary at INFO. A compilation replaces entries for the files
it compiles and retains other entries for incremental compilation. The report's
contents and interpretation are defined in
[The trust report](soundness.md#the-trust-report).
