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
| `verificationWorkers(2)` | `verification_workers` | Nonnegative integer worker count; see [File-batch verification](#file-batch-verification). |
| `verificationCacheDirectory("build/verification-cache")` | `verification_cache_directory` | Nonblank directory path; see [Verification cache](#verification-cache). |
| `dumpUniquenessCFG(true)` | `dump_uniqueness_cfg` | `true`, `false`; dumps the uniqueness control-flow graph with flow information. |
| `exceptionFreeByDefault(true)` | `exception_free_by_default` | `true`, `false`; see [Exception freedom](#exception-freedom). |
| `externalSpec("specs/Library.kt")` | `external_spec` | Existing `.kt` source file; see [External specification inputs](#external-specification-inputs). |
| Managed by the Gradle plugin | `trust_report` | Nonblank output file path; see [Trust reports](#trust-reports). |
| Not exposed | `declaration_report` | Nonblank output file path; see [Declaration reports](#declaration-reports). |

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

## File-batch verification

`verification_workers` defaults to 0 for direct compiler use and 2 in Gradle. Zero
uses declaration-level checking. A positive count selects full-file traversal
and bounds the number of workers per file. Use it only when the host guarantees
that the file callback sees the complete file before descendant checks; PSI
availability or K2 support alone does not establish that capability. In Gradle,
set `verificationWorkers(0)` to select declaration-level checking explicitly.

Each verification program retains an independent proof context, timeout and
cache decision; batching does not combine proofs. Results and diagnostics are
replayed on the checker thread in submission order, independently of worker
completion order. Workers reuse compatible backends and close them on option
changes and before the batch returns. Interruption waits for in-flight work and
backend cleanup, so an unbounded verification may delay it indefinitely.

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
handling are described in the [cache design](viper-outcome-cache.md).
The repository test driver's cache is configured separately; see
[test cache controls](agents-dev.md#verification-cache).

## Exception freedom

`exception_free_by_default` defaults to `false`. When set, every source function without a `throwsOnlyIf` condition
must throw no exception, and its callers assume it throws none. The covered declarations and the errors, which name
what raises each escaping exception, are defined in
[Exception freedom](../SPECIFICATIONS.md#exception-freedom). In a [declaration report](#declaration-reports), the
`verification_detail` of a failed program lists these error kinds, so a declaration that verifies apart from
exception freedom is one whose failures are all `EXCEPTION_FROM_*` or `EXCEPTION_IN_TRY`, and its
`exception_callees` name the callees whose calls raise them.

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

## Declaration reports

`declaration_report` makes a direct compiler invocation write a tab-separated
file with program outcomes for function and property accessor bodies,
standalone lambda programs, and class programs. Class programs target selected
constructor, law, and inherited implementation obligations. A compilation
replaces the file. The first line names the columns:

| Column | Value |
|:--|:--|
| `file`, `line` | Source path and 1-based line where the declaration starts. |
| `declaration` | Fully qualified name, as in verification progress messages. |
| `program` | `body` for a function or accessor body, `class` for a class program, or `lambdaN` for the declaration's N-th lambda program, numbered as in verification progress messages. |
| `conversion` | `converted`; `stopped` when an error prevented conversion from completing; `excluded` when the function is not converted by design: marked `@NeverConvert`, a specification, a law, or not selected. |
| `conversion_kind` | For `stopped`, the [`FailureKind`](../formver.common/src/org/jetbrains/kotlin/formver/common/Failure.kt) of the first conversion error. |
| `conversion_reason` | For `stopped`, that error's reason code, the name of the rule or reason its condition carries, or else the condition's type name; for `excluded`, `NEVER_CONVERT`, `SPEC_OF`, `LAW` or `NOT_SELECTED`. |
| `conversion_detail` | For `stopped`, that error's message. |
| `verification` | For a converted program: `verified`, `failed`, `timeout`, `error`, `skipped` when conversion found errors that prevent verification, or `not_selected`. |
| `verification_detail` | For `failed`, the `FailureKind` of each verification error, sorted and comma-separated; for `skipped`, the skip reason; for `error`, the reason and message. |
| `seconds` | Verification time of the program. |
| `exception_callees` | For `failed`, one entry per `EXCEPTION_FROM_UNSPECIFIED_CALL` or `EXCEPTION_FROM_THROWING_CALL` error whose call has a declared callee, sorted and comma-separated. An entry is `KIND:origin:callee`: the error's `FailureKind`; `source` when the callee is declared in the compiled sources, `library` when it is read from a compiled dependency; and the callee's fully qualified name, including its owning class or package, named as the error message names it (see [Exception freedom](../SPECIFICATIONS.md#exception-freedom)), such as `kotlin.Any.toString`. |
| `program_id` | Stable per-compilation identifier for the reporting slot. A callable or standalone lambda uses its own region identity, and a class program uses the receiving class's syntax span. The progress label, lambda index and worker completion order are not part of the ID. |
| `target_region_id` | Census region targeted by the callable conversion or standalone lambda conversion; blank for class programs. |
| `inherited_candidate_region_ids` | Sorted comma-separated source implementation region IDs from the receiving class's selected inherited obligations. A candidate can appear in multiple classes' rows. |

The report always includes the three identity columns. They remain blank unless
`proof_checkout` (the census checkout root) and `proof_compilation` (the census
compilation ID) are supplied with `declaration_report`. Identity mode uses
compiler PSI (`-Xuse-fir-lt=false`) and is incompatible with `inputs_only`.
A region ID is the lowercase SHA-256 of the UTF-8 serialization
`census-region-v1<TAB>compilation<TAB>root<TAB>path<TAB>sha256<TAB>kind:start:end:discriminator`,
using the [source census](developing.md#production-kotlin-input-evidence) fields. The input digest
binds exact bytes; syntax spans count UTF-16 code units after BOM removal and
line-ending normalization.

Target and candidate IDs describe intent, including on stopped or excluded rows.
They do not establish body membership. For example, `ProgramBodyProcessor` can
emit a header instead of a definition; a terminal `verified` row for that
program does not prove the absent body. No census region receives coverage
credit without separate lowering evidence and reconciliation with all applicable
terminal outcomes.

Queued verification jobs that never start have no terminal row. This includes
jobs left in a stopped worker batch and jobs queued when checking aborts before
the batch runs. Compilations run by the test facade write no rows for converted
programs, because the facade verifies them itself.
