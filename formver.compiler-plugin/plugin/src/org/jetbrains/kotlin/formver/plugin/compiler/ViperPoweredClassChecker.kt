/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.formver.core.trust
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.core.conversion.constructionFailures
import org.jetbrains.kotlin.formver.core.conversion.constructionVerdicts
import org.jetbrains.kotlin.formver.core.conversion.hasSuperclassLaws
import org.jetbrains.kotlin.formver.core.conversion.ownClassLaws
import org.jetbrains.kotlin.formver.core.diagnostics.ConversionErrors
import org.jetbrains.kotlin.formver.common.reportTranslation
import org.jetbrains.kotlin.formver.common.reportTool
import org.jetbrains.kotlin.formver.common.reportInternalFailure
import org.jetbrains.kotlin.formver.core.names.embedName
import org.jetbrains.kotlin.formver.locality.plugin.neverVerify
import org.jetbrains.kotlin.formver.core.shouldVerify
import org.jetbrains.kotlin.formver.core.viperProgram
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.ReportedVerifierErrors
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.reportVerifierError
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.reportingPosition
import org.jetbrains.kotlin.formver.viper.ast.unwrapOr

/** Checks concrete classes' constructors, laws, and inherited member bodies and contracts. */
internal class ViperPoweredClassChecker(
    private val session: FirSession,
    private val config: PluginConfiguration,
    private val verifierLocator: VerifierLocator,
    private val reportedErrors: ReportedVerifierErrors,
    private val sink: VerificationSink = ImmediateVerificationSink,
    processedLambdas: MutableSet<org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousFunctionSymbol> = mutableSetOf(),
) : FirRegularClassChecker(MppCheckerKind.Common) {
    private val lambdaChecker = ViperPoweredDeclarationChecker(session, config, verifierLocator, reportedErrors, sink, processedLambdas)
    private val programs = ClassPrograms(session, config)
    private val progress = VerificationProgress(config.messageCollector)

    context(context: CheckerContext)
    private fun shouldSkip(declaration: FirRegularClass): Boolean =
        programs.isSkipped(declaration, context.containingFilePath)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportFailure(declaration: FirRegularClass, error: org.jetbrains.kotlin.formver.viper.errors.VerifierError) {
        val source = error.reportingPosition(declaration.source).unwrapOr { declaration.source }
        if (reportedErrors.add(error, source)) reporter.reportVerifierError(source, error, config.errorStyle)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportConstructionDiscipline(declaration: FirRegularClass) {
        if (declaration.classKind.isInterface ||
            config.conversionSelection in setOf(TargetsSelection.NO_TARGETS, TargetsSelection.FORCE_DISABLE)) return
        if (declaration.symbol.ownClassLaws(session).isNotEmpty() || declaration.symbol.hasSuperclassLaws(session)) return
        if (declaration.symbol.neverVerify(session)) return
        constructionFailures(declaration.symbol, session).forEach {
            reporter.reportFailure(Failure(FailureKind.VERIFICATION_SKIPPED, declaration.source, it))
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        if (shouldSkip(declaration)) return
        val dumps = lambdaChecker.standaloneLambdaDumps(declaration, declaration.symbol.classId.embedName()).toMutableList()
        reportConstructionDiscipline(declaration)
        programs.implementationObligations(declaration)?.let { checkObligations(declaration, it, dumps) }
        lambdaChecker.reportViperText(declaration, dumps)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkObligations(declaration: FirRegularClass, obligations: ClassObligations, dumps: MutableList<String>) {
        val verdicts = checkNotNull(session.constructionVerdicts) { "the plugin registers construction verdicts" }
        verdicts.decide(declaration.symbol) {
            try {
                val program = programs.convert(declaration, obligations, dumps) ?: return@decide false
                if (System.getProperty("formver.testRun").toBoolean()) {
                    declaration.viperProgram = program.program
                    declaration.shouldVerify = program.verify
                    declaration.trust = program.trust
                } else if (program.verify) {
                    verify(declaration, program)
                }
                program.verifiesConstruction
            } catch (error: UnsupportedFeatureException) {
                reporter.reportTranslation(declaration.source, ConversionErrors.UNSUPPORTED_FEATURE, error.message, error.reason.name)
                false
            } catch (error: Exception) {
                reportInternalError(declaration, error)
                false
            } catch (error: NotImplementedError) {
                reportInternalError(declaration, error)
                false
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun verify(declaration: FirRegularClass, program: ClassProgram) {
        val z3 = verifierLocator.z3Exe { reason ->
            reporter.reportTool(declaration.source, PluginErrors.VERIFIER_UNAVAILABLE, "SOLVER_UNAVAILABLE", reason)
        } ?: return
        sink.submit(config.verificationRequest(program.program, z3)) { obtainResult ->
            try {
                var failed = false
                var elapsedNanos: Long? = null
                val timeout = progress.run(declaration.verificationLabel, declaration.source, { elapsedNanos }) {
                    val result = obtainResult()
                    elapsedNanos = result.elapsedNanos
                    result.errors.forEach { error ->
                        failed = true
                        reportFailure(declaration, error)
                    }
                    result.throwFailure()
                }
                if (timeout != null) reporter.reportFailure(timeout)
                else if (!failed) {
                    config.trustLedger?.record(context.containingFilePath!!, declaration.verificationLabel, { program.trust.semanticOptIns }) {
                        program.trust.assumptions
                    }
                    config.verifiedDeclarations?.record(declaration.symbol)
                }
            } catch (error: UnsupportedFeatureException) {
                reporter.reportTranslation(declaration.source, ConversionErrors.UNSUPPORTED_FEATURE, error.message, error.reason.name)
            } catch (error: Exception) {
                reportInternalError(declaration, error)
            } catch (error: NotImplementedError) {
                reportInternalError(declaration, error)
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportInternalError(declaration: FirRegularClass, error: Throwable) {
        reporter.reportInternalFailure(declaration.source,
            (error as? org.jetbrains.kotlin.formver.common.SnaktInternalException) ?: error.asInternalErrorAt(declaration.source))
        config.messageCollector.report(org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.LOGGING, error.stackTraceToString())
    }
}
