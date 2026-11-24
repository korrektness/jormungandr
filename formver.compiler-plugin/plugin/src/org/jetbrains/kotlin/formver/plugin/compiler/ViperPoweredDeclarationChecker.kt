/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.core.names.embedName
import org.jetbrains.kotlin.formver.core.conversion.registerLambdaValueBody
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.formver.core.isPure
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.formver.core.conversion.SpecifiedLambda
import org.jetbrains.kotlin.formver.core.names.ScopedName
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.common.DeclarationReport
import org.jetbrains.kotlin.formver.common.ErrorStyle
import org.jetbrains.kotlin.formver.common.Failure
import org.jetbrains.kotlin.formver.common.FailureKind
import org.jetbrains.kotlin.formver.common.FailureToFirRenderer
import org.jetbrains.kotlin.formver.common.ConversionOwnershipCondition
import org.jetbrains.kotlin.formver.common.FoldOwnershipCondition
import org.jetbrains.kotlin.formver.common.PurityCondition
import org.jetbrains.kotlin.formver.common.TerminationCondition
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.toFailure
import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.common.LogLevel
import org.jetbrains.kotlin.formver.common.PluginConfiguration
import org.jetbrains.kotlin.formver.common.externalSpecifications
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.common.asInternalErrorAt
import org.jetbrains.kotlin.formver.common.TargetsSelection
import org.jetbrains.kotlin.formver.core.conversion.ProgramConverter
import org.jetbrains.kotlin.formver.core.conversion.record
import org.jetbrains.kotlin.formver.core.conversion.specOfId
import org.jetbrains.kotlin.formver.core.conversion.skipsVerification
import org.jetbrains.kotlin.formver.core.conversion.verificationSkipReason
import org.jetbrains.kotlin.formver.core.diagnostics.ConversionErrors
import org.jetbrains.kotlin.formver.common.reportTranslation
import org.jetbrains.kotlin.formver.common.reportTool
import org.jetbrains.kotlin.formver.common.reportInternalFailure
import org.jetbrains.kotlin.formver.common.reportFailure
import org.jetbrains.kotlin.formver.core.embeddings.expression.debug.print
import org.jetbrains.kotlin.formver.core.names.SimpleNameResolver
import org.jetbrains.kotlin.formver.core.isLaw
import org.jetbrains.kotlin.formver.locality.plugin.neverConvert
import org.jetbrains.kotlin.formver.locality.plugin.neverVerify
import org.jetbrains.kotlin.formver.core.shouldVerify
import org.jetbrains.kotlin.formver.core.viperProgram
import org.jetbrains.kotlin.formver.core.lambdaPrograms
import org.jetbrains.kotlin.formver.core.trust
import org.jetbrains.kotlin.formver.core.LambdaVerificationProgram
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.ReportedVerifierErrors
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.reportVerifierError
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.reportingPosition
import org.jetbrains.kotlin.formver.viper.SiliconFrontend
import org.jetbrains.kotlin.formver.viper.ast.Program
import org.jetbrains.kotlin.formver.viper.ast.registerAllNames
import org.jetbrains.kotlin.formver.viper.ast.unwrapOr
import org.jetbrains.kotlin.formver.viper.mangled
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Converts selected functions to Viper and submits their verification to [sink]. */
internal class ViperPoweredDeclarationChecker(
    private val session: FirSession,
    private val config: PluginConfiguration,
    private val verifierLocator: VerifierLocator,
    private val reportedErrors: ReportedVerifierErrors,
    private val sink: VerificationSink = ImmediateVerificationSink,
    private val processedLambdas: MutableSet<org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousFunctionSymbol> = mutableSetOf(),
) : FirFunctionChecker(MppCheckerKind.Common) {
    private val progress = VerificationProgress(config.messageCollector)

    context(context: CheckerContext)
    private fun shouldSkip(): Boolean =
        session.externalSpecifications?.errors?.isNotEmpty() == true ||
            config.externalSpecs.isExternalSpec(context.containingFilePath)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        if (shouldSkip()) return
        when (declaration) {
            is FirSimpleFunction -> {}
            is FirPropertyAccessor -> if (declaration.body == null || declaration.propertySymbol.isLocal) return
            else -> return
        }
        if (declaration.origin != FirDeclarationOrigin.Source ||
            declaration.source?.kind == KtFakeSourceElementKind.EnumGeneratedDeclaration
        ) return
        config.exclusion(declaration)?.let { reason ->
            recordOutcome(declaration, 0) { copy(conversion = "excluded", conversionReason = reason) }
            return
        }
        declaration.lambdaPrograms = emptyList()
        val written = declaration.writtenLambdas()
        val root = functionProgram(written)
        val dumps = mutableListOf<String>()
        reportConversionErrors(declaration, root, 0) {
            val pureGetter = declaration is FirPropertyAccessor && declaration.isGetter && declaration.symbol.isPure(session)
            if (declaration is FirPropertyAccessor && !pureGetter) root.registerAccessor(declaration)
            else root.register(declaration)
            convert(declaration, root, 0, dumps)
        }
        val converters = mutableListOf(root)
        root.specifiedLambdas.forEachIndexed { index, lambda ->
            if (!processedLambdas.add(lambda.function.symbol)) return@forEachIndexed
            val converter = functionProgram(written)
            converters += converter
            reportConversionErrors(declaration, converter, index + 1) {
                converter.registerLambda(lambda, root.specifiedLambdas)
                convert(declaration, converter, index + 1, dumps)
            }
        }
        convertLambdaValues(declaration, written, converters, root.specifiedLambdas.size + 1, dumps)
        if (dumps.isNotEmpty()) {
            reporter.reportOn(declaration.source, PluginErrors.VIPER_TEXT, declaration.verificationName, dumps.joinToString("\n\n"))
        }
    }

    /**
     * Converts the body of each lambda written in [declaration] that [converters] create as a function value into a
     * program of its own, numbered from [firstIndex] on; the lambda values written in [declaration] that those programs
     * create in turn follow.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun convertLambdaValues(
        declaration: FirFunction, written: Set<FirAnonymousFunction>, converters: List<ProgramConverter>,
        firstIndex: Int, dumps: MutableList<String>,
    ) {
        val pending = ArrayDeque(converters.flatMap { it.lambdaValues })
        var index = firstIndex
        while (pending.isNotEmpty()) {
            val function = pending.removeFirst()
            if (function !in written || !processedLambdas.add(function.symbol)) continue
            val converter = functionProgram(written)
            val current = index++
            reportConversionErrors(function, converter, current, owner = declaration) {
                converter.registerLambdaValueBody(function, declaration.symbol.embedName(converter), current - 1)
                convert(declaration, converter, current, dumps)
            }
            pending += converter.lambdaValues
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun functionProgram(written: Set<FirAnonymousFunction>): ProgramConverter =
        ProgramConverter(session, config, context, reporter).also { it.setSchedulableLambdaValues(written) }

    private fun FirFunction.writtenLambdas(): Set<FirAnonymousFunction> = buildSet {
        accept(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                if (element is FirAnonymousFunction) add(element)
                element.acceptChildren(this)
            }
        })
    }

    /** Converts the specified lambdas of [declaration] that no function contains, and returns their Viper dumps. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    fun standaloneLambdaDumps(declaration: FirDeclaration, scopeName: ScopedName): List<String> {
        if (shouldSkip()) return emptyList()
        if (config.conversionSelection != TargetsSelection.ALL_TARGETS || declaration.symbol.neverConvert(session)) return emptyList()
        val collector = ProgramConverter(session, config, context, reporter)
        val lambdas = with(collector) { SpecifiedLambda.collect(declaration, scopeName) }
        declaration.lambdaPrograms = emptyList()
        val dumps = mutableListOf<String>()
        lambdas.forEachIndexed { index, lambda ->
            if (!processedLambdas.add(lambda.function.symbol)) return@forEachIndexed
            val converter = ProgramConverter(session, config, context, reporter)
            reportConversionErrors(declaration, converter, index + 1) {
                converter.registerLambda(lambda, lambdas)
                convert(declaration, converter, index + 1, dumps)
            }
        }
        return dumps
    }

    /** Reports the Viper [dumps] of [declaration] as one diagnostic. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    fun reportViperText(declaration: FirDeclaration, dumps: List<String>) {
        if (dumps.isNotEmpty()) {
            reporter.reportOn(declaration.source, PluginErrors.VIPER_TEXT, declaration.verificationName, dumps.joinToString("\n\n"))
        }
    }

    private val FirDeclaration.verificationName: String
        get() = when (this) {
            is FirSimpleFunction -> name.asString()
            is FirPropertyAccessor -> symbol.propertySymbol.name.asString()
            is FirRegularClass -> name.asString()
            is FirProperty -> name.asString()
            else -> error("Unexpected verification owner: ${this::class.simpleName}")
        }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun convert(declaration: FirDeclaration, programConversionContext: ProgramConverter, index: Int, dumps: MutableList<String>) {
        programConversionContext.validateAll()
        if (shouldDumpExpEmbeddings(declaration)) {
            with(SimpleNameResolver()) {
                for ((name, embedding) in programConversionContext.debugExpEmbeddings) {
                    reporter.reportOn(declaration.source, PluginErrors.EXP_EMBEDDING, name.mangled, embedding.debugTreeView.print())
                }
            }
        }
        programConversionContext.firstConversionError?.let { failure ->
            recordOutcome(declaration, index) { stoppedBy(failure.kind.name, failure.reasonCode, failure.detail) }
            return
        }
        programConversionContext.linearizeAll()
        val program = programConversionContext.buildProgram()
        with(programConversionContext.nameResolver) { program.registerAllNames() }
        programConversionContext.nameResolver.resolve()
        getProgramForLogging(program)?.let {
            val heading = if (index == 0) "" else "${declaration.verificationName}.lambda${index - 1}.invoke:\n"
            dumps += heading + with(programConversionContext.nameResolver) { it.toDebugOutput() }
        }
        val viperProgram = with(programConversionContext.nameResolver) { program.toSilver() }
        SiliconFrontend.checkConsistency(viperProgram)
        val shouldVerify = config.shouldVerify(declaration) && !programConversionContext.skipsVerification
        val name = declaration.verificationLabel + if (index == 0) "" else ".lambda${index - 1}"
        if (System.getProperty("formver.testRun").toBoolean()) {
            // The test facade verifies the program later.
            val trusted = programConversionContext.trust
            if (index == 0) {
                declaration.viperProgram = viperProgram
                declaration.shouldVerify = shouldVerify
                declaration.trust = trusted
            } else {
                declaration.lambdaPrograms = declaration.lambdaPrograms.orEmpty() +
                    LambdaVerificationProgram(viperProgram, shouldVerify, trusted, name)
            }
        } else if (!shouldVerify) {
            val skip = programConversionContext.verificationSkipReason
            recordOutcome(declaration, index) {
                if (skip == null) copy(verification = "not_selected") else copy(verification = "skipped", verificationDetail = skip.name)
            }
        } else {
            verifyProgram(declaration, programConversionContext, index, viperProgram, name)
        }
    }

    /** Verifies [viperProgram], program [index] of [declaration], and reports and records the outcome. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun verifyProgram(
        declaration: FirDeclaration,
        programConversionContext: ProgramConverter,
        index: Int,
        viperProgram: viper.silver.ast.Program,
        name: String,
    ) {
        val z3Exe = verifierLocator.z3Exe { reason ->
            reporter.reportTool(declaration.source, PluginErrors.VERIFIER_UNAVAILABLE, "SOLVER_UNAVAILABLE", reason)
            recordOutcome(declaration, index) { copy(verification = "error", verificationDetail = "SOLVER_UNAVAILABLE: $reason") }
        } ?: return
        sink.submit(config.verificationRequest(viperProgram, z3Exe)) { obtainResult ->
            var elapsedNanos: Long? = null
            val start = System.nanoTime()
            fun seconds() = (elapsedNanos ?: (System.nanoTime() - start)) / NANOS_PER_SECOND
            try {
                val failedKinds = mutableListOf<String>()
                val exceptionCallees = mutableListOf<String>()
                val timeout = progress.run(name, declaration.source, { elapsedNanos }) {
                    val result = obtainResult()
                    elapsedNanos = result.elapsedNanos
                    result.errors.forEach { err ->
                        val source = err.reportingPosition(declaration.source).unwrapOr { declaration.source }
                        val failure = when (err) { is VerificationError -> err.toFailure(source) }
                        failedKinds += failure.kind.name
                        failure.exceptionCallee?.let { exceptionCallees += it }
                        if (reportedErrors.add(err, source)) reporter.reportVerifierError(source, err, config.errorStyle)
                    }
                    result.throwFailure()
                }
                val failed = failedKinds.isNotEmpty()
                if (timeout != null) reporter.reportFailure(timeout)
                else if (!failed) {
                    config.trustLedger?.record(context.containingFilePath!!, name, programConversionContext.trust)
                    if (index == 0) config.verifiedDeclarations?.record(declaration.symbol)
                }
                recordOutcome(declaration, index) {
                    when {
                        timeout != null -> copy(verification = "timeout")
                        failedKinds.isNotEmpty() -> copy(
                            verification = "failed",
                            verificationDetail = failedKinds.sorted().joinToString(","),
                            exceptionCallees = exceptionCallees.sorted().joinToString(","),
                        )
                        else -> copy(verification = "verified")
                    }.copy(seconds = seconds())
                }
            } catch (e: UnsupportedFeatureException) {
                recordOutcome(declaration, index) { copy(verification = "error", verificationDetail = "${e.reason.name}: ${e.message}", seconds = seconds()) }
                reporter.reportTranslation(reportingSource(e.source, declaration), ConversionErrors.UNSUPPORTED_FEATURE, e.message, e.reason.name)
                programConversionContext.skips.reportConversionStopped(declaration, KotlinCondition.SkipReason.CONVERSION_ERRORS)
            } catch (e: Exception) {
                val internal = (e as? SnaktInternalException) ?: e.asInternalErrorAt(declaration.source)
                recordOutcome(declaration, index) { copy(verification = "error", verificationDetail = "${internal.reason.name}: ${internal.message}", seconds = seconds()) }
                reportInternalError(internal, declaration, programConversionContext)
            } catch (e: NotImplementedError) {
                val internal = e.asInternalErrorAt(declaration.source)
                recordOutcome(declaration, index) { copy(verification = "error", verificationDetail = "${internal.reason.name}: ${internal.message}", seconds = seconds()) }
                reportInternalError(internal, declaration, programConversionContext)
            } catch (e: StackOverflowError) {
                val internal = e.asInternalErrorAt(declaration.source)
                recordOutcome(declaration, index) { copy(verification = "error", verificationDetail = "${internal.reason.name}: ${internal.message}", seconds = seconds()) }
                reportInternalError(internal, declaration, programConversionContext)
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportConversionErrors(
        declaration: FirDeclaration, converter: ProgramConverter, index: Int, owner: FirDeclaration = declaration, convert: () -> Unit,
    ) {
        try {
            convert()
        } catch (e: UnsupportedFeatureException) {
            recordOutcome(owner, index) { stoppedBy(FailureKind.UNSUPPORTED_FEATURE.name, e.reason.name, e.message) }
            reporter.reportTranslation(reportingSource(e.source, declaration), ConversionErrors.UNSUPPORTED_FEATURE, e.message, e.reason.name)
            converter.skips.reportConversionStopped(declaration, KotlinCondition.SkipReason.CONVERSION_ERRORS)
        } catch (e: SnaktInternalException) {
            recordInternalStop(owner, index, e)
            reportInternalError(e, declaration, converter)
        } catch (e: Exception) {
            val internal = e.asInternalErrorAt(declaration.source)
            recordInternalStop(owner, index, internal)
            reportInternalError(internal, declaration, converter)
        } catch (e: NotImplementedError) {
            val internal = e.asInternalErrorAt(declaration.source)
            recordInternalStop(owner, index, internal)
            reportInternalError(internal, declaration, converter)
        }
    }

    context(context: CheckerContext)
    private fun recordInternalStop(declaration: FirDeclaration, index: Int, e: SnaktInternalException) =
        recordOutcome(declaration, index) { stoppedBy(FailureKind.INTERNAL_ERROR.name, e.reason.name, e.message) }

    /** Writes the outcome of program [index] of [declaration] to the declaration report, when one is configured. */
    context(context: CheckerContext)
    private fun recordOutcome(declaration: FirDeclaration, index: Int, outcome: DeclarationReport.Row.() -> DeclarationReport.Row) {
        val report = config.declarationReport ?: return
        if (declaration is FirFunction && declaration.body == null) return
        @OptIn(SymbolInternals::class)
        val line = context.containingFileSymbol!!.fir.sourceFileLinesMapping!!.getLineByOffset(declaration.source!!.startOffset)
        val program = if (index == 0) "body" else "lambda${index - 1}"
        report.write(DeclarationReport.Row(
            context.containingFilePath!!, line + 1, declaration.verificationLabel, program, conversion = "converted",
        ).outcome())
    }

    private fun DeclarationReport.Row.stoppedBy(kind: String, reason: String, detail: String) =
        copy(conversion = "stopped", conversionKind = kind, conversionReason = reason, conversionDetail = detail)

    private val Failure.reasonCode: String
        get() = when (val condition = condition) {
            is KotlinCondition.Translation -> condition.reason
            is KotlinCondition.Tool -> condition.reason
            is KotlinCondition.SpecOf -> condition.rule.name
            is KotlinCondition.Exposes -> condition.rule.name
            is KotlinCondition.ExternalSpec -> condition.rule.name
            is KotlinCondition.Law -> condition.rule.name
            is KotlinCondition.Observer -> condition.rule.name
            is KotlinCondition.Skipped -> condition.reason.name
            is KotlinCondition.OpaqueCall -> condition.reason.name
            is PurityCondition -> condition.rule.name
            is TerminationCondition -> condition.rule.name
            is FoldOwnershipCondition -> condition.rule.name
            is ConversionOwnershipCondition.MissingFacts -> condition.rule.name
            is ConversionOwnershipCondition.Read -> condition.reason.name
            else -> condition::class.java.simpleName
        }

    /**
     * The `exception_callees` entry of this failure, `KIND:origin:callee`, when it is an escaping exception that a call
     * raises; see docs/configuration.md#declaration-reports.
     */
    private val Failure.exceptionCallee: String?
        get() {
            val escape = condition as? KotlinCondition.ExceptionEscape ?: return null
            return "${kind.name}:${escape.calleeOrigin ?: return null}:${escape.calleeName}"
        }

    private val Failure.detail: String
        get() = when (val condition = condition) {
            is KotlinCondition.Translation -> condition.detail
            is KotlinCondition.Tool -> condition.detail
            else -> FailureToFirRenderer.message(this, ErrorStyle.USER_FRIENDLY)
        }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportInternalError(e: SnaktInternalException, declaration: FirDeclaration, converter: ProgramConverter) {
        reporter.reportInternalFailure(reportingSource(e.source, declaration), e)
        converter.skips.reportConversionStopped(declaration, KotlinCondition.SkipReason.INTERNAL_ERROR)
        config.messageCollector.report(CompilerMessageSeverity.LOGGING, e.stackTraceToString())
    }

    /**
     * Where to report an error that stopped the conversion of [declaration]. The error may come from a callee, a class or
     * a library declaration that the conversion embeds; it is reported at [source] only when that lies inside
     * [declaration], so that the function whose conversion stopped always carries it.
     */
    private fun reportingSource(source: KtSourceElement?, declaration: FirDeclaration): KtSourceElement? {
        val outer = declaration.source ?: return source
        return source?.takeIf { it.isWithin(outer) } ?: outer
    }

    private fun KtSourceElement.isWithin(outer: KtSourceElement): Boolean {
        val sameFile = when {
            this is KtPsiSourceElement && outer is KtPsiSourceElement -> psi.containingFile == outer.psi.containingFile
            this is KtLightSourceElement && outer is KtLightSourceElement -> treeStructure === outer.treeStructure
            else -> false
        }
        return sameFile && startOffset >= outer.startOffset && endOffset <= outer.endOffset
    }

    private fun getProgramForLogging(program: Program): Program? = when (config.logLevel) {
        LogLevel.ONLY_WARNINGS -> null
        LogLevel.SHORT_VIPER_DUMP -> program.toShort().withoutPredicates()
        LogLevel.SHORT_VIPER_DUMP_WITH_PREDICATES -> program.toShort()
        LogLevel.FULL_VIPER_DUMP -> program
    }

    private fun getAnnotationId(name: String): ClassId =
        ClassId(FqName.fromSegments(listOf("org", "jetbrains", "kotlin", "formver", "plugin")), Name.identifier(name))

    private val dumpExpEmbeddingsId: ClassId = getAnnotationId("DumpExpEmbeddings")

    /** Why the source function [declaration] is not converted, or null when it is. */
    private fun PluginConfiguration.exclusion(declaration: FirFunction): String? = when {
        declaration.symbol.neverConvert(session) -> "NEVER_CONVERT"
        declaration.hasAnnotation(specOfId, session) -> "SPEC_OF"
        declaration.symbol.isLaw(session) -> "LAW"
        conversionSelection != TargetsSelection.ALL_TARGETS -> "NOT_SELECTED"
        else -> null
    }

    private fun PluginConfiguration.shouldVerify(declaration: FirDeclaration): Boolean = when {
        declaration.symbol.neverVerify(session) -> false
        else -> verificationSelection == TargetsSelection.ALL_TARGETS
    }

    private fun shouldDumpExpEmbeddings(declaration: FirDeclaration): Boolean =
        declaration.hasAnnotation(dumpExpEmbeddingsId, session)

    private companion object {
        const val NANOS_PER_SECOND = 1e9
    }
}
