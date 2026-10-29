package org.jetbrains.kotlin.formver.plugin.compiler

import com.intellij.lang.LighterASTNode
import com.intellij.openapi.util.Ref
import com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithParameters1
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.locality.plugin.LocalityErrors
import org.jetbrains.kotlin.formver.locality.contract.plugin.LocalityContractErrors
import org.jetbrains.kotlin.formver.type.contract.plugin.FunctionTypeFact
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import kotlin.test.*

class AttributeFailureReportingTest {
    private val owner = FirRegularClassSymbol(ClassId.topLevel(FqName("example.Box")))
    private val path = listOf(owner)
    private val type = ConeClassLikeTypeImpl(ConeClassLikeLookupTagImpl(owner.classId), emptyArray(), false)

    private fun withSources(test: (List<KtSourceElement>) -> Unit) = test(listOf(0, 10, 20).map(::source))

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

    private fun assertDiagnostic(failure: Failure) {
        val diagnostic = assertNotNull(FailureToFirRenderer.diagnostic(failure, LanguageVersionSettingsImpl.DEFAULT))
        assertSame(failure.kind.factory, diagnostic.factory)
        assertEquals(failure.kind.name, diagnostic.factory.name)
        assertEquals(Severity.ERROR, diagnostic.severity)
        val withParameters = assertIs<KtDiagnosticWithParameters1<*>>(diagnostic)
        assertSame<Any?>(failure.primary, withParameters.element)
        assertSame(failure, withParameters.a)
        assertEquals(failure.primary!!.startOffset, diagnostic.firstRange.startOffset)
        assertEquals(failure.primary!!.endOffset, diagnostic.firstRange.endOffset)
        assertNull(failure.counterexample)
        assertNotNull(FailureToFirRenderer.MAP[failure.kind.factory])
    }

    @Test
    fun `every attribute kind uses the common FIR adapter`() = withSources { sources ->
        val source = sources.first()
        val value = FactTarget.Value(FactPosition.ARGUMENT)
        val context = FactTarget.Context(type)
        val problems = listOf(
            LocalityErrors.LOCALITY_MISMATCH.failure(source, value, Locality.Global, Locality.Local),
            LocalityErrors.CONTEXT_LOCALITY_MISMATCH.failure(source, context, Locality.Global, Locality.Local),
            LocalityContractErrors.LOCALITY_CONTRACT_MISMATCH.failure(source, value, null, null),
            LocalityContractErrors.CONTEXT_LOCALITY_CONTRACT_MISMATCH.failure(source, context, null, null),
            UniquenessErrors.UNIQUENESS_MISMATCH.failure(source, value, Uniqueness.Unique, Uniqueness.Shared),
            UniquenessErrors.CONTEXT_UNIQUENESS_MISMATCH.failure(source, context, Uniqueness.Unique, Uniqueness.Shared),
            constructionFailure(source, FactPosition.ARGUMENT, ConstructionEscapeFact(owner, ConstructionEscapeFact.Kind.Use, null)),
            localCaptureFailure(source, owner, owner, sources[1]),
            uniqueCaptureFailure(source, path, sources[1]),
            movedAccessFailure(source, path, listOf(sources[1])),
            argumentCollisionFailure(source, FailureKind.INVALID_DUPLICATE_UNIQUE_ARGUMENT, path, path, sources[1]),
            argumentCollisionFailure(source, FailureKind.INVALID_OVERLAPPING_UNIQUE_ARGUMENTS, path, path + owner, sources[1]),
            pathFailure(source, FailureKind.ESCAPE_UNIQUENESS_INCONSISTENCY, path),
            pathFailure(source, FailureKind.EXIT_UNIQUENESS_INCONSISTENCY, path),
            Failure(FailureKind.CONTEXT_ESCAPE_UNIQUENESS_INCONSISTENCY, source, AttributeCondition.Path(path, contextType = type)),
        ) + listOf(
            FailureKind.INVALID_LOCALITY_TYPE_TARGET, FailureKind.INVALID_UNIQUENESS_TYPE_TARGET,
            FailureKind.INVALID_TYPE_PARAMETER_UNIQUENESS, FailureKind.INVALID_VALUE_TYPE_UNIQUENESS,
            FailureKind.OVERRIDE_UNIQUENESS_MISMATCH, FailureKind.ACTUAL_UNIQUENESS_MISMATCH,
            FailureKind.INVALID_PURE_UNIQUE_RESULT, FailureKind.INVALID_PURE_REFERENCE_RESULT,
            FailureKind.INVALID_UNIQUE_PRED_PLACEMENT,
        ).map { Failure(it, source, AttributeCondition.Rule(it)) }
        assertEquals(FailureKind.entries.filter { it.category == FailureCategory.INVALID_INPUT }.toSet(), problems.map { it.kind }.toSet())
        problems.forEach(::assertDiagnostic)
        problems.forEach {
            assertTrue(FailureToFirRenderer.message(it).isNotBlank())
        }
    }

    @Test
    fun `construction escapes keep their origin and raw class symbol`() = withSources { sources ->
        for (kind in ConstructionEscapeFact.Kind.entries) {
            val escape = ConstructionEscapeFact(owner, kind, null, sources[1])
            val failure = constructionFailure(sources[0], FactPosition.RETURN, escape)
            assertSame(escape, assertIs<AttributeCondition.Construction>(failure.condition).escape)
            assertSame(owner, escape.escapingClass)
            assertEquals(listOf(RelatedLocation("construction escape", sources[1])), failure.related)
            assertDiagnostic(failure)
            assertTrue(FailureToFirRenderer.message(failure).isNotBlank())
        }
    }

    @Test
    fun `fact adapters retain raw types and recursively structured contracts`() = withSources { sources ->
        val nested = FunctionTypeFact(listOf(FunctionTypeFact.ParameterTypeFact(Locality.Local, null)), null)
        val expected = FunctionTypeFact(listOf(FunctionTypeFact.ParameterTypeFact(Locality.Global, nested)), nested)
        val failure = LocalityContractErrors.CONTEXT_LOCALITY_CONTRACT_MISMATCH.failure(
            sources[0], FactTarget.Context(type), expected, nested,
        )
        val condition = assertIs<AttributeCondition.ContractMismatch>(failure.condition)
        assertSame(type, assertIs<FactTarget.Context>(condition.target).type)
        assertEquals(Locality.Global, condition.expected!!.parameters.single().fact)
        assertEquals(Locality.Local, condition.expected!!.parameters.single().function!!.parameters.single().fact)
        assertEquals(Locality.Local, condition.expected!!.result!!.parameters.single().fact)
        assertDiagnostic(failure)
        assertTrue(FailureToFirRenderer.message(failure).isNotBlank())
    }

    @Test
    fun `move origins are deduplicated and ordered without changing primary highlighting`() = withSources { sources ->
        val failure = movedAccessFailure(sources[2], path, listOf(sources[1], sources[0], sources[1]))
        assertSame(sources[2], failure.primary)
        assertEquals(sources.take(2), failure.related.map { it.source })
        assertEquals(listOf("move", "move"), failure.related.map { it.role })
        assertSame(owner, assertIs<AttributeCondition.Path>(failure.condition).path.single())
        assertDiagnostic(failure)
        val withoutMoves = movedAccessFailure(sources[2], path, emptyList())
        assertEquals(FailureToFirRenderer.message(withoutMoves), FailureToFirRenderer.message(failure))
    }

    @Test
    fun `capture boundaries remain separate from the use location`() = withSources { sources ->
        val failures = listOf(
            localCaptureFailure(sources[0], owner, owner, sources[1]),
            uniqueCaptureFailure(sources[0], path, sources[1]),
        )
        failures.forEach {
            assertSame(sources[0], it.primary)
            assertEquals(listOf(RelatedLocation("capture boundary", sources[1])), it.related)
            assertDiagnostic(it)
        }
        assertTrue(uniqueCaptureFailure(sources[0], path, null).related.isEmpty())
        val local = assertIs<AttributeCondition.LocalCapture>(failures[0].condition)
        assertSame(owner, local.symbol)
        assertSame(owner, local.boundary)
    }

    @Test
    fun `each collision reports the opposite argument as a related location`() = withSources { sources ->
        for (kind in listOf(FailureKind.INVALID_DUPLICATE_UNIQUE_ARGUMENT, FailureKind.INVALID_OVERLAPPING_UNIQUE_ARGUMENTS)) {
            val left = argumentCollisionFailure(sources[0], kind, path, path + owner, sources[1])
            val right = argumentCollisionFailure(sources[1], kind, path + owner, path, sources[0])
            assertEquals(listOf(RelatedLocation("colliding argument", sources[1])), left.related)
            assertEquals(listOf(RelatedLocation("colliding argument", sources[0])), right.related)
            assertEquals(path + owner, assertIs<AttributeCondition.Path>(left.condition).other)
            assertDiagnostic(left)
            assertDiagnostic(right)
        }
    }
}
