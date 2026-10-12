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

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportFailure(declaration: FirRegularClass, error: org.jetbrains.kotlin.formver.viper.errors.VerifierError) {
        val source = error.reportingPosition(declaration.source).unwrapOr { declaration.source }
        if (reportedErrors.add(error, source)) reporter.reportVerifierError(source, error, config.errorStyle)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    @OptIn(DirectDeclarationsAccess::class)
    override fun check(declaration: FirRegularClass) {
        if (declaration.origin != FirDeclarationOrigin.Source) return
        lambdaChecker.checkStandaloneLambdas(declaration, declaration.symbol.classId.embedName())
        if (declaration.classKind.isInterface || declaration.isAbstract) return
        if (config.conversionSelection in setOf(TargetsSelection.NO_TARGETS, TargetsSelection.FORCE_DISABLE)) return
        val inherited = declaration.symbol.inheritedImplementationObligations()
        val primary = declaration.symbol.declarationSymbols.filterIsInstance<FirConstructorSymbol>()
            .singleOrNull { it.isPrimary }?.takeIf {
                declaration.symbol.ownClassLaws(session).isNotEmpty() || declaration.symbol.hasSuperclassLaws(session)
            }
        val hasLawHarness = declaration.symbol.interfaceLaws().isNotEmpty() ||
            declaration.symbol.declarationSymbols.any { it.isLaw(session) }
        if (primary == null && inherited.isEmpty() && !hasLawHarness) return
        if ((declaration.symbol.ownClassLaws(session).isNotEmpty() || declaration.symbol.hasSuperclassLaws(session)) &&
            classLawConstructionFailures(declaration.symbol, session).isNotEmpty()) return
        try {
            val converter = ProgramConverter(session, config, context, reporter)
            converter.registerClassLaws(declaration.symbol)
            primary?.let { converter.registerPrimaryConstructor(it) }
            for (obligation in inherited) {
                converter.registerInheritedImplementation(declaration.symbol, obligation.implementation, obligation.contracts)
            }
            converter.validateAll()
            if (converter.hadConversionError) return
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
            if (System.getProperty("formver.testRun").toBoolean()) {
                declaration.viperProgram = viperProgram
                declaration.shouldVerify = verify
            } else if (verify) {
                val z3 = verifierLocator.z3Exe { reason ->
                    reporter.reportTool(declaration.source, PluginErrors.VERIFIER_UNAVAILABLE, "SOLVER_UNAVAILABLE", reason)
                } ?: return
                SiliconFrontend(
                    z3, cacheDirectory = config.verificationCacheDirectory?.let { java.nio.file.Path.of(it) },
                    pluginOptions = config.verificationCacheOptions,
                ).use { verifier ->
                    verifier.verify(viperProgram) { error -> reportFailure(declaration, error) }
                }
            }
        } catch (error: UnsupportedFeatureException) {
            reporter.reportTranslation(declaration.source, ConversionErrors.UNSUPPORTED_FEATURE, error.message, error.reason.name)
        } catch (error: Exception) {
            reporter.reportInternalFailure(declaration.source,
                (error as? org.jetbrains.kotlin.formver.common.SnaktInternalException) ?: error.asInternalErrorAt(declaration.source))
            config.messageCollector.report(org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.LOGGING, error.stackTraceToString())
        } catch (error: NotImplementedError) {
            reporter.reportInternalFailure(declaration.source, error.asInternalErrorAt(declaration.source))
            config.messageCollector.report(org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.LOGGING, error.stackTraceToString())
        }
    }
}
