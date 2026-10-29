/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.formver.common.ExternalSpecRule
import org.jetbrains.kotlin.formver.common.Failure
import org.jetbrains.kotlin.formver.common.FailureKind
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.common.PluginConfiguration
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.common.TargetsSelection
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.common.asInternalErrorAt
import org.jetbrains.kotlin.formver.common.externalSpecifications
import org.jetbrains.kotlin.formver.common.reportFailure
import org.jetbrains.kotlin.formver.common.reportInternalFailure
import org.jetbrains.kotlin.formver.common.reportTool
import org.jetbrains.kotlin.formver.common.reportTranslation
import org.jetbrains.kotlin.formver.core.conversion.ProgramConverter
import org.jetbrains.kotlin.formver.core.conversion.registerVacuityProbes
import org.jetbrains.kotlin.formver.core.conversion.SpecVacuityPart
import org.jetbrains.kotlin.formver.core.conversion.SpecVacuityProbe
import org.jetbrains.kotlin.formver.core.diagnostics.ConversionErrors
import org.jetbrains.kotlin.formver.core.shouldVerify
import org.jetbrains.kotlin.formver.core.viperProgram
import org.jetbrains.kotlin.formver.viper.SiliconFrontend
import org.jetbrains.kotlin.formver.viper.ast.Info
import org.jetbrains.kotlin.formver.viper.ast.registerAllNames
import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import viper.silver.plugin.standard.refute.Refute

/**
 * Checks the specifications of each user-supplied external specification file for contradictions. A contradictory
 * precondition, postcondition or set of laws lets every proof that assumes it succeed, so each one is an error on its
 * model.
 */
@OptIn(DirectDeclarationsAccess::class)
internal class SpecVacuityChecker(
    private val session: FirSession,
    private val config: PluginConfiguration,
    private val verifierLocator: VerifierLocator,
    private val sink: VerificationSink = ImmediateVerificationSink,
) : FirFileChecker(MppCheckerKind.Common) {
    private val progress = VerificationProgress(config.messageCollector)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFile) {
        val registry = session.externalSpecifications ?: return
        val path = declaration.sourceFile?.path ?: return
        if (!config.externalSpecs.isExternalSpec(path) || config.externalSpecs.isBundled(path)) return
        // A binding error suspends every binding, and the checkers report it on its model.
        if (registry.errors.isNotEmpty()) return
        if (config.conversionSelection != TargetsSelection.ALL_TARGETS ||
            config.verificationSelection != TargetsSelection.ALL_TARGETS) return
        val models = declaration.declarations.flatMap(::declarationsOf).map { it.symbol }.filter(registry::isModel)
        if (models.isEmpty()) return
        val program = probeProgram(declaration, models) ?: return
        if (System.getProperty("formver.testRun").toBoolean()) {
            // The test facade verifies the program later.
            declaration.viperProgram = program
            declaration.shouldVerify = true
        } else {
            verify(declaration, program)
        }
    }

    /** The program of the vacuity probes of [models], or null when converting them reported an error. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun probeProgram(file: FirFile, models: List<FirBasedSymbol<*>>): viper.silver.ast.Program? = try {
        val converter = ProgramConverter(session, config, context, reporter)
        models.forEach(converter::registerVacuityProbes)
        converter.validateAll()
        if (converter.hadConversionError) null else {
            converter.linearizeAll()
            val program = converter.buildProgram()
            with(converter.nameResolver) { program.registerAllNames() }
            converter.nameResolver.resolve()
            with(converter.nameResolver) { program.toSilver() }.also(SiliconFrontend::checkConsistency)
        }
    } catch (e: UnsupportedFeatureException) {
        reporter.reportTranslation(e.source ?: file.source, ConversionErrors.UNSUPPORTED_FEATURE, e.message, e.reason.name)
        null
    } catch (e: SnaktInternalException) {
        reporter.reportInternalFailure(e.source ?: file.source, e)
        null
    } catch (e: Exception) {
        reporter.reportInternalFailure(file.source, e.asInternalErrorAt(file.source))
        null
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun verify(file: FirFile, program: viper.silver.ast.Program) {
        val z3Exe = verifierLocator.z3Exe { reason ->
            reporter.reportTool(file.source, PluginErrors.VERIFIER_UNAVAILABLE, "SOLVER_UNAVAILABLE", reason)
        } ?: return
        sink.submit(config.verificationRequest(program, z3Exe, counterexamples = false)) { obtainResult ->
            var result = VerificationResult(emptyList())
            val timeout = progress.run(file.verificationLabel, file.source, { result.elapsedNanos }) {
                result = obtainResult()
                result.throwFailure()
            }
            val failures = if (timeout != null) listOf(timeout) else specVacuityFailures(result.errors, file.source)
            failures.forEach { reporter.reportFailure(it) }
        }
    }

    private fun declarationsOf(declaration: FirDeclaration): List<FirDeclaration> = when (declaration) {
        is FirSimpleFunction, is FirProperty -> listOf(declaration)
        is FirRegularClass -> listOf(declaration) + declaration.declarations.flatMap(::declarationsOf)
        else -> emptyList()
    }
}

/**
 * The failures that [errors], from the verification of a specification file's vacuity probes, report. A contradictory
 * precondition also fails the probe of its postcondition, which is left out.
 */
fun specVacuityFailures(errors: List<VerifierError>, file: KtSourceElement?): List<Failure> {
    val failures = errors.map { specVacuityFailure(it, file) }
    fun Failure.rule() = (condition as? KotlinCondition.ExternalSpec)?.rule
    val contradictoryPreconditions = failures.filter { it.rule() == ExternalSpecRule.VACUOUS_PRECONDITION }
        .mapTo(HashSet()) { (it.condition as KotlinCondition.ExternalSpec).target }
    return failures.filterNot {
        it.rule() == ExternalSpecRule.VACUOUS_POSTCONDITION &&
            (it.condition as KotlinCondition.ExternalSpec).target in contradictoryPreconditions
    }
}

/**
 * The failure that [error] reports. Every refutation in a probe program is a probe, so any other error is SnaKt's own
 * and is reported at [file].
 */
private fun specVacuityFailure(error: VerifierError, file: KtSourceElement?): Failure {
    val node = (error as? VerificationError)?.result?.offendingNode() as? Refute
    val probe = node?.let { (Info.fromSilver(it.info()) as? Info.Wrapped)?.info as? SpecVacuityProbe }
        ?: return Failure(FailureKind.INTERNAL_ERROR, file,
            KotlinCondition.Tool("INVARIANT_BROKEN", "A specification vacuity probe failed outside its refutations: ${error.msg}"))
    val rule = when (probe.part) {
        SpecVacuityPart.PRECONDITION -> ExternalSpecRule.VACUOUS_PRECONDITION
        SpecVacuityPart.POSTCONDITION -> ExternalSpecRule.VACUOUS_POSTCONDITION
        SpecVacuityPart.LAWS -> ExternalSpecRule.VACUOUS_LAWS
    }
    return Failure(FailureKind.EXTERNAL_SPEC_VACUOUS, probe.model.source, KotlinCondition.ExternalSpec(rule, probe.model.modelName))
}

private val FirBasedSymbol<*>.modelName: String
    get() = when (this) {
        is FirClassLikeSymbol<*> -> classId.asSingleFqName().asString()
        is FirCallableSymbol<*> -> callableId?.asSingleFqName()?.asString() ?: toString()
        else -> toString()
    }
