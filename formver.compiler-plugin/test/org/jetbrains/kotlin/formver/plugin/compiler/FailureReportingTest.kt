package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithParameters1
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.viper.ast.unwrapOr
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.core.embeddings.asInfo
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.*
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.core.names.SimpleNameResolver
import org.jetbrains.kotlin.formver.viper.ast.Position
import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.seqOf
import viper.silver.ast.*
import viper.silver.verifier.errors
import viper.silver.verifier.reasons
import kotlin.test.*

class FailureReportingTest {
    private val pos = `NoPosition$`.`MODULE$`
    private val info = `NoInfo$`.`MODULE$`
    private val trafos = `NoTrafos$`.`MODULE$`

    private fun failure(role: SourceRole? = null): VerificationError {
        val metadata = with(SimpleNameResolver()) { role.asInfo.toSilver() }
        val condition = FalseLit(pos, metadata, trafos)
        val node = Assert(condition, pos, metadata, trafos)
        return VerificationError(errors.AssertFailed(node, reasons.AssertionFalse(condition), false))
    }

    @Test
    fun `backend failures without a source role are structured errors with both reason IDs`() {
        val backend = failure()
        val problem = backend.toFailure(null)
        assertEquals(FailureKind.VIPER_VERIFICATION_ERROR, problem.kind)
        assertEquals(KotlinCondition.Unclassified, problem.condition)
        assertEquals(backend.result.id(), problem.backend!!.errorId)
        assertEquals(backend.result.reason().id(), problem.backend!!.reasonId)
        assertEquals(Severity.ERROR, problem.kind.factory.severity)
        assertNull(problem.primary)
        assertNull(problem.counterexample)
        assertTrue(problem.related.isEmpty())
    }

    @Test
    fun `role adapter covers every proof failure kind`() {
        val roles = listOf(
            SourceRole.ReturnsEffect.Bool(true),
            SourceRole.Proof(KotlinCondition.ProofClause.ASSERTION),
            SourceRole.ConditionalEffect(SourceRole.ReturnsEffect.Wildcard, SourceRole.Condition.Constant(false)),
            SourceRole.ArrayElementAccessCheck(SourceRole.ArrayElementAccessCheck.Bound.NEGATIVE, null),
            SourceRole.Ownership("x", SourceRole.Ownership.Site.Fold),
            SourceRole.OverrideRefinement("base", SourceRole.OverrideRefinement.Clause.POSTCONDITION, Position.NoPosition),
        )
        val problems = roles.map { failure(it).toFailure(null) } + subListFailure().toFailure(null) + failure().toFailure(null)
        assertEquals(setOf(
            FailureKind.UNEXPECTED_RETURNED_VALUE, FailureKind.CONDITIONAL_EFFECT_ERROR,
            FailureKind.POSSIBLE_INDEX_OUT_OF_BOUND, FailureKind.OWNERSHIP_NOT_ESTABLISHED,
            FailureKind.OVERRIDE_NOT_REFINING, FailureKind.INVALID_SUBLIST_RANGE,
            FailureKind.VIPER_VERIFICATION_ERROR, FailureKind.VERIFICATION_CONDITION_NOT_ESTABLISHED,
        ), problems.map { it.kind }.toSet())
        problems.forEach {
            assertEquals(Severity.ERROR, it.kind.factory.severity)
            assertSame(it.kind.factory, it.kind.factory)
            assertNotNull(FailureToFirRenderer.MAP[it.kind.factory])
        }
        assertEquals(KotlinCondition.ReturnValue.TRUE, (problems[0].condition as KotlinCondition.Returns).effect)
        assertEquals(KotlinCondition.Bound.NEGATIVE, (problems[3].condition as KotlinCondition.Index).bound)
    }

    @Test
    fun `statement provenance survives a transformed proposition without metadata`() {
        val metadata = with(SimpleNameResolver()) {
            SourceRole.Proof(KotlinCondition.ProofClause.REFUTATION).asInfo.toSilver()
        }
        val proposition = FalseLit(pos, info, trafos)
        val node = Assert(proposition, pos, metadata, trafos)
        val problem = VerificationError(errors.AssertFailed(node, reasons.AssertionFalse(proposition), false)).toFailure(null)
        assertEquals(FailureKind.VERIFICATION_CONDITION_NOT_ESTABLISHED, problem.kind)
        assertEquals(KotlinCondition.Proof(KotlinCondition.ProofClause.REFUTATION), problem.condition)
        assertNotNull(problem.backend)
        assertNull(problem.counterexample)
    }

    @Test
    fun `expression metadata carries contract clauses through backend failures`() {
        listOf(KotlinCondition.ProofClause.PRECONDITION, KotlinCondition.ProofClause.POSTCONDITION).forEach { clause ->
            val expression = with(SimpleNameResolver()) {
                org.jetbrains.kotlin.formver.viper.ast.Exp.WithInfo(
                    org.jetbrains.kotlin.formver.viper.ast.Exp.BoolLit(false), SourceRole.Proof(clause).asInfo,
                ).toSilver()
            }
            assertIs<FalseLit>(expression)
            val backend = VerificationError(errors.AssertFailed(Assert(expression, pos, info, trafos),
                reasons.AssertionFalse(expression), false))
            assertEquals(KotlinCondition.Proof(clause), backend.toFailure(null).condition)
        }
    }

    @Test
    fun `postcondition origin remains related to the failing implementation`() {
        val clause = diagnosticSource(10)
        val implementation = diagnosticSource(20)
        val backend = with(SimpleNameResolver()) {
            val expression = org.jetbrains.kotlin.formver.viper.ast.Exp.WithInfo(
                org.jetbrains.kotlin.formver.viper.ast.Exp.BoolLit(false, Position.Wrapped(clause)),
                SourceRole.Proof(KotlinCondition.ProofClause.POSTCONDITION).asInfo,
            ).toSilver()
            val member = Method("implementation", seqOf<LocalVarDecl>(), seqOf<LocalVarDecl>(),
                seqOf<Exp>(), seqOf(expression), scala.Option.empty(), Position.Wrapped(implementation).toSilver(), info, trafos)
            VerificationError(errors.PostconditionViolated(expression, member, reasons.AssertionFalse(expression), false))
        }
        val primary = backend.reportingPosition(implementation).unwrapOr<KtSourceElement> { null }
        val problem = backend.toFailure(primary)
        assertSame(implementation, problem.primary)
        assertEquals(KotlinCondition.Proof(KotlinCondition.ProofClause.POSTCONDITION), problem.condition)
        assertEquals(listOf(clause), problem.related.map { it.source })
        assertSame(implementation, backend.reportingPosition(diagnosticSource(30)).unwrapOr<KtSourceElement> { null })
    }

    @Test
    fun `expression annotations do not hide the violated obligation`() {
        val incidental = with(SimpleNameResolver()) { SourceRole.Condition.Constant(false).asInfo.toSilver() }
        val obligation = with(SimpleNameResolver()) { SourceRole.ReturnsEffect.Bool(true).asInfo.toSilver() }
        val proposition = FalseLit(pos, obligation, trafos)
        val node = Assert(proposition, pos, incidental, trafos)
        val problem = VerificationError(errors.AssertFailed(node, reasons.AssertionFalse(proposition), false)).toFailure(null)
        assertEquals(FailureKind.UNEXPECTED_RETURNED_VALUE, problem.kind)
        assertEquals(KotlinCondition.Returns(KotlinCondition.ReturnValue.TRUE), problem.condition)
    }

    @Test
    fun `observer permission failures remain specific inside annotated proof clauses`() {
        val source = diagnosticSource(10)
        val observerPosition = Position.Wrapped(source)
        val backend = with(SimpleNameResolver()) {
            val observer = SourceRole.ObserverAccess(observerPosition).asInfo.toSilver()
            val predicate = PredicateAccess(seqOf<Exp>(), "observer", pos, observer, trafos)
            val proof = SourceRole.Proof(KotlinCondition.ProofClause.POSTCONDITION).asInfo.toSilver()
            val node = Assert(TrueLit(pos, info, trafos), pos, proof, trafos)
            VerificationError(errors.AssertFailed(node, reasons.InsufficientPermission(predicate), false))
        }
        val problem = backend.toFailure(source)
        assertEquals(FailureKind.OWNERSHIP_NOT_ESTABLISHED, problem.kind)
        assertIs<KotlinCondition.Ownership>(problem.condition)
        assertSame(source, backend.reportingPosition(diagnosticSource(20)).unwrapOr<KtSourceElement> { null })
    }

    private fun subListFailure(): VerificationError {
        val metadata = with(SimpleNameResolver()) { SourceRole.SubListCreation.CheckInSize.asInfo.toSilver() }
        val condition = FalseLit(pos, info, trafos)
        val call = FuncApp("subList", seqOf<Exp>(condition), pos, metadata, `Bool$`.`MODULE$`, trafos)
        return VerificationError(errors.PreconditionInAppFalse(call, reasons.AssertionFalse(condition), false))
    }

    @Test
    fun `equal clauses at distinct call sites are not deduplicated`() {
        val first = subListFailure()
        val second = subListFailure()
        assertEquals(first.result.offendingNode(), second.result.offendingNode())
        val reports = ReportedVerifierErrors()
        assertTrue(reports.add(first, null))
        assertTrue(reports.add(second, null))
        assertFalse(reports.add(first, null))
        val firstCall = diagnosticSource(10)
        val secondCall = diagnosticSource(20)
        assertTrue(reports.add(first, firstCall))
        assertTrue(reports.add(second, secondCall))
        assertFalse(reports.add(first, firstCall))
        assertFalse(reports.add(second, secondCall))
    }

    @Test
    fun `cloned backend obligations at one Kotlin origin are deduplicated`() {
        val source = diagnosticSource(10)
        val first = failure(SourceRole.Proof(KotlinCondition.ProofClause.ASSERTION))
        val second = failure(SourceRole.Proof(KotlinCondition.ProofClause.ASSERTION))
        val reports = ReportedVerifierErrors()
        assertTrue(reports.add(first, source))
        assertFalse(reports.add(second, source))
        assertTrue(reports.add(second, diagnosticSource(20)))
        assertTrue(reports.add(failure(SourceRole.Proof(KotlinCondition.ProofClause.REFUTATION)), source))
        val proposition = TrueLit(pos, info, trafos)
        val different = VerificationError(errors.AssertFailed(Assert(proposition, pos, info, trafos),
            reasons.AssertionFalse(proposition), false))
        assertTrue(reports.add(different, source))
        fun atExpression(offset: Int): VerificationError {
            val position = with(SimpleNameResolver()) { Position.Wrapped(diagnosticSource(offset)).toSilver() }
            val condition = FalseLit(position, info, trafos)
            return VerificationError(errors.AssertFailed(Assert(condition, pos, info, trafos),
                reasons.AssertionFalse(condition), false))
        }
        assertTrue(reports.add(atExpression(30), source))
        assertTrue(reports.add(atExpression(40), source))
    }

    @Test
    fun `presentation modes retain structured evidence without changing the obligation`() {
        val problem = failure(SourceRole.ReturnsEffect.Bool(true)).toFailure(null)
        val friendly = FailureToFirRenderer.message(problem, ErrorStyle.USER_FRIENDLY)
        val original = FailureToFirRenderer.message(problem, ErrorStyle.ORIGINAL_VIPER)
        val both = FailureToFirRenderer.message(problem, ErrorStyle.BOTH)
        assertNotEquals(friendly, original)
        assertTrue(original.contains(problem.backend!!.errorId))
        assertTrue(original.contains(problem.backend!!.reasonId))
        assertTrue(original.contains(problem.backend!!.detail))
        assertTrue(both.startsWith(friendly))
        assertTrue(both.contains(problem.backend!!.detail))
        assertEquals(both, FailureToFirRenderer.message(problem.copy(errorStyle = ErrorStyle.BOTH)))
        val reports = ReportedVerifierErrors()
        assertTrue(reports.add(problem))
        assertFalse(reports.add(problem.copy(errorStyle = ErrorStyle.BOTH)))
    }

    @Test
    fun `skipped verification carries a coded cause independently of presentation`() {
        KotlinCondition.SkipReason.entries.forEach { reason ->
            val problem = Failure(FailureKind.VERIFICATION_SKIPPED, null, KotlinCondition.Skipped(reason, null))
            ErrorStyle.entries.forEach { style ->
                assertTrue(FailureToFirRenderer.message(problem, style).contains(reason.name))
            }
            assertEquals(Severity.WARNING, problem.kind.factory.severity)
            assertNull(problem.counterexample)
        }
    }

    @Test
    fun `exception adapters retain machine reasons and preserve attributed failures`() {
        val unsupported = UnsupportedFeatureException(null, "test construct")
        val propagated = assertFailsWith<UnsupportedFeatureException> {
            attributingFailuresTo(null) { throw unsupported }
        }
        assertSame(unsupported, propagated)
        assertEquals(UnsupportedReason.KOTLIN_CONSTRUCT, propagated.reason)
        val cause = IllegalStateException("test invariant")
        val internal = assertFailsWith<SnaktInternalException> {
            attributingFailuresTo(null) { throw cause }
        }
        assertSame(cause, internal.cause)
        assertEquals(InternalReason.UNEXPECTED_EXCEPTION, internal.reason)
        assertEquals(InternalReason.INVARIANT_BROKEN, SnaktInternalException(null, "test state").reason)
    }

    @Test
    fun `unsupported feature reasons survive attribution and the FIR boundary`() {
        val source = diagnosticSource(10)
        UnsupportedReason.entries.forEach { reason ->
            val original = UnsupportedFeatureException(source, "test construct", reason)
            val exception = assertFailsWith<UnsupportedFeatureException> {
                attributingFailuresTo(diagnosticSource(20)) { throw original }
            }
            val problem = Failure(FailureKind.UNSUPPORTED_FEATURE, exception.source,
                KotlinCondition.Translation(exception.reason.name, exception.message))
            val diagnostic = assertIs<KtDiagnosticWithParameters1<*>>(
                FailureToFirRenderer.diagnostic(problem, LanguageVersionSettingsImpl.DEFAULT))
            assertSame(original, exception)
            assertSame(source, diagnostic.element)
            assertSame(problem, diagnostic.a)
            assertSame(FailureKind.UNSUPPORTED_FEATURE.factory, diagnostic.factory)
            assertEquals(reason.name, (problem.condition as KotlinCondition.Translation).reason)
            assertEquals(Severity.WARNING, diagnostic.severity)
            assertNull(problem.counterexample)
        }
    }

    @Test
    fun `every specification family preserves its rule through the common FIR adapter`() {
        val source = diagnosticSource(10)
        val problems = listOf(
            Failure(FailureKind.INVALID_SPEC_OF, source, KotlinCondition.SpecOf(SpecOfRule.BODY_SHAPE)),
            Failure(FailureKind.INVALID_EXTERNAL_SPEC, source, KotlinCondition.ExternalSpec(ExternalSpecRule.SOURCE_SUBTYPE, "a.B")),
            Failure(FailureKind.INVALID_EXPOSES, source, KotlinCondition.Exposes(ExposesRule.BINDING_SHAPE)),
            Failure(FailureKind.INVALID_OBSERVER, source, KotlinCondition.Observer(ObserverRule.GHOST_OWNER)),
            Failure(FailureKind.INVALID_LAW, source, KotlinCondition.Law(LawRule.BODY)),
        )
        assertEquals(FailureKind.entries.filter { it.category == FailureCategory.SPECIFICATION }.toSet(),
            problems.map { it.kind }.toSet())
        problems.forEach { problem ->
            val diagnostic = assertIs<KtDiagnosticWithParameters1<*>>(
                FailureToFirRenderer.diagnostic(problem, LanguageVersionSettingsImpl.DEFAULT))
            assertSame(problem, diagnostic.a)
            assertSame(problem.kind.factory, diagnostic.factory)
            assertEquals(Severity.ERROR, diagnostic.severity)
            assertSame(source, diagnostic.element)
            assertTrue(FailureToFirRenderer.message(problem).isNotBlank())
        }
    }

    @Test
    fun `tool failures and untracked writes retain payloads and registry severity in FIR`() {
        val source = diagnosticSource(10)
        val problems = FailureKind.entries.filter { it.category == FailureCategory.TOOL }.map { kind ->
            Failure(kind, source, KotlinCondition.Tool("TEST_REASON", "test evidence"))
        } + Failure(FailureKind.UNTRACKED_WRITE, source, KotlinCondition.UntrackedWrite)
        problems.forEach { problem ->
            val diagnostic = assertIs<KtDiagnosticWithParameters1<*>>(
                FailureToFirRenderer.diagnostic(problem, LanguageVersionSettingsImpl.DEFAULT))
            assertSame(problem, diagnostic.a)
            assertSame(problem.kind.factory, diagnostic.factory)
            assertEquals(problem.kind.severity, diagnostic.severity)
            assertSame(source, diagnostic.element)
            assertNull(problem.counterexample)
            assertTrue(FailureToFirRenderer.message(problem).isNotBlank())
        }
    }

    @Test
    fun `registry keeps translation coverage separate from proof failures`() {
        assertTrue(FailureKind.entries.filter { it.category == FailureCategory.PROOF }
            .all { it.severity == Severity.ERROR })
        assertEquals(Severity.WARNING, FailureKind.UNTRACKED_WRITE.severity)
        assertEquals(Severity.WARNING, FailureKind.UNSUPPORTED_FEATURE.severity)
        assertEquals(Severity.ERROR, FailureKind.INTERNAL_ERROR.severity)
        assertEquals(FailureCategory.TRANSLATION, FailureKind.VERIFICATION_SKIPPED.category)
        assertEquals(FailureCategory.TOOL, FailureKind.INVALID_VIPER_PROGRAM.category)
        assertEquals(FailureKind.entries.size, FailureKind.entries.map { it.factory.name }.toSet().size)
    }

    @Test
    fun `presentation and counterexample slots do not identify obligations`() {
        val problem = failure().toFailure(null)
        val reports = ReportedVerifierErrors()
        assertTrue(reports.add(problem))
        assertFalse(reports.add(problem.copy(backend = problem.backend!!.copy(detail = "different backend detail"))))
        assertFalse(reports.add(problem.copy(counterexample = Counterexample(listOf(
            CounterexampleBinding("x", CounterexampleValue.Integer(7.toBigInteger())),
        )))))
    }
}
