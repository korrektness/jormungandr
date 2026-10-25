package org.jetbrains.kotlin.formver.plugin.compiler

import com.intellij.lang.LighterASTNode
import com.intellij.openapi.util.Ref
import com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithParameters1
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.core.conversion.AccessPolicy
import org.jetbrains.kotlin.formver.core.diagnostics.ErrorCollectionContext
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.callables.FunctionSignatureImpl
import org.jetbrains.kotlin.formver.core.embeddings.callables.NamedFunctionSignatureImpl
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineCallableImpl
import org.jetbrains.kotlin.formver.core.embeddings.types.FunctionTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.FieldEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.formver.core.names.DispatchReceiverName
import org.jetbrains.kotlin.formver.core.names.SimpleKotlinName
import org.jetbrains.kotlin.formver.core.linearization.*
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.PathStep
import org.jetbrains.kotlin.formver.core.purity.checkReadOnlyVarReads
import org.jetbrains.kotlin.formver.core.purity.checkValidity
import org.jetbrains.kotlin.formver.viper.ast.Type
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.test.*

class ConversionFailureReportingTest {
    private class Errors : ErrorCollectionContext {
        val failures = mutableListOf<Failure>()
        override fun reportPurityViolation(source: KtSourceElement?, condition: PurityCondition) {
            failures += purityFailure(source, condition)
        }
        override fun reportUnsupportedOwnership(source: KtSourceElement?, condition: ConversionOwnershipCondition) {
            failures += unsupportedOwnershipFailure(source, condition)
        }
        override fun reportUntrackedWrite(source: KtSourceElement?) = error("Unexpected write")
        override fun reportUncheckedLibraryCall(source: KtSourceElement?, member: String) = error("Unexpected library call")
        override fun reportUnsupportedFeature(source: KtSourceElement?, msg: String, reason: UnsupportedReason) =
            error("Unexpected unsupported feature")
    }

    private val symbol = FirRegularClassSymbol(ClassId.topLevel(FqName("example.Owner")))
    private val root = FirVariableEmbedding(SimpleKotlinName(Name.identifier("root")), buildType { any() }, symbol)
    private val property = FirRegularPropertySymbol(CallableId(FqName("example"), Name.identifier("member")))
    private val field = object : FieldEmbedding {
        override val name = SimpleKotlinName(Name.identifier("member"))
        override val type = root.type
        override val viperType = Type.Ref
        override val accessPolicy = AccessPolicy.BY_RECEIVER_UNIQUENESS
        override val includeInShortDump = false
        override val symbol = property
    }

    private fun source(offset: Int): KtSourceElement {
        val node = object : LighterASTNode {
            override fun getTokenType() = KtTokens.IDENTIFIER
            override fun getStartOffset() = offset
            override fun getEndOffset() = offset + 1
        }
        val tree = object : FlyweightCapableTreeStructure<LighterASTNode> {
            override fun getRoot() = node
            override fun getParent(node: LighterASTNode): LighterASTNode? = null
            override fun getChildren(node: LighterASTNode, into: Ref<Array<LighterASTNode>>): Int {
                into.set(emptyArray())
                return 0
            }
            override fun disposeChildren(nodes: Array<LighterASTNode>, count: Int) {}
            override fun toString(node: LighterASTNode): CharSequence = "x"
            override fun getStartOffset(node: LighterASTNode) = node.startOffset
            override fun getEndOffset(node: LighterASTNode) = node.endOffset
        }
        return KtLightSourceElement(node, offset, offset + 1, tree)
    }

    private fun assertDiagnostic(failure: Failure, kind: FailureKind) {
        assertEquals(kind, failure.kind)
        val diagnostic = assertIs<KtDiagnosticWithParameters1<*>>(
            FailureToFirRenderer.diagnostic(failure, LanguageVersionSettingsImpl.DEFAULT))
        assertSame(kind.factory, diagnostic.factory)
        assertEquals(Severity.ERROR, diagnostic.severity)
        assertSame(failure, diagnostic.a)
        assertSame<Any?>(failure.primary, diagnostic.element)
        assertNull(failure.counterexample)
        assertNotNull(FailureToFirRenderer.MAP[kind.factory])
        assertTrue(FailureToFirRenderer.message(failure).isNotBlank())
    }

    @Test
    fun `fold absence conversion retains paths loss sites and FIR provenance`() {
        val current = source(0)
        val origin = source(10)
        val path = OwnedPath(root)
        val movedTo = OwnedPath(PlaceholderVariableEmbedding(SimpleKotlinName(Name.identifier("target")), root.type))
        val reasons = listOf(
            Absence.NeverHeld to OwnershipAbsenceReason.NEVER_HELD,
            Absence.Released(origin) to OwnershipAbsenceReason.CONSUMED,
            Absence.Moved(movedTo, origin) to OwnershipAbsenceReason.MOVED,
            Absence.Forgotten(origin) to OwnershipAbsenceReason.JOINED,
            Absence.Framed(origin) to OwnershipAbsenceReason.FRAMED,
        )
        reasons.forEach { (absence, reason) ->
            val original = FoldFailure.NotHeld<VariableEmbedding, PathStep, ClassTypeEmbedding, KtSourceElement?>(path, path, absence)
            val exception = FoldStateException(current, original.toCondition())
            val condition = exception.condition
            val problem = unsupportedOwnershipFailure(exception.source, condition)
            assertEquals(FoldOwnershipRule.NOT_HELD, condition.rule)
            assertSame(symbol, condition.path!!.root.symbol)
            assertEquals(condition.path, condition.absent)
            assertEquals(reason, condition.absence!!.reason)
            if (reason == OwnershipAbsenceReason.NEVER_HELD) assertTrue(problem.related.isEmpty())
            else assertSame(origin, problem.related.single().source)
            if (reason == OwnershipAbsenceReason.MOVED) assertEquals("target", condition.absence!!.target!!.root.name)
            assertDiagnostic(problem, FailureKind.UNSUPPORTED_OWNERSHIP)
            assertEquals(FailureToFirRenderer.message(problem), exception.message)
        }
    }

    @Test
    fun `every fold rule reaches the shared FIR adapter with structured evidence`() {
        val path = OwnedPath(root).toConversionPath()
        val conditions = listOf(
            FoldOwnershipCondition(FoldOwnershipRule.NOT_HELD, path, path, OwnershipAbsence(OwnershipAbsenceReason.NEVER_HELD)),
            FoldOwnershipCondition(FoldOwnershipRule.HOLE_BELOW, path, path, OwnershipAbsence(OwnershipAbsenceReason.CONSUMED)),
            FoldOwnershipCondition(FoldOwnershipRule.NOT_SUPERTYPE, path, typeName = "Owner"),
            FoldOwnershipCondition(FoldOwnershipRule.LOOP_INVARIANT_HOLE, path, path),
            FoldOwnershipCondition(FoldOwnershipRule.UNTRACKED_WRITE_RECEIVER),
            FoldOwnershipCondition(FoldOwnershipRule.BORROWED_ASSIGNMENT, path),
            FoldOwnershipCondition(FoldOwnershipRule.DOWNCAST),
            FoldOwnershipCondition(FoldOwnershipRule.BORROWED_VIEW_ESCAPE,
                path.copy(members = listOf(ConversionOwnershipPath.Member(property, "member")))),
        )
        assertEquals(FoldOwnershipRule.entries.toSet(), conditions.map { it.rule }.toSet())
        val current = source(0)
        conditions.forEach { condition ->
            val problem = unsupportedOwnershipFailure(current, condition)
            assertSame(current, problem.primary)
            assertSame(condition, problem.condition)
            assertDiagnostic(problem, FailureKind.UNSUPPORTED_OWNERSHIP)
            if (condition.rule == FoldOwnershipRule.BORROWED_VIEW_ESCAPE) {
                val evidence = assertNotNull(assertIs<FoldOwnershipCondition>(problem.condition).path)
                assertSame(symbol, evidence.root.symbol)
                assertSame(property, evidence.members.single().symbol)
                assertContains(FailureToFirRenderer.message(problem), "root.member")
            }
        }
    }

    @Test
    fun `every purity rule uses the structured adapter and shared renderer`() {
        for (rule in PurityRule.entries) {
            val condition = PurityCondition(rule, symbol)
            val failure = purityFailure(source(0), condition)
            assertSame(condition, failure.condition)
            assertSame(symbol, condition.declaration)
            assertDiagnostic(failure, FailureKind.PURITY_VIOLATION)
        }
    }

    @Test
    fun `purity traversal attributes assertion and refutation failures to their conditions`() {
        val fallback = source(0)
        val conditionSource = source(10)
        for ((embedding, rule) in listOf(
            Assert(WithPosition(Unreachable, conditionSource)) to PurityRule.ASSERT_CONDITION,
            Refute(WithPosition(Unreachable, conditionSource)) to PurityRule.REFUTE_CONDITION,
        )) {
            val errors = Errors()
            assertFalse(embedding.checkValidity(fallback, errors))
            val failure = errors.failures.single()
            assertSame(conditionSource, failure.primary)
            assertEquals(rule, assertIs<PurityCondition>(failure.condition).rule)
            assertDiagnostic(failure, FailureKind.PURITY_VIOLATION)
        }
        assertTrue(Assert(BooleanLit(true)).checkValidity(fallback, Errors()))
    }

    @Test
    fun `read-only traversal covers all read categories without preformatted payloads`() {
        val reads = listOf(
            FieldAccess(root, field, false), IntArrayGet(root, IntLit(0), false),
            IntArrayContents(root, false), StringBuilderLength(root, false),
        )
        val conditions = reads.map { read ->
            val errors = Errors()
            read.checkReadOnlyVarReads(source(0), errors)
            val failure = errors.failures.single()
            assertDiagnostic(failure, FailureKind.UNSUPPORTED_OWNERSHIP)
            assertIs<ConversionOwnershipCondition.Read>(failure.condition)
        }
        assertEquals(ConversionOwnershipCondition.ReadKind.entries.toSet(), conditions.map { it.kind }.toSet())
        conditions.forEach {
            assertEquals(ConversionOwnershipCondition.ReadReason.NEEDS_UNIQUE_RECEIVER, it.reason)
            assertSame(symbol, it.receiver!!.root.symbol)
        }
        assertSame(property, conditions.first().property)
    }

    @Test
    fun `receiver paths preserve raw root and member symbols`() {
        val receiver = PrimitiveFieldAccess(root, field)
        val errors = Errors()
        IntArrayGet(receiver, IntLit(0), false).checkReadOnlyVarReads(source(0), errors)
        val path = assertIs<ConversionOwnershipCondition.Read>(errors.failures.single().condition).receiver!!
        assertSame(symbol, path.root.symbol)
        assertEquals("root", path.root.name)
        assertSame(property, path.members.single().symbol)
        assertEquals("member", path.members.single().name)
    }

    @Test
    fun `old clears consumption context but still requires ownership`() {
        val consumed = setOf(root.name)
        val errors = Errors()
        IntArrayGet(root, IntLit(0), true).checkReadOnlyVarReads(source(0), errors, consumed)
        assertEquals(ConversionOwnershipCondition.ReadReason.CONSUMED_ROOT,
            assertIs<ConversionOwnershipCondition.Read>(errors.failures.single().condition).reason)
        val old = Errors()
        Old(IntArrayGet(root, IntLit(0), true)).checkReadOnlyVarReads(source(0), old, consumed)
        assertTrue(old.failures.isEmpty())
        Old(IntArrayGet(root, IntLit(0), false)).checkReadOnlyVarReads(source(0), old, consumed)
        assertEquals(ConversionOwnershipCondition.ReadReason.NEEDS_UNIQUE_RECEIVER,
            assertIs<ConversionOwnershipCondition.Read>(old.failures.single().condition).reason)
    }

    @Test
    fun `consumed receiver identity is retained separately from presentation`() {
        val receiver = root.copy(name = DispatchReceiverName)
        val errors = Errors()
        StringBuilderLength(receiver, true).checkReadOnlyVarReads(source(0), errors, setOf(receiver.name))
        val condition = assertIs<ConversionOwnershipCondition.Read>(errors.failures.single().condition)
        assertEquals(ConversionOwnershipPath.Kind.DISPATCH_RECEIVER, condition.receiver!!.root.kind)
        assertSame(symbol, condition.receiver!!.root.symbol)
        assertDiagnostic(errors.failures.single(), FailureKind.UNSUPPORTED_OWNERSHIP)
    }

    @Test
    fun `pure calls through consumed unique arguments retain callee and argument provenance`() {
        val callee = FirNamedFunctionSymbol(CallableId(FqName("example"), Name.identifier("callee")))
        val parameter = root.copy(isUnique = true)
        val signature = FunctionSignatureImpl(
            FunctionTypeEmbedding(null, null, listOf(root.type), root.type, false),
            null, null, listOf(parameter), root, true,
        )
        val function = NonInlineCallableImpl(NamedFunctionSignatureImpl(signature, root.name, callee), callee)
        val argumentSource = source(10)
        val errors = Errors()
        FunctionCall(function, listOf(WithPosition(root, argumentSource)))
            .checkReadOnlyVarReads(source(0), errors, setOf(root.name))
        val failure = errors.failures.single()
        assertSame(argumentSource, failure.primary)
        val condition = assertIs<ConversionOwnershipCondition.ConsumedArgument>(failure.condition)
        assertSame(callee, condition.callee)
        assertSame(symbol, condition.argument.root.symbol)
        assertDiagnostic(failure, FailureKind.UNSUPPORTED_OWNERSHIP)
        val old = Errors()
        Old(FunctionCall(function, listOf(root))).checkReadOnlyVarReads(source(0), old, setOf(root.name))
        assertTrue(old.failures.isEmpty())
    }

    @Test
    fun `missing analysis and consumed argument conditions retain declaration and path identities`() {
        val callee = FirNamedFunctionSymbol(CallableId(FqName("example"), Name.identifier("callee")))
        for (rule in ConversionOwnershipCondition.MissingFactsRule.entries) {
            val origin = source(10)
            val condition = ConversionOwnershipCondition.MissingFacts(rule, callee, origin)
            val failure = unsupportedOwnershipFailure(source(0), condition)
            assertSame(callee, assertIs<ConversionOwnershipCondition.MissingFacts>(failure.condition).declaration)
            assertEquals(listOf(RelatedLocation("analysis declaration", origin)), failure.related)
            assertDiagnostic(failure, FailureKind.UNSUPPORTED_OWNERSHIP)
        }
        val path = ConversionOwnershipPath(ConversionOwnershipPath.Root(ConversionOwnershipPath.Kind.LOCAL, symbol, "root"))
        val condition = ConversionOwnershipCondition.ConsumedArgument(path, callee)
        val failure = unsupportedOwnershipFailure(source(0), condition)
        assertSame(path, assertIs<ConversionOwnershipCondition.ConsumedArgument>(failure.condition).argument)
        assertSame(callee, condition.callee)
        assertDiagnostic(failure, FailureKind.UNSUPPORTED_OWNERSHIP)
        assertDiagnostic(unsupportedOwnershipFailure(source(0), ConversionOwnershipCondition.TryWithOwnership),
            FailureKind.UNSUPPORTED_OWNERSHIP)
    }
}
