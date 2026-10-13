/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.formver.core.conversion.InheritedImplementationObligation
import org.jetbrains.kotlin.formver.core.conversion.inheritedImplementationObligations
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.core.conversion.classLawConstructionFailures
import org.jetbrains.kotlin.formver.core.conversion.hasSuperclassLaws
import org.jetbrains.kotlin.formver.core.conversion.ownClassLaws
import org.jetbrains.kotlin.formver.core.conversion.ProgramConverter
import org.jetbrains.kotlin.formver.core.conversion.interfaceLaws
import org.jetbrains.kotlin.formver.core.conversion.registerClassLaws
import org.jetbrains.kotlin.formver.core.diagnostics.ConversionErrors
import org.jetbrains.kotlin.formver.common.reportTranslation
import org.jetbrains.kotlin.formver.common.reportTool
import org.jetbrains.kotlin.formver.common.reportInternalFailure
import org.jetbrains.kotlin.formver.core.isLaw
import org.jetbrains.kotlin.formver.core.names.embedName
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.formver.core.shouldVerify
import org.jetbrains.kotlin.formver.core.viperProgram
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.ReportedVerifierErrors
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.reportVerifierError
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.reportingPosition
import org.jetbrains.kotlin.formver.viper.SiliconFrontend
import org.jetbrains.kotlin.formver.viper.ast.registerAllNames
import org.jetbrains.kotlin.formver.viper.ast.unwrapOr

/** Checks concrete implementations' laws and inherited member bodies and contracts. */
class ViperPoweredClassChecker(
    private val session: FirSession,
    private val config: PluginConfiguration,
    private val verifierLocator: VerifierLocator,
    private val reportedErrors: ReportedVerifierErrors,
) : FirRegularClassChecker(MppCheckerKind.Common) {
    private val lambdaChecker = ViperPoweredDeclarationChecker(session, config, verifierLocator, reportedErrors)

    context(context: CheckerContext)
    private fun shouldSkip(declaration: FirRegularClass): Boolean =
        session.externalSpecifications?.errors?.isNotEmpty() == true ||
            config.externalSpecs.isExternalSpec(context.containingFilePath) ||
            declaration.origin != FirDeclarationOrigin.Source

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportFailure(declaration: FirRegularClass, error: org.jetbrains.kotlin.formver.viper.errors.VerifierError) {
        val source = error.reportingPosition(declaration.source).unwrapOr { declaration.source }
        if (reportedErrors.add(error, source)) reporter.reportVerifierError(source, error, config.errorStyle)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        if (shouldSkip(declaration)) return
        lambdaChecker.checkStandaloneLambdas(declaration, declaration.symbol.classId.embedName())
        val obligations = implementationObligations(declaration) ?: return
        try {
            val (program, verify) = convert(declaration, obligations) ?: return
            if (System.getProperty("formver.testRun").toBoolean()) {
                declaration.viperProgram = program
                declaration.shouldVerify = verify
            } else if (verify) {
                verify(declaration, program)
            }
        } catch (error: UnsupportedFeatureException) {
            reporter.reportTranslation(declaration.source, ConversionErrors.UNSUPPORTED_FEATURE, error.message, error.reason.name)
        } catch (error: Exception) {
            reportInternalError(declaration, error)
        } catch (error: NotImplementedError) {
            reportInternalError(declaration, error)
        }
    }

    private data class Obligations(
        val primary: FirConstructorSymbol?,
        val inherited: List<InheritedImplementationObligation>,
    )

    /** What a concrete class must prove, or null when it has nothing to prove or its laws cannot be constructed. */
    context(context: CheckerContext)
    @OptIn(DirectDeclarationsAccess::class)
    private fun implementationObligations(declaration: FirRegularClass): Obligations? {
        if (declaration.classKind.isInterface || declaration.isAbstract) return null
        if (config.conversionSelection in setOf(TargetsSelection.NO_TARGETS, TargetsSelection.FORCE_DISABLE)) return null
        val symbol = declaration.symbol
        val hasClassLaws = symbol.ownClassLaws(session).isNotEmpty() || symbol.hasSuperclassLaws(session)
        val inherited = symbol.inheritedImplementationObligations()
        val primary = symbol.declarationSymbols.filterIsInstance<FirConstructorSymbol>()
            .singleOrNull { it.isPrimary }?.takeIf { hasClassLaws }
        val hasLawHarness = symbol.interfaceLaws().isNotEmpty() || symbol.declarationSymbols.any { it.isLaw(session) }
        return when {
            primary == null && inherited.isEmpty() && !hasLawHarness -> null
            hasClassLaws && classLawConstructionFailures(symbol, session).isNotEmpty() -> null
            else -> Obligations(primary, inherited)
        }
    }

    /** The checked Viper program for [obligations] and whether to verify it, or null after a conversion error. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun convert(declaration: FirRegularClass, obligations: Obligations): Pair<viper.silver.ast.Program, Boolean>? {
        val converter = ProgramConverter(session, config, context, reporter)
        converter.registerClassLaws(declaration.symbol)
        obligations.primary?.let { converter.registerPrimaryConstructor(it) }
        for (obligation in obligations.inherited) {
            converter.registerInheritedImplementation(declaration.symbol, obligation.implementation, obligation.contracts)
        }
        converter.validateAll()
        if (converter.hadConversionError) return null
        converter.linearizeAll()
        val program = converter.buildProgram()
        with(converter.nameResolver) { program.registerAllNames() }
        converter.nameResolver.resolve()
        val dump = when (config.logLevel) {
            LogLevel.ONLY_WARNINGS -> null
            LogLevel.SHORT_VIPER_DUMP -> program.toShort().withoutPredicates()
            LogLevel.SHORT_VIPER_DUMP_WITH_PREDICATES -> program.toShort()
            LogLevel.FULL_VIPER_DUMP -> program
        }
        dump?.let {
            reporter.reportOn(declaration.source, PluginErrors.VIPER_TEXT, declaration.name.asString(),
                with(converter.nameResolver) { it.toDebugOutput() })
        }
        val viperProgram = with(converter.nameResolver) { program.toSilver() }
        SiliconFrontend.checkConsistency(viperProgram)
        val verify = config.verificationSelection !in setOf(TargetsSelection.NO_TARGETS, TargetsSelection.FORCE_DISABLE) &&
            !converter.skipsVerification
        return viperProgram to verify
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun verify(declaration: FirRegularClass, program: viper.silver.ast.Program) {
        val z3 = verifierLocator.z3Exe { reason ->
            reporter.reportTool(declaration.source, PluginErrors.VERIFIER_UNAVAILABLE, "SOLVER_UNAVAILABLE", reason)
        } ?: return
        SiliconFrontend(
            z3, cacheDirectory = config.verificationCacheDirectory?.let { java.nio.file.Path.of(it) },
            pluginOptions = config.verificationCacheOptions, counterexamples = config.counterexamples,
        ).use { verifier ->
            verifier.verify(program) { error -> reportFailure(declaration, error) }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportInternalError(declaration: FirRegularClass, error: Throwable) {
        reporter.reportInternalFailure(declaration.source,
            (error as? org.jetbrains.kotlin.formver.common.SnaktInternalException) ?: error.asInternalErrorAt(declaration.source))
        config.messageCollector.report(org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.LOGGING, error.stackTraceToString())
    }
}
