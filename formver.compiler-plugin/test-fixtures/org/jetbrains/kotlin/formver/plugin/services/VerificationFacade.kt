package org.jetbrains.kotlin.formver.plugin.services

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.InternalDiagnosticFactoryMethod
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitorVoid
import org.jetbrains.kotlin.formver.common.services.runChecks
import org.jetbrains.kotlin.formver.core.shouldVerify
import org.jetbrains.kotlin.formver.core.viperProgram
import org.jetbrains.kotlin.formver.core.lambdaPrograms
import org.jetbrains.kotlin.formver.core.trust
import org.jetbrains.kotlin.formver.plugin.compiler.VerificationErrors
import org.jetbrains.kotlin.formver.plugin.compiler.VerificationProgress
import org.jetbrains.kotlin.formver.plugin.compiler.verificationLabel
import org.jetbrains.kotlin.formver.plugin.compiler.specVacuityFailures
import org.jetbrains.kotlin.formver.common.PluginConfiguration.Companion.DEFAULT_VERIFICATION_TIMEOUT_SECONDS
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.*
import org.jetbrains.kotlin.formver.common.FailureToFirRenderer
import org.jetbrains.kotlin.formver.common.Failure
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.plugin.runners.*
import org.jetbrains.kotlin.formver.testrunner.Lifetime
import org.jetbrains.kotlin.formver.viper.InvalidViperProgramException
import org.jetbrains.kotlin.formver.viper.SiliconFrontend
import org.jetbrains.kotlin.formver.viper.OrderedVerificationExecutor
import java.nio.file.Path
import java.io.Closeable
import scala.jdk.javaapi.CollectionConverters
import org.jetbrains.kotlin.formver.viper.ast.unwrapOr
import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import org.jetbrains.kotlin.test.TestInfrastructureInternals
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.model.*
import org.jetbrains.kotlin.test.services.TestServices

fun shouldSkipByTestMode(testServices: TestServices): Boolean = when (getTestMode()) {
    TestMode.CHECK_CONVERSION -> true
    TestMode.UPDATE -> !testServices.conversionDiagnosticsCollector.resultHasChanged()
            && !testServices.viperTextCollector.resultHasChanged()
    TestMode.FULL -> false
}


class ViperProgramVerificationFacade(val testServices: TestServices) :
    AbstractTestFacade<FirOutputArtifact, FirOutputArtifact>() {
    private val reportedErrors = ReportedVerifierErrors()

    override val inputKind: TestArtifactKind<FirOutputArtifact>
        get() = FrontendKinds.FIR
    override val outputKind: TestArtifactKind<FirOutputArtifact>
        get() = FrontendKinds.FIR

    /**
     * Returns true iff the given [decl] should be verified.
     */
    fun shouldVerify(decl: FirDeclaration, testServices: TestServices): Boolean =
        decl.shouldVerify == true && !shouldSkipByTestMode(testServices)

    /**
     * Extracts the Viper programs and if necessary, verifies them.
     * The result is registered in the [testServices].
     */
    @OptIn(TestInfrastructureInternals::class)
    override fun transform(
        module: TestModule,
        inputArtifact: FirOutputArtifact
    ): FirOutputArtifact {
        inputArtifact.partsForDependsOnModules.forEach { part ->
            val toVerify: MutableList<Pair<TestFile, FirDeclaration>> = mutableListOf()
            part.firFiles.map { (testFile, firFile) ->
                firFile.accept(object : FirDefaultVisitorVoid() {
                    override fun visitElement(element: FirElement) {
                        when (element) {
                            is FirSimpleFunction -> visitSimpleFunction(element)
                            else -> element.acceptChildren(this)
                        }
                    }

                    override fun visitFile(file: FirFile) {
                        if (shouldVerify(file, testServices)) toVerify.add(Pair(testFile, file))
                        file.acceptChildren(this)
                    }

                    override fun visitRegularClass(regularClass: FirRegularClass) {
                        if (shouldVerify(regularClass, testServices) ||
                            !shouldSkipByTestMode(testServices) && regularClass.lambdaPrograms.orEmpty().any { it.shouldVerify }) {
                            toVerify.add(Pair(testFile, regularClass))
                        }
                        regularClass.acceptChildren(this)
                    }

                    override fun visitProperty(property: FirProperty) {
                        if (!shouldSkipByTestMode(testServices) && property.lambdaPrograms.orEmpty().any { it.shouldVerify }) {
                            toVerify.add(Pair(testFile, property))
                        }
                        property.acceptChildren(this)
                    }

                    override fun visitPropertyAccessor(propertyAccessor: FirPropertyAccessor) {
                        if (shouldVerify(propertyAccessor, testServices)) {
                            toVerify.add(Pair(testFile, propertyAccessor))
                        }
                        propertyAccessor.acceptChildren(this)
                    }

                    override fun visitSimpleFunction(simpleFunction: FirSimpleFunction) {
                        if (shouldVerify(simpleFunction, testServices) ||
                            !shouldSkipByTestMode(testServices) && simpleFunction.lambdaPrograms.orEmpty().any { it.shouldVerify }) {
                            toVerify.add(Pair(testFile, simpleFunction))
                        }
                        simpleFunction.acceptChildren(this)
                    }
                })
            }
            if (toVerify.isNotEmpty()) {
                val z3 = SiliconFrontend.locateZ3()
                val vacuityReport = VacuityProbe.report
                val programs = toVerify.map { (_, decl) ->
                    val group = listOfNotNull(decl.viperProgram.takeIf { decl.shouldVerify == true }) +
                        decl.lambdaPrograms.orEmpty().filter { it.shouldVerify }.map { it.program }
                    if (vacuityReport == null) group else group.map(VacuityProbe::probe)
                }
                val configuration = TestVerifierConfiguration(
                    z3 = z3,
                    counterexamples = FormVerDirectives.COUNTEREXAMPLES in module.directives,
                    timeoutSeconds = DEFAULT_VERIFICATION_TIMEOUT_SECONDS,
                    cacheDirectory = System.getProperty("formver.verificationCacheDirectory")?.let { Path.of(it) },
                    cacheReportDirectory = System.getProperty("formver.verificationCacheReport")?.let { Path.of(it) },
                    cacheRefresh = java.lang.Boolean.getBoolean("formver.verificationCacheRefresh"),
                    cacheColdSample = System.getProperty("formver.verificationColdSample"),
                )
                val progress = VerificationProgress(MessageCollector.NONE)
                val timingReport = DeclarationVerificationReport.fromEnvironment()
                val attempts = OrderedVerificationExecutor.shared.map(toVerify.zip(programs)) { (owner, group) ->
                    verifierPool.checkout(configuration).use { lease ->
                        val errors = mutableListOf<VerifierError>()
                        var invalid: InvalidViperProgramException? = null
                        var timeout: Failure? = null
                        val verify: ((String) -> Unit) -> String = { onCacheStatus ->
                            try {
                                timeout = progress.run(owner.second.verificationLabel, owner.second.source) {
                                    lease.run { verifier ->
                                        group.forEach { verifier.verify(it, onCacheStatus) { error -> errors.add(error) } }
                                    }
                                }
                            } catch (error: InvalidViperProgramException) {
                                invalid = error
                            }
                            when {
                                timeout != null -> "timeout"
                                invalid != null -> "invalid"
                                errors.isNotEmpty() -> "failed"
                                else -> "verified"
                            }
                        }
                        if (timingReport == null) verify({})
                        else timingReport.run(
                            owner.first.originalFile.path, owner.second.verificationLabel, owner.second.source?.startOffset,
                            DEFAULT_VERIFICATION_TIMEOUT_SECONDS, verify,
                        )
                        VerificationAttempt(errors, invalid, timeout)
                    }
                }
                toVerify.zip(attempts).zip(programs).forEach { (verified, group) ->
                    val (owner, probedAttempt) = verified
                    val (testFile, decl) = owner
                    val hits = probedAttempt.errors.mapNotNull(VacuityProbe::hitOf)
                    val attempt = probedAttempt.copy(errors = probedAttempt.errors.filter { VacuityProbe.hitOf(it) == null })
                    val verifiedOtherwise = attempt.errors.isEmpty() && attempt.invalid == null
                    if (vacuityReport != null && hits.isNotEmpty() && verifiedOtherwise) {
                        VacuityProbe.record(vacuityReport, testFile.originalFile.path, decl.symbol.toString(), hits, group)
                    }
                    if (verifiedOtherwise && attempt.timeout == null && decl !is FirFile) recordTrust(testFile, decl)
                    val diagnostics = verifyFunction(attempt, decl, module)
                    testServices.verificationDiagnosticsCollector.addDiagnostics(diagnostics)
                    testServices.allTagCollector.reportDiagnostics(testFile, diagnostics)
                    testServices.verificationOutcomes.reportDiagnostics(testFile, diagnostics)
                }

            }
        }

        return inputArtifact
    }

    /** Records the verified programs of [decl], whose verification succeeded, in the trust report. */
    private fun recordTrust(testFile: TestFile, decl: FirDeclaration) {
        val collector = testServices.trustReportCollector
        if (decl.shouldVerify == true) collector.record(testFile.name, decl.verificationLabel, decl.trust!!)
        for (lambda in decl.lambdaPrograms.orEmpty().filter { it.shouldVerify }) {
            collector.record(testFile.name, lambda.label, lambda.trust)
        }
    }

    companion object {
        // Closed by Lifetime when the class loader holding this pool is dropped, or at JVM exit.
        private val verifierPool = TestVerifierPool(4, create = ::TestVerifier).also(Lifetime::register)
    }

    /** Opt-in fixtures also exercise frontend reuse across Silicon's global configuration changes. */
    private class TestVerifier(configuration: TestVerifierConfiguration) : Closeable {
        private val counterexamples = configuration.counterexamples
        private val verifier = SiliconFrontend(
            configuration.z3, timeoutSeconds = configuration.timeoutSeconds,
            cacheDirectory = configuration.cacheDirectory, counterexamples = counterexamples,
            cacheReportDirectory = configuration.cacheReportDirectory,
            cacheRefresh = configuration.cacheRefresh,
            cacheColdSample = configuration.cacheColdSample,
        )
        private val ordinary = if (counterexamples) {
            SiliconFrontend(configuration.z3, timeoutSeconds = configuration.timeoutSeconds, counterexamples = false)
        } else null

        fun verify(program: viper.silver.ast.Program, onCacheStatus: (String) -> Unit, onFailure: (VerifierError) -> Unit) {
            fun hasModel(error: VerifierError): Boolean = error is VerificationError &&
                CollectionConverters.asJava(error.result.failureContexts()).any { it.counterExample().isDefined }
            ordinary?.verify(program) { check(!hasModel(it)) }
            verifier.verify(program, onCacheStatus) {
                if (counterexamples) check(hasModel(it))
                onFailure(it)
            }
            ordinary?.verify(program) { check(!hasModel(it)) }
        }

        override fun close() {
            try {
                verifier.close()
            } finally {
                ordinary?.close()
            }
        }
    }

    private data class VerificationAttempt(
        val errors: List<VerifierError>, val invalid: InvalidViperProgramException?, val timeout: Failure?,
    )

    /** Formats worker results on the compiler thread. */
    @OptIn(InternalDiagnosticFactoryMethod::class)
    private fun verifyFunction(
        attempt: VerificationAttempt,
        decl: FirDeclaration,
        module: TestModule
    ): List<KtDiagnostic> {
        val results = mutableListOf<KtDiagnostic>()
        if (decl is FirFile) specVacuityFailures(attempt.errors, decl.source).forEach {
            results.add(FailureToFirRenderer.diagnostic(it, module.languageVersionSettings)!!)
        } else attempt.errors.forEach { err ->
            val source = err.reportingPosition(decl.source).unwrapOr { decl.source }!!
            if (reportedErrors.add(err, source)) {
                results.add(
                    when (err) {
                        is VerificationError -> formatVerificationError(err, source, module)
                    }
                )
            }
        }
        attempt.timeout?.let { results.add(FailureToFirRenderer.diagnostic(it, module.languageVersionSettings)!!) }
        attempt.invalid?.let { e ->
            results.add(
                FailureToFirRenderer.diagnostic(
                    Failure(VerificationErrors.INVALID_VIPER_PROGRAM, decl.source,
                        KotlinCondition.Tool("BACKEND_REJECTED_PROGRAM", e.message!!)),
                    module.languageVersionSettings,
                )!!
            )
        }

        return results
    }

    override fun shouldTransform(module: TestModule): Boolean = true


    @OptIn(InternalDiagnosticFactoryMethod::class)
    private fun formatVerificationError(
        err: VerificationError, source: KtSourceElement,
        module: TestModule
    ): KtDiagnostic {
        return FailureToFirRenderer.diagnostic(err.toFailure(source), module.languageVersionSettings)!!
    }
}


/**
 * If necessary, asserts the verification result as well as the conversion result.
 */
class ViperResultHandler(testServices: TestServices) :
    AnalysisHandler<FirOutputArtifact>(testServices, false, false) {
    override val artifactKind: TestArtifactKind<FirOutputArtifact> get() = FrontendKinds.FIR

    override fun processModule(module: TestModule, info: FirOutputArtifact) {
        if (shouldSkipByTestMode(testServices)) return

        testServices.verificationOutcomes.check(info, verified = true)
        runChecks(
            testServices,
            { testServices.conversionDiagnosticsCollector.assertEquality() },
            { testServices.viperTextCollector.assertEquality() },
            { testServices.verificationDiagnosticsCollector.assertEquality() },
            { testServices.allTagCollector.assertEqual() },
            { testServices.trustReportCollector.assertEquality() },
        )
    }

    override fun processAfterAllModules(someAssertionWasFailed: Boolean) {}
}
