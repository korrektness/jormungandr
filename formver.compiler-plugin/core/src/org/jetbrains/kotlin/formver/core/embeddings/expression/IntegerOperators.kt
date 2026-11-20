package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.core.domains.IntegerBounds
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.core.embeddings.asInfo
import org.jetbrains.kotlin.formver.core.embeddings.types.*
import org.jetbrains.kotlin.formver.viper.ast.*
import java.math.BigInteger

/** Kotlin overload widths, including promotion of Byte and Short arithmetic to Int and of UByte and UShort to UInt. */
enum class IntegerKind(val lower: BigInteger, val upper: BigInteger) {
    Byte(kotlin.Byte.MIN_VALUE.toLong(), kotlin.Byte.MAX_VALUE.toLong()),
    Short(kotlin.Short.MIN_VALUE.toLong(), kotlin.Short.MAX_VALUE.toLong()),
    Int(kotlin.Int.MIN_VALUE.toLong(), kotlin.Int.MAX_VALUE.toLong()),
    Long(kotlin.Long.MIN_VALUE, kotlin.Long.MAX_VALUE),
    Char(kotlin.Char.MIN_VALUE.code.toLong(), kotlin.Char.MAX_VALUE.code.toLong()),
    UByte(0, kotlin.UByte.MAX_VALUE.toLong()),
    UShort(0, kotlin.UShort.MAX_VALUE.toLong()),
    UInt(0, kotlin.UInt.MAX_VALUE.toLong()),
    ULong(BigInteger.ZERO, BigInteger(kotlin.ULong.MAX_VALUE.toString()));

    constructor(lower: kotlin.Long, upper: kotlin.Long) : this(BigInteger.valueOf(lower), BigInteger.valueOf(upper))

    val unsigned get() = this in listOf(UByte, UShort, UInt, ULong)

    val pretype: PretypeEmbedding
        get() = when (this) {
            Byte -> ByteTypeEmbedding
            Short -> ShortTypeEmbedding
            Int -> IntTypeEmbedding
            Long -> LongTypeEmbedding
            Char -> CharTypeEmbedding
            UByte -> UByteTypeEmbedding
            UShort -> UShortTypeEmbedding
            UInt -> UIntTypeEmbedding
            ULong -> ULongTypeEmbedding
        }
    val type get() = pretype.asTypeEmbedding()

    fun contains(other: IntegerKind) = lower <= other.lower && other.upper <= upper
    fun range(value: Exp) = Exp.And(value ge IntegerBounds.literal(lower), value le IntegerBounds.literal(upper))
    fun checkedRange(value: Exp, operation: String): Exp {
        val bounds = range(value) as Exp.And
        val info = SourceRole.Arithmetic(KotlinCondition.IntegerOverflow(operation, name, lower.toString(), upper.toString())).asInfo
        return Exp.And(Exp.WithInfo(bounds.left, info), Exp.WithInfo(bounds.right, info), info = info)
    }
}

object IntegerOperators {
    data class Binary(val receiver: IntegerKind, val argument: IntegerKind, val operation: String, val result: IntegerKind)
    data class Unary(val receiver: IntegerKind, val operation: String, val result: IntegerKind)

    private fun quotient(a: Exp, b: Exp): Exp {
        // Viper integers are unbounded, so negating a signed minimum is representable here.
        fun abs(x: Exp) = Exp.TernaryExp(x lt 0.toExp(), Exp.Minus(x), x)
        val q = Exp.Div(abs(a), abs(b))
        return Exp.TernaryExp(Exp.NeCmp(a lt 0.toExp(), b lt 0.toExp()), Exp.Minus(q), q)
    }

    /** Kotlin's [operation] on [a] and [b]; [unsigned] operands are non-negative, where Viper's `\` and `%` agree with Kotlin's. */
    private fun binaryValue(operation: String, a: Exp, b: Exp, unsigned: Boolean = false): Exp = when (operation) {
        "div" -> if (unsigned) Exp.Div(a, b) else quotient(a, b)
        "rem" -> if (unsigned) Exp.Mod(a, b) else Exp.Sub(a, Exp.Mul(quotient(a, b), b))
        "plus" -> Exp.Add(a, b)
        "minus" -> Exp.Sub(a, b)
        "times" -> Exp.Mul(a, b)
        "compareTo" -> Exp.TernaryExp(a lt b, (-1).toExp(), Exp.TernaryExp(a gt b, 1.toExp(), 0.toExp()))
        else -> error("Unknown integer operation $operation")
    }

    private fun unaryValue(operation: String, a: Exp): Exp = when (operation) {
        "inc" -> Exp.Add(a, 1.toExp())
        "dec" -> Exp.Sub(a, 1.toExp())
        "unaryMinus" -> Exp.Minus(a)
        else -> a
    }

    val binary: Map<Binary, BinaryOperatorExpEmbeddingTemplate> = buildMap {
        val signed = listOf(IntegerKind.Byte, IntegerKind.Short, IntegerKind.Int, IntegerKind.Long)
        val unsigned = IntegerKind.entries.filter { it.unsigned }
        for (family in listOf(signed, unsigned)) for (a in family) for (b in family) {
            val promoted = if (a == family.last() || b == family.last()) family.last() else family[family.lastIndex - 1]
            for (operation in listOf("plus", "minus", "times", "div", "rem", "compareTo")) {
                // Int/Int uses the shared templates in OperatorExpEmbeddings.
                if (a == IntegerKind.Int && b == IntegerKind.Int) continue
                val result = if (operation == "compareTo") IntegerKind.Int else promoted
                val key = Binary(a, b, operation, result)
                put(key, buildBinaryOperator {
                    setName("${operation}${a.name}${b.name}")
                    withSignature {
                        withParam { existing(a.pretype) }
                        withParam { existing(b.pretype) }
                        withReturnType { existing(result.pretype) }
                    }
                    viperImplementation { binaryValue(operation, args[0], args[1], a.unsigned) }
                    if (operation != "compareTo") additionalConditions {
                        val left = a.type.injection.fromRef(args[0])
                        val right = b.type.injection.fromRef(args[1])
                        val division = operation == "div" || operation == "rem"
                        if (division) precondition {
                            Exp.WithInfo(right ne 0.toExp(), SourceRole.Arithmetic(KotlinCondition.DivisionByZero(operation)).asInfo)
                        }
                        if (!(division && a.unsigned)) precondition { result.checkedRange(binaryValue(operation, left, right), operation) }
                    }
                })
            }
        }
        put(Binary(IntegerKind.Char, IntegerKind.Char, "compareTo", IntegerKind.Int), buildBinaryOperator {
            setName("compareChars")
            withSignature {
                withParam { char() }; withParam { char() }; withReturnType { int() }
            }
            viperImplementation { binaryValue("compareTo", args[0], args[1]) }
        })
    }

    private fun floorQuotient(a: Exp, b: Exp): Exp =
        Exp.TernaryExp(b gt 0.toExp(), Exp.Div(a, b), Exp.Div(Exp.Minus(a), Exp.Minus(b)))

    private fun flooringValue(operation: String, a: Exp, b: Exp): Exp = when (operation) {
        "floorDiv" -> floorQuotient(a, b)
        "mod" -> Exp.Sub(a, Exp.Mul(b, floorQuotient(a, b)))
        else -> error("Unknown flooring operation $operation")
    }

    /**
     * The `kotlin` extensions `floorDiv`, whose result has the promoted width, and `mod`, whose result has the
     * divisor's width, on signed integer operands.
     */
    val flooring: Map<Binary, BinaryOperatorExpEmbeddingTemplate> = buildMap {
        val signed = IntegerKind.entries.filter { it != IntegerKind.Char && !it.unsigned }
        for (a in signed) for (b in signed) {
            val promoted = if (a == IntegerKind.Long || b == IntegerKind.Long) IntegerKind.Long else IntegerKind.Int
            for ((operation, result) in listOf("floorDiv" to promoted, "mod" to b)) {
                put(Binary(a, b, operation, result), buildBinaryOperator {
                    setName("${operation}${a.name}${b.name}")
                    withSignature {
                        withParam { existing(a.pretype) }
                        withParam { existing(b.pretype) }
                        withReturnType { existing(result.pretype) }
                    }
                    viperImplementation { flooringValue(operation, args[0], args[1]) }
                    additionalConditions {
                        val left = a.type.injection.fromRef(args[0])
                        val right = b.type.injection.fromRef(args[1])
                        precondition {
                            Exp.WithInfo(right ne 0.toExp(), SourceRole.Arithmetic(KotlinCondition.DivisionByZero(operation)).asInfo)
                        }
                        if (operation == "floorDiv") precondition {
                            result.checkedRange(flooringValue(operation, left, right), operation)
                        }
                    }
                })
            }
        }
    }

    val wrappingBinary: Map<Binary, BinaryOperatorExpEmbeddingTemplate> = binary.keys.filter {
        it.result in listOf(IntegerKind.Int, IntegerKind.UInt) && it.operation in listOf("plus", "minus", "times")
    }.associateWith { op ->
        buildBinaryOperator {
            setName("wrapping${op.operation}${op.receiver.name}${op.argument.name}")
            withSignature {
                withParam { existing(op.receiver.pretype) }
                withParam { existing(op.argument.pretype) }
                withReturnType { existing(op.result.pretype) }
            }
            viperImplementation {
                val value = binaryValue(op.operation, args[0], args[1])
                if (op.result == IntegerKind.Int && op.operation != "times") OperatorExpEmbeddings.wrapInt(value)
                else wrapInto(op.result, value, pos, info)
            }
        }
    }

    /** Unary minus with two's-complement wrapping, keyed like [unary]; an `Int` receiver is keyed too. */
    val wrappingUnaryMinus: Map<Unary, UnaryOperatorExpEmbeddingTemplate> = IntegerKind.entries.filter { it != IntegerKind.Char && !it.unsigned }
        .associate { a ->
            val result = if (a == IntegerKind.Long) IntegerKind.Long else IntegerKind.Int
            Unary(a, "unaryMinus", result) to buildUnaryOperator {
                setName("wrappingUnaryMinus${a.name}")
                withSignature {
                    withParam { existing(a.pretype) }; withReturnType { existing(result.pretype) }
                }
                viperImplementation {
                    if (result == IntegerKind.Long) wrapLong(Exp.Minus(args[0]), pos, info)
                    else wrapInto(result, Exp.Minus(args[0]), pos, info)
                }
            }
        }

    fun wrapLong(value: Exp, pos: Position = Position.NoPosition, info: Info = Info.NoInfo): Exp {
        // These are mathematical integers: H and 2*H exceed the host Long range.
        val half = Exp.Minus(Exp.IntLit(kotlin.Long.MIN_VALUE))
        val modulus = Exp.Mul(2.toExp(), half)
        return Exp.Sub(value, Exp.Mul(modulus, Exp.Div(Exp.Add(value, half), modulus)), pos, info)
    }

    val wrappingLongBinary: Map<Binary, BinaryOperatorExpEmbeddingTemplate> = binary.keys.filter {
        it.result in listOf(IntegerKind.Long, IntegerKind.ULong) && it.operation in listOf("plus", "minus", "times")
    }.associateWith { op ->
        buildBinaryOperator {
            setName("wrapping${op.operation}${op.receiver.name}${op.argument.name}")
            withSignature {
                withParam { existing(op.receiver.pretype) }
                withParam { existing(op.argument.pretype) }
                withReturnType { existing(op.result.pretype) }
            }
            viperImplementation {
                val value = binaryValue(op.operation, args[0], args[1])
                if (op.result == IntegerKind.Long) wrapLong(value, pos, info) else wrapInto(op.result, value, pos, info)
            }
        }
    }

    val unary: Map<Unary, UnaryOperatorExpEmbeddingTemplate> = buildMap {
        for (a in IntegerKind.entries) {
            val operations = if (a == IntegerKind.Char || a.unsigned) listOf("inc", "dec")
            else listOf("inc", "dec", "unaryMinus", "unaryPlus")
            for (operation in operations) {
                if (a == IntegerKind.Int) continue
                val retainsWidth = operation in listOf("inc", "dec") || a in listOf(IntegerKind.Long, IntegerKind.Char)
                val result = if (retainsWidth) a else IntegerKind.Int
                val key = Unary(a, operation, result)
                put(key, buildUnaryOperator {
                    setName("${operation}${a.name}")
                    withSignature {
                        withParam { existing(a.pretype) }; withReturnType { existing(result.pretype) }
                    }
                    viperImplementation { unaryValue(operation, args[0]) }
                    if (operation != "unaryPlus") additionalConditions {
                        precondition { result.checkedRange(unaryValue(operation, a.type.injection.fromRef(args[0])), operation) }
                    }
                })
            }
        }
        for (a in IntegerKind.entries) for (b in IntegerKind.entries) {
            if (IntegerKind.Char in listOf(a, b) && (a.unsigned || b.unsigned)) continue
            put(Unary(a, "to${b.name}", b), buildUnaryOperator {
                setName("convert${a.name}To${b.name}")
                withSignature {
                    withParam { existing(a.pretype) }; withReturnType { existing(b.pretype) }
                }
                viperImplementation { args[0] }
                if (!b.contains(a)) additionalConditions {
                    precondition { b.checkedRange(a.type.injection.fromRef(args[0]), "to${b.name}") }
                }
            })
        }
        put(Unary(IntegerKind.Char, "<get-code>", IntegerKind.Int), buildUnaryOperator {
            setName("charCode")
            withSignature { withParam { char() }; withReturnType { int() } }
            viperImplementation { args[0] }
        })
    }

    /** [value] reduced into [target]'s range modulo its width, as a two's-complement truncation. */
    fun wrapInto(target: IntegerKind, value: Exp, pos: Position, info: Info): Exp {
        // Mathematical integers: Viper division by a positive modulus rounds down.
        val modulus = IntegerBounds.literal(target.upper - target.lower + BigInteger.ONE)
        val lower = IntegerBounds.literal(target.lower)
        return Exp.Sub(value, Exp.Mul(modulus, Exp.Div(Exp.Sub(value, lower), modulus)), pos, info)
    }

    /**
     * The `to` conversions whose target cannot represent every receiver value, which reinterpret the receiver's low
     * bits in the target's width instead of checking its range.
     */
    val wrappingNarrowing: Map<Unary, UnaryOperatorExpEmbeddingTemplate> = unary.keys.filter {
        it.operation == "to${it.result.name}" && !it.result.contains(it.receiver)
    }.associateWith { op ->
        buildUnaryOperator {
            setName("wrappingConvert${op.receiver.name}To${op.result.name}")
            withSignature {
                withParam { existing(op.receiver.pretype) }; withReturnType { existing(op.result.pretype) }
            }
            viperImplementation { wrapInto(op.result, args[0], pos, info) }
        }
    }

    val templates get() = binary.values + flooring.values + wrappingBinary.values + wrappingUnaryMinus.values + wrappingLongBinary.values + unary.values + wrappingNarrowing.values +
        BitwiseOperators.templates
}
