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
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.core.conversion.classLawConstructionFailures
import org.jetbrains.kotlin.formver.core.conversion.ownClassLaws
import org.jetbrains.kotlin.formver.core.conversion.ProgramConverter
import org.jetbrains.kotlin.formver.core.conversion.interfaceLaws
import org.jetbrains.kotlin.formver.core.conversion.registerClassLaws
import org.jetbrains.kotlin.formver.core.diagnostics.ConversionErrors
import org.jetbrains.kotlin.formver.core.isLaw
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.formver.core.shouldVerify
import org.jetbrains.kotlin.formver.core.viperProgram
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.ReportedVerifierErrors
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.reportVerifierError
import org.jetbrains.kotlin.formver.viper.SiliconFrontend
import org.jetbrains.kotlin.formver.viper.ast.registerAllNames
import org.jetbrains.kotlin.formver.viper.ast.unwrapOr

/** Checks the representation and inherited interface laws at each source implementation. */
class ViperPoweredClassChecker(
    private val session: FirSession,
    private val config: PluginConfiguration,
    private val verifierLocator: VerifierLocator,
    private val reportedErrors: ReportedVerifierErrors,
) : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    @OptIn(DirectDeclarationsAccess::class)
    override fun check(declaration: FirRegularClass) {
        if (declaration.origin != FirDeclarationOrigin.Source || declaration.classKind.isInterface) return
        if (config.conversionSelection in setOf(TargetsSelection.NO_TARGETS, TargetsSelection.FORCE_DISABLE) ||
            (declaration.symbol.interfaceLaws().isEmpty() &&
                declaration.symbol.declarationSymbols.none { it.isLaw(session) })) return
        if (declaration.symbol.ownClassLaws(session).isNotEmpty() &&
            classLawConstructionFailures(declaration.symbol, session).isNotEmpty()) return
        try {
            val converter = ProgramConverter(session, config, context, reporter)
            converter.registerClassLaws(declaration.symbol)
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
                    reporter.reportOn(declaration.source, PluginErrors.VERIFIER_UNAVAILABLE, reason)
                } ?: return
                SiliconFrontend(z3).use { verifier ->
                    verifier.verify(viperProgram) { error ->
                        val source = error.position.unwrapOr { declaration.source }
                        if (reportedErrors.add(error, source)) reporter.reportVerifierError(source, error, config.errorStyle)
                    }
                }
            }
        } catch (error: UnsupportedFeatureException) {
            reporter.reportOn(declaration.source, ConversionErrors.UNSUPPORTED_FEATURE, error.message)
        } catch (error: Exception) {
            reporter.reportOn(declaration.source, PluginErrors.INTERNAL_ERROR, error.asInternalErrorAt(declaration.source).message)
            config.messageCollector.report(org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.LOGGING, error.stackTraceToString())
        } catch (error: NotImplementedError) {
            reporter.reportOn(declaration.source, PluginErrors.INTERNAL_ERROR, error.asInternalErrorAt(declaration.source).message)
            config.messageCollector.report(org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.LOGGING, error.stackTraceToString())
        }
    }
}
