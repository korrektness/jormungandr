package org.jetbrains.kotlin.formver.core.embeddings

import org.jetbrains.kotlin.formver.core.embeddings.expression.BitwiseOperators
import org.jetbrains.kotlin.formver.core.embeddings.expression.BitwiseOperators.Operation
import org.jetbrains.kotlin.formver.core.embeddings.expression.BitwiseOperators.Shift
import org.jetbrains.kotlin.formver.core.embeddings.expression.BitwiseOperators.Width
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Info
import org.jetbrains.kotlin.formver.viper.ast.Position
import org.jetbrains.kotlin.formver.viper.ast.Type
import org.jetbrains.kotlin.formver.viper.ast.Var
import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Viper encodings of `and`, `or`, `xor`, `inv` and the shifts, evaluated on integers, agree with the JVM. */
class BitwiseOperatorsTest {
    private val x = Exp.LocalVar(TestName("x"), Type.Int)
    private val y = Exp.LocalVar(TestName("y"), Type.Int)

    private val intSamples: List<Long> = (listOf(0, 1, -1, 2, -2, 7, 0x7F, 0x80, 0xFF, -128, -256, 0x5555, Int.MAX_VALUE, Int.MIN_VALUE,
        Int.MAX_VALUE - 1, Int.MIN_VALUE + 1) + Random(1).let { r -> List(24) { r.nextInt() } }).map { it.toLong() }
    private val longSamples: List<Long> = intSamples + listOf(Long.MAX_VALUE, Long.MIN_VALUE, Long.MIN_VALUE + 1, 1L shl 40, -(1L shl 40)) +
        Random(2).let { r -> List(24) { r.nextLong() } }

    private fun samples(width: Width) = if (width == Width.Int) intSamples else longSamples

    private fun jvm(width: Width, operation: Operation, a: Long, b: Long): Long = when (width) {
        Width.Int -> when (operation) {
            Operation.And -> a.toInt() and b.toInt()
            Operation.Or -> a.toInt() or b.toInt()
            Operation.Xor -> a.toInt() xor b.toInt()
        }.toLong()
        Width.Long -> when (operation) {
            Operation.And -> a and b
            Operation.Or -> a or b
            Operation.Xor -> a xor b
        }
    }

    private fun jvm(width: Width, shift: Shift, a: Long, d: Int): Long = when (width) {
        Width.Int -> when (shift) {
            Shift.Left -> a.toInt() shl d
            Shift.Right -> a.toInt() shr d
            Shift.UnsignedRight -> a.toInt() ushr d
        }.toLong()
        Width.Long -> when (shift) {
            Shift.Left -> a shl d
            Shift.Right -> a shr d
            Shift.UnsignedRight -> a ushr d
        }
    }

    private fun lit(value: Long): Exp = if (value < 0 && value != Long.MIN_VALUE) Exp.Minus(Exp.IntLit(-value)) else Exp.IntLit(value)

    private fun binary(width: Width, operation: Operation, a: Exp, b: Exp) =
        BitwiseOperators.binary(width, operation, a, b, Position.NoPosition, Info.NoInfo)

    private fun shift(width: Width, shift: Shift, a: Exp, d: Exp) =
        BitwiseOperators.shift(width, shift, a, d, Position.NoPosition, Info.NoInfo)

    private fun check(expected: Long, exp: Exp, vararg env: Pair<Exp.LocalVar, Long>) {
        val actual = Evaluator(env.associate { (v, value) -> v.name to BigInteger.valueOf(value) }).int(exp)
        assertEquals(BigInteger.valueOf(expected), actual, "$exp with ${env.toList()}")
    }

    @Test
    fun `logical operations agree with the JVM for variable and literal operands`() {
        for (width in Width.entries) for (operation in Operation.entries) {
            val values = samples(width)
            for (a in values) for (b in values.take(20)) {
                val expected = jvm(width, operation, a, b)
                check(expected, binary(width, operation, x, y), x to a, y to b)
                check(expected, binary(width, operation, x, lit(b)), x to a)
                check(expected, binary(width, operation, lit(a), x), x to b)
                check(expected, binary(width, operation, lit(a), lit(b)))
            }
        }
    }

    @Test
    fun `inversion agrees with the JVM`() {
        for (width in Width.entries) for (a in samples(width)) {
            val expected = if (width == Width.Int) a.toInt().inv().toLong() else a.inv()
            check(expected, BitwiseOperators.inv(x), x to a)
            check(expected, BitwiseOperators.inv(lit(a)))
        }
    }

    @Test
    fun `shifts mask literal and variable distances as the JVM does`() {
        for (width in Width.entries) for (s in Shift.entries) for (a in samples(width)) {
            for (d in listOf(0, 1, 7, 8, 31, 32, 33, 39, 63, 64, 65, -1, -31)) {
                val expected = jvm(width, s, a, d)
                check(expected, shift(width, s, x, lit(d.toLong())), x to a)
                check(expected, shift(width, s, x, y), x to a, y to d.toLong())
                check(expected, shift(width, s, lit(a), lit(d.toLong())))
            }
        }
    }

    @Test
    fun `operands combined from masks and shifts agree with the JVM`() {
        for (operation in Operation.entries) for (a in intSamples) for (b in intSamples.take(16)) {
            disjointCombinations(operation, a, b)
        }
        for (width in Width.entries) for (operation in Operation.entries) for (a in samples(width)) {
            maskedShifts(width, operation, a)
        }
    }

    private fun disjointCombinations(operation: Operation, a: Long, b: Long) {
        val ai = a.toInt()
        val bi = b.toInt()
        // Disjoint by their possible bits: a low byte and a value shifted past it.
        val lowByte = binary(Width.Int, Operation.And, x, Exp.IntLit(0xFF))
        val shifted = shift(Width.Int, Shift.Left, y, Exp.IntLit(8))
        check(jvm(Width.Int, operation, (ai and 0xFF).toLong(), (bi shl 8).toLong()),
            binary(Width.Int, operation, lowByte, shifted), x to a, y to b)
        // An unconstrained value against one with its low byte clear: the guard holds only for some samples.
        check(jvm(Width.Int, operation, a, (bi shl 8).toLong()), binary(Width.Int, operation, x, shifted), x to a, y to b)
        // A low part below a variable shift distance, and a value shifted by that distance.
        val low = binary(Width.Int, Operation.And, x, Exp.IntLit(0x1F))
        for (d in listOf(0, 5, 8, 31)) {
            val byDistance = shift(Width.Int, Shift.Left, y, Exp.Add(Exp.IntLit(d.toLong()), Exp.IntLit(0)))
            check(jvm(Width.Int, operation, (ai and 0x1F).toLong(), (bi shl d).toLong()),
                binary(Width.Int, operation, low, byDistance), x to a, y to b)
        }
    }

    @Test
    fun `a low mask built from a variable shift agrees with the JVM`() {
        for (width in Width.entries) for (a in samples(width)) for (d in listOf(0, 1, 7, 30, 31, 32, 62, 63, 64, -1)) {
            val mask = Exp.Sub(shift(width, Shift.Left, Exp.IntLit(1), y), Exp.IntLit(1))
            // The mask under a let, as a checked subtraction in a specification produces it.
            val boundMask = Exp.LetBinding(Var(TestName("m"), Type.Int).decl(), Exp.IntLit(0), mask)
            val expected = if (width == Width.Int) (a.toInt() and ((1 shl d) - 1)).toLong() else a and ((1L shl d) - 1)
            check(expected, binary(width, Operation.And, x, mask), x to a, y to d.toLong())
            check(expected, binary(width, Operation.And, boundMask, Exp.Add(x, Exp.IntLit(0))), x to a, y to d.toLong())
        }
    }

    /** Masks with several runs of ones, applied to shifted values. */
    private fun maskedShifts(width: Width, operation: Operation, a: Long) {
        for (mask in listOf(0x5555L, -0x100L, 0x0F0F_0000L, Int.MIN_VALUE.toLong())) {
            val m = if (width == Width.Int) mask.toInt().toLong() else mask
            check(jvm(width, operation, jvm(width, Shift.UnsignedRight, a, 3), m),
                binary(width, operation, shift(width, Shift.UnsignedRight, x, Exp.IntLit(3)), lit(m)), x to a)
            check(jvm(width, operation, jvm(width, Shift.Right, a, 9), m),
                binary(width, operation, shift(width, Shift.Right, x, Exp.IntLit(9)), lit(m)), x to a)
        }
    }

    private data class TestName(val text: String) : SymbolicName {
        override val inViper: Boolean = true
        override val candidates = emptyList<org.jetbrains.kotlin.formver.viper.CandidateName>()
        override val children = emptyList<org.jetbrains.kotlin.formver.viper.AnyName>()
    }

    /** Viper's integer semantics: unbounded, with Euclidean division and remainder. */
    private class Evaluator(private val env: Map<SymbolicName, BigInteger>) {
        private val functions = BitwiseOperators.allFunctions.associateBy { it.name }

        fun int(e: Exp): BigInteger = when (e) {
            is Exp.IntLit -> BigInteger.valueOf(e.value)
            is Exp.LocalVar -> env.getValue(e.name)
            is Exp.WithInfo -> int(e.inner)
            is Exp.Minus -> int(e.arg).negate()
            is Exp.Add -> int(e.left) + int(e.right)
            is Exp.Sub -> int(e.left) - int(e.right)
            is Exp.Mul -> int(e.left) * int(e.right)
            is Exp.Div -> euclid(int(e.left), int(e.right)).first
            is Exp.Mod -> euclid(int(e.left), int(e.right)).second
            is Exp.TernaryExp -> if (bool(e.condExp)) int(e.thenExp) else int(e.elseExp)
            is Exp.LetBinding -> Evaluator(env + (e.variable.name to int(e.varExp))).int(e.body)
            is Exp.FuncApp -> {
                val function = functions.getValue(e.functionName)
                val bound = function.formalArgs.zip(e.args) { decl, arg -> decl.name to int(arg) }
                Evaluator(env + bound).int(function.body!!)
            }
            else -> error("Not an integer expression: $e")
        }

        fun bool(e: Exp): Boolean = when (e) {
            is Exp.WithInfo -> bool(e.inner)
            is Exp.And -> bool(e.left) && bool(e.right)
            is Exp.Or -> bool(e.left) || bool(e.right)
            is Exp.Not -> !bool(e.arg)
            is Exp.EqCmp -> int(e.left) == int(e.right)
            is Exp.NeCmp -> int(e.left) != int(e.right)
            is Exp.LtCmp -> int(e.left) < int(e.right)
            is Exp.LeCmp -> int(e.left) <= int(e.right)
            is Exp.GtCmp -> int(e.left) > int(e.right)
            is Exp.GeCmp -> int(e.left) >= int(e.right)
            else -> error("Not a boolean expression: $e")
        }

        private fun euclid(a: BigInteger, b: BigInteger): Pair<BigInteger, BigInteger> {
            val r = a.mod(b.abs())
            return (a - r).divide(b) to r
        }
    }
}
