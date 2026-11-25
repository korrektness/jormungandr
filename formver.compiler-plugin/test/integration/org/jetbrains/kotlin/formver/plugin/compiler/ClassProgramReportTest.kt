@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.jetbrains.kotlin.formver.plugin.compiler

import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.common.messages.GroupingMessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.pipeline.ArgumentsPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmConfigurationPipelinePhase
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmFrontendPipelinePhase
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.diagnostics.DiagnosticContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.core.conversion.ConstructionVerdicts
import org.jetbrains.kotlin.formver.core.verificationSkipped
import org.jetbrains.kotlin.formver.locality.plugin.LocalityExtensionRegistrar
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.ReportedVerifierErrors
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessExtensionRegistrar
import org.jetbrains.kotlin.formver.viper.InvalidViperProgramException
import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import kotlin.test.*

class ClassProgramReportTest {
    @TempDir
    lateinit var temp: File

    private data class Outcome(val conversion: String, val kind: String, val reason: String, val verification: String)
    private data class Result(
        val rows: Map<String, Outcome>,
        val diagnostics: List<String>,
        val skipped: Map<String, Boolean>,
        val failure: Throwable?,
    )

    private fun compile(
        source: String,
        sink: VerificationSink,
        flush: () -> Unit = {},
        conversionFailure: (() -> Throwable)? = null,
        locateSolver: () -> String = { "unused-z3" },
        verificationCacheDirectory: String? = null,
    ): Result {
        val file = temp.resolve("Classes.kt").apply { writeText(source.trimIndent()) }
        val report = temp.resolve("classes.tsv")
        val config = PluginConfiguration(
            LogLevel.ONLY_WARNINGS, ErrorStyle.USER_FRIENDLY, TargetsSelection.ALL_TARGETS, TargetsSelection.ALL_TARGETS,
            false, MessageCollector.NONE, declarationReport = DeclarationReport(report),
            verificationCacheDirectory = verificationCacheDirectory,
        )
        val diagnostics = mutableListOf<String>()
        val skipped = mutableMapOf<String, Boolean>()
        var failure: Throwable? = null
        val registrar = object : FirExtensionRegistrar() {
            override fun ExtensionRegistrarContext.configurePlugin() {
                +{ session: FirSession ->
                    val programs = ClassPrograms(session, config)
                    ConstructionVerdicts(session, programs::constructionVerified)
                }
                +FirAdditionalCheckersExtension.Factory { session ->
                    object : FirAdditionalCheckersExtension(session) {
                        override val declarationCheckers = object : DeclarationCheckers() {
                            override val fileCheckers = setOf(object : FirFileChecker(MppCheckerKind.Common) {
                                context(context: CheckerContext, reporter: DiagnosticReporter)
                                @OptIn(DirectDeclarationsAccess::class)
                                override fun check(declaration: FirFile) {
                                    val checkReporter = object : DiagnosticReporter() {
                                        override fun report(diagnostic: KtDiagnostic?, context: DiagnosticContext) {
                                            val kind = diagnostic?.factoryName ?: return
                                            diagnostics += kind
                                            if (kind == FailureKind.UNSUPPORTED_FEATURE.name && conversionFailure != null) {
                                                throw conversionFailure()
                                            }
                                        }
                                    }
                                    val checker = ViperPoweredClassChecker(session, config, VerifierLocator(locateSolver),
                                        ReportedVerifierErrors(), sink, useVerificationFacade = false)
                                    val classes = declaration.declarations.filterIsInstance<FirRegularClass>()
                                    failure = runCatching {
                                        with(checkReporter) { classes.forEach { checker.check(it) } }
                                        flush()
                                    }.exceptionOrNull()
                                    for (owner in classes) skipped[owner.name.asString()] = owner.verificationSkipped == true
                                }
                            })
                        }
                    }
                }
            }
        }
        runFrontend(file, registrar)
        val rows = if (report.isDirectory) emptyList() else report.readLines().drop(1).map { it.split('\t') }
        val mapped = rows.associate { "${it[2]}.${it[3]}" to Outcome(it[4], it[5], it[6], it[8]) }
        assertEquals(rows.size, mapped.size)
        return Result(mapped, diagnostics, skipped, failure)
    }

    @Test
    fun `immediate backend errors retain class diagnostics and overflow propagates with one row`() {
        val overflow = StackOverflowError()
        val errors = listOf(
            InvalidViperProgramException(listOf("invalid")), UnsupportedFeatureException(null, "unsupported"), overflow,
        ).iterator()
        val result = compile(
            "class Invalid(val n: Int)\nclass Unsupported(val n: Int)\nclass Overflow(val n: Int)",
            VerificationSink { _, replay ->
                val error = errors.next()
                replay { throw error }
            },
        )
        assertSame(overflow, result.failure)
        assertEquals(listOf(FailureKind.INTERNAL_ERROR.name, FailureKind.UNSUPPORTED_FEATURE.name), result.diagnostics)
        assertEquals(mapOf("Invalid" to false, "Unsupported" to false, "Overflow" to false), result.skipped)
        assertEquals(mapOf("Invalid.class" to backendError, "Unsupported.class" to backendError, "Overflow.class" to backendError), result.rows)
    }

    @Test
    fun `a report append failure does not replace the backend diagnostic`() {
        val result = compileWithBrokenReport(InvalidViperProgramException(listOf("invalid")))
        assertIs<IOException>(result.failure)
        assertEquals(listOf(FailureKind.INTERNAL_ERROR.name), result.diagnostics)
    }

    @Test
    fun `a report append failure after verification is not diagnosed as a backend failure`() {
        val result = compileWithBrokenReport(null)
        assertIs<IOException>(result.failure)
        assertEquals(emptyList(), result.diagnostics)
    }

    private fun compileWithBrokenReport(backendFailure: Throwable?) = compile(
        "class Checked(val n: Int)",
        VerificationSink { _, replay ->
            breakReport()
            replay { VerificationResult(emptyList(), backendFailure) }
        },
    )

    private fun breakReport() {
        val report = temp.resolve("classes.tsv")
        check(report.delete() && report.mkdir())
    }

    @Test
    fun `a report append failure does not replace the class conversion diagnostic`() {
        val result = compile(
            "class Stops { init { val half = 0.5 } }",
            VerificationSink { _, _ -> error("Stopped conversion submitted verification") },
            conversionFailure = {
                breakReport()
                IllegalStateException()
            },
        )
        assertIs<IOException>(result.failure)
        assertEquals(listOf(FailureKind.UNSUPPORTED_FEATURE.name, FailureKind.INTERNAL_ERROR.name), result.diagnostics)
    }

    @Test
    fun `verification overflow remains primary if its report append fails`() {
        val overflow = StackOverflowError()
        val result = compileWithBrokenReport(overflow)
        assertSame(overflow, result.failure)
        assertIs<IOException>(overflow.suppressed.single())
    }

    @Test
    fun `solver lookup exceptions produce a verification error without conversion skip`() {
        val result = compile(
            "class BadLookup(val n: Int)",
            VerificationSink { _, _ -> throw AssertionError("Verification submitted without a solver") },
            locateSolver = { throw IllegalStateException() },
        )
        assertNull(result.failure)
        assertEquals(listOf(FailureKind.INTERNAL_ERROR.name), result.diagnostics)
        assertEquals(false, result.skipped["BadLookup"])
        assertEquals(mapOf("BadLookup.class" to backendError), result.rows)
    }

    @Test
    fun `an invalid cache path records a request construction failure without conversion skip`() {
        val result = compile(
            "class BadRequest(val n: Int)",
            VerificationSink { _, _ -> throw AssertionError("Verification submitted with an invalid cache path") },
            verificationCacheDirectory = "cache\u0000directory",
        )
        assertNull(result.failure)
        assertEquals(listOf(FailureKind.INTERNAL_ERROR.name), result.diagnostics)
        assertEquals(false, result.skipped["BadRequest"])
        assertEquals(mapOf("BadRequest.class" to backendError), result.rows)
    }

    @Test
    fun `worker cancellation leaves its row alongside successful and invalid siblings`() =
        assertBatchRows(InterruptedException(), internalErrors = 2)

    @Test
    fun `worker assertion failure leaves its row and propagates unchanged`() =
        assertBatchRows(AssertionError(), internalErrors = 1)

    private fun assertBatchRows(terminal: Throwable, internalErrors: Int) {
        val failures = listOf(null, InvalidViperProgramException(listOf("invalid")), terminal).iterator()
        val sink = FileVerificationSink(1) {
            object : FileVerificationBackend {
                override fun verify(program: viper.silver.ast.Program, onError: (VerifierError) -> Unit) {
                    failures.next()?.let { throw it }
                }
                override fun close() = Unit
            }
        }
        val result = compile("class Good(val n: Int)\nclass Invalid(val n: Int)\nclass Terminal(val n: Int)", sink, { sink.flush() })
        assertSame(terminal, result.failure)
        assertEquals(List(internalErrors) { FailureKind.INTERNAL_ERROR.name }, result.diagnostics)
        assertEquals(mapOf("Good.class" to Outcome("converted", "", "", "verified"),
            "Invalid.class" to backendError, "Terminal.class" to backendError), result.rows)
    }

    @Test
    fun `an overflow during class conversion records a stopped row before escaping`() {
        val overflow = StackOverflowError()
        val result = compile("class Stops { init { val half = 0.5 } }", VerificationSink { _, _ ->
            error("Stopped conversion submitted verification")
        }, conversionFailure = { overflow })
        assertSame(overflow, result.failure)
        assertEquals(mapOf("Stops.class" to Outcome("stopped", FailureKind.INTERNAL_ERROR.name,
            InternalReason.UNEXPECTED_EXCEPTION.name, "")), result.rows)
    }

    @Test
    fun `class-owned lambda overflow gets the lambda skip diagnostic and does not escape`() {
        val result = compile(
            """
            import org.jetbrains.kotlin.formver.plugin.*
            fun interface Spec {
                @Unique @Borrowed fun invoke(x: Int): Int
                @GhostObserver @Pure fun state(): Int = ghostObserver()
            }
            interface Owner {
                val action: Spec get() = Spec { x ->
                    exposes(Spec::class) { state() == 0 }
                    x
                }
            }
            """, VerificationSink { _, replay -> replay { throw StackOverflowError() } },
        )
        assertNull(result.failure)
        assertEquals(listOf(FailureKind.INTERNAL_ERROR.name, FailureKind.VERIFICATION_SKIPPED.name), result.diagnostics)
        assertEquals(true, result.skipped["Owner"])
        assertEquals(mapOf("Owner.lambda0" to backendError), result.rows)
    }

    private companion object {
        val backendError = Outcome("converted", "", "", "error")
    }
}

private fun runFrontend(file: File, checkers: FirExtensionRegistrar) {
    val compiler = K2JVMCompiler()
    val stdlib = File(KotlinVersion::class.java.protectionDomain.codeSource.location.toURI())
    val classpath = listOf(stdlib.path, System.getProperty("annotationsRuntime.classpath")).joinToString(File.pathSeparator)
    val args = compiler.createArguments()
    compiler.parseArguments(arrayOf("-no-stdlib", "-no-reflect", "-classpath", classpath, file.path), args)
    val root = Disposer.newDisposable()
    try {
        val messages = GroupingMessageCollector(MessageCollector.NONE, false, false)
        val artifact = checkNotNull(JvmConfigurationPipelinePhase.executePhase(
            ArgumentsPipelineArtifact(args, Services.EMPTY, root, messages, compiler.defaultPerformanceManager),
        ))
        artifact.configuration.add(CompilerPluginRegistrar.COMPILER_PLUGIN_REGISTRARS, object : CompilerPluginRegistrar() {
            override val supportsK2 = true
            override val pluginId = "formver.class-report-test"
            override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
                FirExtensionRegistrarAdapter.registerExtension(checkers)
                FirExtensionRegistrarAdapter.registerExtension(LocalityExtensionRegistrar())
                FirExtensionRegistrarAdapter.registerExtension(UniquenessExtensionRegistrar())
            }
        })
        assertNotNull(JvmFrontendPipelinePhase.executePhase(artifact))
    } finally {
        Disposer.dispose(root)
    }
}
