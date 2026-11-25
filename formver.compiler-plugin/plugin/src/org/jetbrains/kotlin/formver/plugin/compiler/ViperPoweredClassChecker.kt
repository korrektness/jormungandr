/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.core.conversion.ProgramConverter
import org.jetbrains.kotlin.formver.core.conversion.constructionFailures
import org.jetbrains.kotlin.formver.core.conversion.constructionVerdicts
import org.jetbrains.kotlin.formver.core.conversion.hasSuperclassLaws
import org.jetbrains.kotlin.formver.core.conversion.ownClassLaws
import org.jetbrains.kotlin.formver.core.conversion.verificationSkipReason
import org.jetbrains.kotlin.formver.core.diagnostics.ConversionErrors
import org.jetbrains.kotlin.formver.core.names.embedName
import org.jetbrains.kotlin.formver.core.shouldVerify
import org.jetbrains.kotlin.formver.core.trust
import org.jetbrains.kotlin.formver.core.viperProgram
import org.jetbrains.kotlin.formver.locality.plugin.neverVerify
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.ReportedVerifierErrors

/** Checks concrete classes' constructors, laws, and inherited member bodies and contracts. */
internal class ViperPoweredClassChecker(
    private val session: FirSession,
    private val config: PluginConfiguration,
    verifierLocator: VerifierLocator,
    reportedErrors: ReportedVerifierErrors,
    sink: VerificationSink = ImmediateVerificationSink,
    processedLambdas: MutableSet<org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousFunctionSymbol> = mutableSetOf(),
    private val useVerificationFacade: Boolean = System.getProperty("formver.testRun").toBoolean(),
) : FirRegularClassChecker(MppCheckerKind.Common) {
    private val checker = ViperPoweredDeclarationChecker(session, config, verifierLocator, reportedErrors, sink, processedLambdas, useVerificationFacade)
    private val programs = ClassPrograms(session, config)

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
        if (programs.isSkipped(declaration, context.containingFilePath)) return
        val dumps = checker.standaloneLambdaDumps(declaration, declaration.symbol.classId.embedName()).toMutableList()
        reportConstructionDiscipline(declaration)
        programs.implementationObligations(declaration)?.let { checkObligations(declaration, it, dumps) }
        checker.reportViperText(declaration, dumps)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkObligations(declaration: FirRegularClass, obligations: ClassObligations, dumps: MutableList<String>) {
        val verdicts = checkNotNull(session.constructionVerdicts) { "the plugin registers construction verdicts" }
        verdicts.decide(declaration.symbol) {
            val site = config.programReportSite(declaration, inherited = obligations.inherited)
            val (converter, program) = try {
                val converter = ProgramConverter(session, config, context, reporter)
                converter to programs.convert(declaration, obligations, dumps, converter, trackBodies = site?.tracksBodies == true)
            } catch (error: UnsupportedFeatureException) {
                reporter.reportTranslation(declaration.source, ConversionErrors.UNSUPPORTED_FEATURE, error.message, error.reason.name)
                checker.recordTranslationStop(site, error)
                return@decide false
            } catch (error: Exception) {
                reportInternalConversionError(declaration, error, site)
                return@decide false
            } catch (error: NotImplementedError) {
                reportInternalConversionError(declaration, error, site)
                return@decide false
            } catch (error: Throwable) {
                if (config.declarationReport == null) throw error
                error.rethrowAfterRecording {
                    checker.recordInternalStop(site, error.asInternalErrorAt(declaration.source))
                }
            }
            if (program == null) {
                checker.recordConversionStop(site, checkNotNull(converter.firstConversionError))
                return@decide false
            }
            if (useVerificationFacade) {
                declaration.viperProgram = program.program
                declaration.shouldVerify = program.verify
                declaration.trust = program.trust
            } else {
                val emittedSite = site?.withEmittedBodies(program.emittedBodies)
                if (program.verify) {
                    if (!checker.verifyProgram(declaration, converter, 0, program.program, declaration.verificationLabel, emittedSite)) {
                        return@decide false
                    }
                } else {
                    val skip = converter.verificationSkipReason
                    emittedSite?.record {
                        if (skip == null) copy(verification = "not_selected") else copy(verification = "skipped", verificationDetail = skip.name)
                    }
                }
            }
            program.verifiesConstruction
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportInternalConversionError(declaration: FirRegularClass, error: Throwable, site: ProgramReportSite?) {
        val internal = (error as? SnaktInternalException) ?: error.asInternalErrorAt(declaration.source)
        reporter.reportInternalFailure(declaration.source, internal)
        config.messageCollector.report(CompilerMessageSeverity.LOGGING, error.stackTraceToString())
        checker.recordInternalStop(site, internal)
    }
}

/** Records a fatal outcome without letting a reporting failure replace the fatal error. */
internal inline fun Throwable.rethrowAfterRecording(record: () -> Unit): Nothing {
    try {
        record()
    } catch (reportFailure: Throwable) {
        if (reportFailure !== this) addSuppressed(reportFailure)
    }
    throw this
}
