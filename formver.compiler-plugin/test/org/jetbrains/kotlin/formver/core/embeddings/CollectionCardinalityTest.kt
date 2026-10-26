package org.jetbrains.kotlin.formver.core.embeddings

import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionCardinalityTest {
    @Test
    fun `mathematical collection cardinalities must fit their Int result`() {
        val operators = listOf(
            OperatorExpEmbeddings.MultisetSize,
            OperatorExpEmbeddings.MultisetCount,
        )
        for (operator in operators) {
            val function = operator.refsOperation
            // Checked operators retain their preconditions when lowered to native Viper expressions.
            assertTrue(function.checked)
            val projectedArgs = function.formalArgs.zip(function.argsInjections) { arg, injection ->
                injection.fromRef(Exp.LocalVar(arg.name, arg.type))
            }
            val cardinality = function.original.toFuncApp(projectedArgs)
            for (value in listOf(0L, 1L, Int.MAX_VALUE.toLong(), Int.MAX_VALUE.toLong() + 1, 1L shl 32)) {
                val evaluator = CardinalityEvaluator(mapOf(cardinality to value))
                assertEquals(value <= Int.MAX_VALUE, function.pres.all(evaluator::boolean), "${function.name}: $value")
            }
        }
    }

    @Test
    fun `sequence injection bounds lengths without typing oversized native sequences`() {
        val domain = RuntimeTypeDomain(TypeResolver().apply { markIterationSequenceUsed() })
        val injection = RuntimeTypeDomain.iterationSequenceInjection
        val quantifiers = domain.axioms.mapNotNull { it.exp as? Exp.Forall }
        val introduction = quantifiers.single {
            it.variables.singleOrNull()?.type == Type.Seq(Type.Ref) &&
                (it.exp as? Exp.Implies)?.right is Exp.DomainFuncApp
        }
        val nativeSequence = introduction.variables.single().let { Exp.LocalVar(it.name, it.type) }
        val projection = quantifiers.single {
            val bound = (it.exp as? Exp.Implies)?.right as? Exp.LeCmp
            val length = bound?.left as? Exp.SeqLength
            (length?.seq as? Exp.DomainFuncApp)?.function == injection.fromRef
        }
        val reference = projection.variables.single().let { Exp.LocalVar(it.name, it.type) }
        for (length in listOf(0L, Int.MAX_VALUE.toLong(), Int.MAX_VALUE.toLong() + 1)) {
            val fits = length <= Int.MAX_VALUE
            for (typed in listOf(false, true)) {
                val intro = CardinalityEvaluator(mapOf(Exp.SeqLength(nativeSequence) to length), typed)
                assertEquals(!fits || typed, intro.boolean(introduction.exp))
                val project = CardinalityEvaluator(mapOf(Exp.SeqLength(injection.fromRef(reference)) to length), typed)
                assertEquals(!typed || fits, project.boolean(projection.exp))
            }
        }
    }

    @Test
    fun `sequence append checks the combined length`() {
        val function = OperatorExpEmbeddings.IterationSequenceAppend.refsOperation
        assertTrue(function.checked)
        val lengths = function.formalArgs.zip(function.argsInjections) { arg, injection ->
            Exp.SeqLength(injection.fromRef(Exp.LocalVar(arg.name, arg.type)))
        }
        val maximum = Int.MAX_VALUE.toLong()
        for ((left, right) in listOf(0L to maximum, maximum to 0L, maximum - 1 to 1L, maximum to 1L, 1L to maximum)) {
            val evaluator = CardinalityEvaluator(mapOf(lengths[0] to left, lengths[1] to right))
            assertEquals(left + right <= maximum, function.pres.all(evaluator::boolean), "$left + $right")
        }
    }

    /** Interpret cardinalities without constructing collections with billions of elements. */
    private class CardinalityEvaluator(val values: Map<Exp, Long>, val typed: Boolean = true) {
        private fun integer(exp: Exp): Long = values[exp] ?: when (exp) {
            is Exp.IntLit -> exp.value
            is Exp.Add -> integer(exp.left) + integer(exp.right)
            else -> error("Unexpected integer expression: $exp")
        }

        fun boolean(exp: Exp): Boolean = when (exp) {
            is Exp.WithInfo -> boolean(exp.inner)
            is Exp.And -> boolean(exp.left) && boolean(exp.right)
            is Exp.Implies -> !boolean(exp.left) || boolean(exp.right)
            is Exp.LeCmp -> integer(exp.left) <= integer(exp.right)
            is Exp.GeCmp -> integer(exp.left) >= integer(exp.right)
            is Exp.DomainFuncApp -> {
                check(exp.function == RuntimeTypeDomain.isSubtype)
                typed
            }
            else -> error("Unexpected precondition: $exp")
        }
    }
}
