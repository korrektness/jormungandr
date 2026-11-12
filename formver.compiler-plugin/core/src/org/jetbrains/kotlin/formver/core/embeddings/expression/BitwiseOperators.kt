/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain

import org.jetbrains.kotlin.formver.core.domains.FunctionBuilder
import org.jetbrains.kotlin.formver.core.linearization.subExps
import org.jetbrains.kotlin.formver.core.names.BitwiseOperandName
import org.jetbrains.kotlin.formver.core.names.DomainAssociatedFuncName
import org.jetbrains.kotlin.formver.viper.ast.*
import org.jetbrains.kotlin.formver.viper.ast.Function

/**
 * Kotlin's `and`, `or`, `xor`, `inv`, `shl`, `shr` and `ushr` on `Int` and `Long`, as two's-complement operations on
 * Viper's mathematical integers.
 *
 * The encoding stays in integer arithmetic: converting between integers and bit-vectors (`int2bv`, `bv2nat`) makes
 * Z3 time out on small byte-codec proofs. A literal operand or shift distance becomes division and remainder by
 * constant powers of two. Other operands go through [functions], whose bodies decide an operand of 0 or -1, and
 * operands that occupy disjoint bit ranges, before they sum the result bit by bit. Z3 instantiates a function body
 * only where its application is relevant, so a decided case does not pay for the bitwise sum.
 */
object BitwiseOperators {
    private const val LONG_SIGN_BIT = Long.SIZE_BITS - 1

    enum class Width(val bits: Int) {
        Int(kotlin.Int.SIZE_BITS), Long(kotlin.Long.SIZE_BITS);

        val modulus get() = pow2(bits)
        val signBit get() = pow2(bits - 1)
        val pretype get() = if (this == Int) IntegerKind.Int.pretype else IntegerKind.Long.pretype

        /** The literal's value as this width's two's-complement number. */
        fun normalize(value: kotlin.Long): kotlin.Long = if (this == Int) value.toInt().toLong() else value
    }

    enum class Operation { And, Or, Xor }
    enum class Shift { Left, Right, UnsignedRight }

    /** `2^k` for `0 <= k <= 64`; Viper literals hold host `Long`s, so the two largest are computed. */
    fun pow2(k: Int): Exp = when {
        k < LONG_SIGN_BIT -> Exp.IntLit(1L shl k)
        k == LONG_SIGN_BIT -> Exp.Minus(Exp.IntLit(Long.MIN_VALUE))
        else -> Exp.Mul(Exp.IntLit(2), pow2(LONG_SIGN_BIT))
    }

    private fun Exp.constant(): Long? = when (this) {
        is Exp.IntLit -> value
        is Exp.Minus -> (arg as? Exp.IntLit)?.value?.takeIf { it != Long.MIN_VALUE }?.let { -it }
        is Exp.WithInfo -> inner.constant()
        else -> null
    }

    private fun lit(value: Long): Exp =
        if (value == Long.MIN_VALUE) Exp.IntLit(value) else if (value < 0) Exp.Minus(Exp.IntLit(-value)) else Exp.IntLit(value)

    /** Binds a compound [value] so that [body] may mention it several times without copying it. */
    private fun bind(value: Exp, body: (Exp) -> Exp): Exp {
        if (value.isAtomic()) return body(value)
        // Viper rejects a binder that shadows another, so a binder is named by its nesting depth.
        val variable = Var(BitwiseOperandName(value.bindingDepth() + 1), Type.Int)
        return Exp.LetBinding(variable.decl(), value, body(variable.use()))
    }

    /** A variable, a literal, or an unboxed variable: cheap to repeat. */
    private fun Exp.isAtomic(): Boolean = this is Exp.LocalVar || constant() != null ||
            this is Exp.DomainFuncApp && args.size == 1 && args.single() is Exp.LocalVar

    private fun Exp.bindingDepth(): Int {
        val own = ((this as? Exp.LetBinding)?.variable?.name as? BitwiseOperandName)?.n ?: 0
        return maxOf(own, subExps().maxOfOrNull { it.bindingDepth() } ?: 0)
    }

    /** The value in `[-2^(bits-1), 2^(bits-1))` congruent to [value] modulo `2^bits`. */
    private fun wrap(width: Width, value: Exp): Exp = bind(Exp.Mod(value, width.modulus)) { u ->
        Exp.TernaryExp(u ge width.signBit, Exp.Sub(u, width.modulus), u)
    }

    /** `x shl s`; the remainder is left out when no possibly set bit is shifted out. */
    private fun leftShift(width: Width, x: Exp, s: Int): Exp {
        val product = Exp.Mul(x, pow2(s))
        val bits = width.possibleBits(x)
        return when {
            bits ushr (width.bits - s) != 0L -> wrap(width, product)
            (bits shl s) ushr (width.bits - 1) and 1L == 0L -> product
            else -> bind(product) { p -> Exp.TernaryExp(p ge width.signBit, Exp.Sub(p, width.modulus), p) }
        }
    }

    private fun fold(width: Width, operation: Operation, a: Long, b: Long): Long = width.normalize(
        when (operation) {
            Operation.And -> a and b
            Operation.Or -> a or b
            Operation.Xor -> a xor b
        }
    )

    /** `x and mask`: each maximal run of ones `[lo, hi)` in a non-negative mask selects `x mod 2^hi - x mod 2^lo`. */
    private fun andConstant(width: Width, x: Exp, mask: Long): Exp = when {
        mask == 0L -> Exp.IntLit(0)
        mask == -1L -> x
        // Two's complement: `x and m == x - (x and m.inv())`, and `m.inv()` is non-negative.
        mask < 0 -> bind(x) { v -> Exp.Sub(v, andConstant(width, v, mask.inv())) }
        mask and (mask + 1) == 0L ->
            if (width.possibleBits(x) and mask.inv() == 0L) x else Exp.Mod(x, pow2(Long.SIZE_BITS - mask.countLeadingZeroBits()))
        else -> bind(x) { v ->
            runs(mask).map { (lo, hi) ->
                if (lo == 0) Exp.Mod(v, pow2(hi)) else Exp.Sub(Exp.Mod(v, pow2(hi)), Exp.Mod(v, pow2(lo)))
            }.reduce<Exp, Exp> { sum, term -> Exp.Add(sum, term) }
        }
    }

    private fun runs(mask: Long): List<Pair<Int, Int>> = buildList {
        var bit = 0
        while (bit < LONG_SIGN_BIT) {
            if (mask shr bit and 1L == 0L) { bit++; continue }
            val lo = bit
            while (bit < LONG_SIGN_BIT && mask shr bit and 1L == 1L) bit++
            add(lo to bit)
        }
    }

    private fun withConstant(width: Width, operation: Operation, x: Exp, m: Long): Exp = when (operation) {
        Operation.And -> andConstant(width, x, m)
        // Over the integers, `x + m == (x or m) + (x and m) == (x xor m) + 2 * (x and m)`.
        Operation.Or -> when (m) {
            0L -> x
            -1L -> lit(-1)
            else -> bind(x) { v -> Exp.Sub(Exp.Add(v, lit(m)), andConstant(width, v, m)) }
        }
        Operation.Xor -> when (m) {
            0L -> x
            -1L -> Exp.Sub(Exp.Minus(x), Exp.IntLit(1))
            else -> bind(x) { v -> Exp.Sub(Exp.Add(v, lit(m)), Exp.Mul(Exp.IntLit(2), andConstant(width, v, m))) }
        }
    }

    fun binary(width: Width, operation: Operation, a: Exp, b: Exp, pos: Position, info: Info): Exp {
        val left = a.constant()
        val right = b.constant()
        val masked = if (operation == Operation.And) lowMask(width, a, b) ?: lowMask(width, b, a) else null
        return when {
            left != null && right != null -> lit(fold(width, operation, left, right))
            right != null -> withConstant(width, operation, a, right)
            left != null -> withConstant(width, operation, b, left)
            // Operands with no bit in common add up to the same value under `or` and `xor`.
            width.possibleBits(a) and width.possibleBits(b) == 0L -> when (operation) {
                Operation.And -> Exp.IntLit(0)
                Operation.Or, Operation.Xor -> Exp.Add(a, b, pos, info)
            }
            masked != null -> masked
            else -> {
                val general = functions.getValue(width to operation).toFuncApp(listOf(a, b), pos, info)
                // `a op b` with `0 <= a < 2^k` and the low k bits of b clear has no bit in common, which a solver
                // finds slowly by itself.
                val (low, shifted, power) = listOf(a to b, b to a).firstNotNullOfOrNull { (low, shifted) ->
                    width.lowZeroBound(shifted)?.let { Triple(low, shifted, it) }
                } ?: return general
                val disjoint = Exp.And(Exp.IntLit(0) le low, low lt power)
                Exp.TernaryExp(disjoint, if (operation == Operation.And) Exp.IntLit(0) else Exp.Add(low, shifted), general, pos, info)
            }
        }
    }

    /** `2^k` for the number k of low bits that are clear in [e], from a variable left shift or from its possible bits. */
    private fun Width.lowZeroBound(e: Exp): Exp? {
        e.variableLeftShift(this)?.let { return powers.getValue(this).toFuncApp(listOf(it)) }
        val bits = possibleBits(e)
        val k = bits.countTrailingZeroBits()
        return if (bits != 0L && k in 1 until this.bits) pow2(k) else null
    }

    /**
     * `x and ((1 shl s) - 1)` as `x mod 2^s`, keeping the lets around the mask so that their checks still apply. For
     * `s mod bits == bits - 1` the wrapped mask is the maximum value, whose `and` is also the remainder by `2^s`.
     */
    private fun lowMask(width: Width, x: Exp, mask: Exp): Exp? {
        val depth = maxOf(x.bindingDepth(), mask.bindingDepth()) + 1
        val bound = if (x.isAtomic()) x else Var(BitwiseOperandName(depth), Type.Int).use()
        val result = mask.replaceLowMask(width) { s -> Exp.Mod(bound, powers.getValue(width).toFuncApp(listOf(s))) } ?: return null
        return if (bound === x) result else Exp.LetBinding(Var(BitwiseOperandName(depth), Type.Int).decl(), x, result)
    }

    /** [mask] with its innermost `(1 shl s) - 1` replaced by [replacement] of `s`, or null for another shape. */
    private fun Exp.replaceLowMask(width: Width, replacement: (Exp) -> Exp): Exp? = when (this) {
        is Exp.WithInfo -> inner.replaceLowMask(width, replacement)
        is Exp.LetBinding -> body.replaceLowMask(width, replacement)?.let { Exp.LetBinding(variable, varExp, it, pos, info) }
        is Exp.Sub -> {
            val shift = left.unboxed() as? Exp.FuncApp
            val leftShift = shiftFunctions.getValue(width to Shift.Left).name
            if (right.unboxed().constant() == 1L && shift?.functionName == leftShift && shift.args[0].unboxed().constant() == 1L) {
                replacement(shift.args[1])
            } else null
        }
        else -> null
    }

    /** [e] without info and without an unboxing of a boxing. */
    private fun Exp.unboxed(): Exp = when {
        this is Exp.WithInfo -> inner.unboxed()
        this is Exp.DomainFuncApp && args.size == 1 && (args[0] as? Exp.DomainFuncApp)?.args?.size == 1 &&
            boxings.any { it.fromRef == function && it.toRef == (args[0] as Exp.DomainFuncApp).function } ->
            (args[0] as Exp.DomainFuncApp).args[0].unboxed()
        else -> this
    }

    private val boxings get() = listOf(RuntimeTypeDomain.intInjection, RuntimeTypeDomain.longInjection)

    /** The distance of a left shift by a variable distance. */
    private fun Exp.variableLeftShift(width: Width): Exp? =
        (this as? Exp.FuncApp)?.takeIf { it.functionName == shiftFunctions.getValue(width to Shift.Left).name }?.args?.get(1)

    private val Width.allBits get() = -1L ushr (Long.SIZE_BITS - bits)

    private fun Exp.powerOfTwo(): Int? = constant()?.takeIf { it > 0 && it and (it - 1) == 0L }?.countTrailingZeroBits()
        ?: (this as? Exp.Minus)?.let { if ((it.arg as? Exp.IntLit)?.value == Long.MIN_VALUE) LONG_SIGN_BIT else null }
        ?: (this as? Exp.Mul)?.let { if (it.left.constant() == 2L && it.right.powerOfTwo() == LONG_SIGN_BIT) Long.SIZE_BITS else null }

    private fun lowBits(width: Width, k: Int): Long = if (k >= width.bits) width.allBits else (1L shl k) - 1

    /**
     * The bits of [e]'s two's-complement pattern that may be set, recovered from the shapes this object builds and
     * from remainders by powers of two. A shape it does not recognise may set every bit.
     */
    private fun Width.possibleBits(e: Exp): Long {
        e.constant()?.let { return it and allBits }
        return when (e) {
            is Exp.WithInfo -> possibleBits(e.inner)
            is Exp.Mod -> e.right.powerOfTwo()?.let { k -> possibleBits(e.left) and lowBits(this, k) } ?: allBits
            is Exp.Sub -> {
                // `x mod 2^hi - x mod 2^lo`: bits `[lo, hi)` of `x`.
                val hi = (e.left as? Exp.Mod)?.takeIf { it.left == (e.right as? Exp.Mod)?.left }?.right?.powerOfTwo()
                val lo = (e.right as? Exp.Mod)?.right?.powerOfTwo()
                if (hi != null && lo != null && lo < hi) possibleBits((e.left as Exp.Mod).left) and lowBits(this, hi) and lowBits(this, lo).inv()
                else allBits
            }
            is Exp.Add -> {
                val left = possibleBits(e.left)
                val right = possibleBits(e.right)
                if (left and right == 0L) left or right else allBits
            }
            is Exp.Div -> {
                val s = e.right.powerOfTwo() ?: return allBits
                val dividend = e.left
                // An unsigned shift divides the pattern read as unsigned; a signed one extends the sign.
                if (dividend is Exp.Mod && dividend.right.powerOfTwo() == bits) possibleBits(dividend.left) ushr s
                else {
                    val bitsOf = possibleBits(dividend)
                    val sign = bitsOf ushr (bits - 1) and 1L
                    ((bitsOf ushr s) or (if (sign == 1L) allBits and (allBits ushr s).inv() else 0L)) and allBits
                }
            }
            // In range, a product with `2^s` is the pattern shifted left.
            is Exp.Mul -> e.right.powerOfTwo()?.let { s -> possibleBits(e.left) shl s and allBits } ?: allBits
            // A left shift: `let u == (x * 2^s) [mod 2^bits] in (u >= 2^(bits-1) ? u - 2^bits : u)`.
            is Exp.LetBinding -> {
                val wrapped = e.varExp.let { if (it is Exp.Mod && it.right.powerOfTwo() == bits) it.left else it }
                if (e.body is Exp.TernaryExp && wrapped is Exp.Mul) possibleBits(wrapped) else allBits
            }
            is Exp.FuncApp -> {
                val operation = functions.entries.firstOrNull { it.value.name == e.functionName && it.key.first == this }?.key?.second
                when (operation) {
                    Operation.And -> possibleBits(e.args[0]) and possibleBits(e.args[1])
                    Operation.Or, Operation.Xor -> possibleBits(e.args[0]) or possibleBits(e.args[1])
                    null -> allBits
                }
            }
            else -> allBits
        }
    }

    fun inv(a: Exp): Exp = a.constant()?.let { lit(it.inv()) } ?: Exp.Sub(Exp.Minus(a), Exp.IntLit(1))

    private fun shiftConstant(width: Width, shift: Shift, x: Exp, distance: Int): Exp {
        val s = distance and (width.bits - 1)
        x.constant()?.let { value ->
            return lit(
                when (width) {
                    Width.Int -> when (shift) {
                        Shift.Left -> value.toInt() shl s
                        Shift.Right -> value.toInt() shr s
                        Shift.UnsignedRight -> value.toInt() ushr s
                    }.toLong()
                    Width.Long -> when (shift) {
                        Shift.Left -> value shl s
                        Shift.Right -> value shr s
                        Shift.UnsignedRight -> value ushr s
                    }
                }
            )
        }
        if (s == 0) return x
        // Viper's division and remainder by a positive divisor round toward negative infinity.
        return when (shift) {
            Shift.Left -> leftShift(width, x, s)
            Shift.Right -> Exp.Div(x, pow2(s))
            Shift.UnsignedRight -> Exp.Div(Exp.Mod(x, width.modulus), pow2(s))
        }
    }

    fun shift(width: Width, shift: Shift, x: Exp, distance: Exp, pos: Position, info: Info): Exp {
        val d = distance.constant() ?: return shiftFunctions.getValue(width to shift).toFuncApp(listOf(x, distance), pos, info)
        return shiftConstant(width, shift, x, d.toInt())
    }

    private fun name(prefix: String, width: Width, suffix: String = "") = DomainAssociatedFuncName("$prefix${width.name}$suffix")

    private fun bit(x: Exp, i: Int): Exp = Exp.Mod(if (i == 0) x else Exp.Div(x, pow2(i)), Exp.IntLit(2))

    /** The bitwise result as a sum of the bits' weights; the top bit weighs `-2^(bits-1)`. */
    private fun bitSum(width: Width, operation: Operation): Function = FunctionBuilder.build(name("bit${operation.name}Sum", width)) {
        val a = argument(Type.Int)
        val b = argument(Type.Int)
        returns(Type.Int)
        body {
            (0 until width.bits).map { i ->
                val ones = Exp.Add(bit(a, i), bit(b, i))
                val set = when (operation) {
                    Operation.And -> ones eq Exp.IntLit(2)
                    Operation.Or -> ones ge Exp.IntLit(1)
                    Operation.Xor -> ones eq Exp.IntLit(1)
                }
                val weight = if (i == width.bits - 1) Exp.Minus(width.signBit) else pow2(i)
                Exp.TernaryExp(set, weight, Exp.IntLit(0))
            }.reduce<Exp, Exp> { sum, term -> Exp.Add(sum, term) }
        }
    }

    /** Some `k` has one operand in `[0, 2^k)` and the other a multiple of `2^k`, so no bit is set in both. */
    private fun disjoint(width: Width, a: Exp, b: Exp): Exp = (1 until width.bits).flatMap { k ->
        fun below(x: Exp) = Exp.And(Exp.IntLit(0) le x, x lt pow2(k))
        fun above(x: Exp) = Exp.Mod(x, pow2(k)) eq Exp.IntLit(0)
        listOf(Exp.And(below(a), above(b)), Exp.And(below(b), above(a)))
    }.reduce<Exp, Exp> { any, case -> Exp.Or(any, case) }

    private val sums: Map<Pair<Width, Operation>, Function> = Width.entries.flatMap { w ->
        Operation.entries.map { op -> (w to op) to bitSum(w, op) }
    }.toMap()

    private val functions: Map<Pair<Width, Operation>, Function> = sums.mapValues { (key, sum) ->
        val (width, operation) = key
        FunctionBuilder.build(name("bit${operation.name}", width)) {
            val a = argument(Type.Int)
            val b = argument(Type.Int)
            returns(Type.Int)
            body {
                val zero = Exp.IntLit(0)
                val minusOne = lit(-1)
                fun cases(pairs: List<Pair<Exp, Exp>>, otherwise: Exp): Exp =
                    pairs.foldRight(otherwise) { (condition, value), rest -> Exp.TernaryExp(condition, value, rest) }
                fun not(x: Exp) = Exp.Sub(Exp.Minus(x), Exp.IntLit(1))
                val general = sum.toFuncApp(listOf(a, b))
                when (operation) {
                    // A low mask `2^k - 1`, such as `(1 shl k) - 1`, keeps the remainder by `2^k`.
                    Operation.And -> cases(
                        listOf((b eq zero) to zero, (b eq minusOne) to a, (a eq zero) to zero, (a eq minusOne) to b) +
                            (1 until width.bits).flatMap { k ->
                                val mask = Exp.Sub(pow2(k), Exp.IntLit(1))
                                listOf((b eq mask) to Exp.Mod(a, pow2(k)), (a eq mask) to Exp.Mod(b, pow2(k)))
                            } + (disjoint(width, a, b) to zero),
                        otherwise = general,
                    )
                    Operation.Or -> cases(
                        listOf(
                            (b eq zero) to a, (b eq minusOne) to minusOne, (a eq zero) to b, (a eq minusOne) to minusOne,
                            disjoint(width, a, b) to Exp.Add(a, b),
                        ),
                        otherwise = general,
                    )
                    Operation.Xor -> cases(
                        listOf(
                            (b eq zero) to a, (b eq minusOne) to not(a), (a eq zero) to b, (a eq minusOne) to not(b),
                            disjoint(width, a, b) to Exp.Add(a, b),
                        ),
                        otherwise = general,
                    )
                }
            }
        }
    }

    /**
     * `2^(d mod bits)` as a table of literals. Once the solver has split on the distance, the shift multiplies or
     * divides by a constant; a case split over shifted values instead is much slower in loops.
     */
    private val powers: Map<Width, Function> = Width.entries.associateWith { width ->
        FunctionBuilder.build(name("powerOfTwo", width)) {
            val distance = argument(Type.Int)
            returns(Type.Int)
            postcondition { result ge Exp.IntLit(1) }
            body {
                val masked = Exp.Mod(distance, Exp.IntLit(width.bits))
                (width.bits - 1 downTo 1).fold(Exp.IntLit(1) as Exp) { rest, k -> Exp.TernaryExp(masked eq Exp.IntLit(k), pow2(k), rest) }
            }
        }
    }

    /** A shift by a variable distance. */
    private val shiftFunctions: Map<Pair<Width, Shift>, Function> = Width.entries.flatMap { width ->
        Shift.entries.map { shift ->
            (width to shift) to FunctionBuilder.build(name("shift${shift.name}", width)) {
                val x = argument(Type.Int)
                val distance = argument(Type.Int)
                returns(Type.Int)
                body {
                    val power = powers.getValue(width).toFuncApp(listOf(distance))
                    when (shift) {
                        Shift.Left -> wrap(width, Exp.Mul(x, power))
                        Shift.Right -> Exp.Div(x, power)
                        Shift.UnsignedRight -> Exp.TernaryExp(
                            Exp.Mod(distance, Exp.IntLit(width.bits)) eq Exp.IntLit(0), x,
                            Exp.Div(Exp.Mod(x, width.modulus), power),
                        )
                    }
                }
            }
        }
    }.toMap()

    /** Declared in every program and removed when no operation calls them. */
    val allFunctions: List<Function> get() = sums.values + functions.values + powers.values + shiftFunctions.values

    /** Kotlin's operator names; each is unchecked, as its result is always representable. */
    val binaryTemplates: Map<Pair<Width, String>, BinaryOperatorExpEmbeddingTemplate> = Width.entries.flatMap { width ->
        val logical = Operation.entries.map { op ->
            (width to op.name.lowercase()) to buildBinaryOperator {
                setName("${op.name.lowercase()}${width.name}s")
                withSignature {
                    withParam { existing(width.pretype) }; withParam { existing(width.pretype) }
                    withReturnType { existing(width.pretype) }
                }
                viperImplementation { binary(width, op, args[0], args[1], pos, info) }
                expandInline()
            }
        }
        val shifts = mapOf(Shift.Left to "shl", Shift.Right to "shr", Shift.UnsignedRight to "ushr").map { (shift, kotlinName) ->
            (width to kotlinName) to buildBinaryOperator {
                setName("$kotlinName${width.name}")
                withSignature {
                    withParam { existing(width.pretype) }; withParam { int() }
                    withReturnType { existing(width.pretype) }
                }
                viperImplementation { shift(width, shift, args[0], args[1], pos, info) }
                expandInline()
            }
        }
        logical + shifts
    }.toMap()

    val invTemplates: Map<Width, UnaryOperatorExpEmbeddingTemplate> = Width.entries.associateWith { width ->
        buildUnaryOperator {
            setName("inv${width.name}")
            withSignature { withParam { existing(width.pretype) }; withReturnType { existing(width.pretype) } }
            viperImplementation { inv(args[0]) }
            expandInline()
        }
    }

    val templates get() = binaryTemplates.values + invTemplates.values
}
