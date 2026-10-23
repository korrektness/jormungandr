package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.core.domains.IntegerBounds
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.core.embeddings.asInfo
import org.jetbrains.kotlin.formver.core.embeddings.types.*
import org.jetbrains.kotlin.formver.viper.ast.*

/** Kotlin overload widths, including promotion of Byte and Short arithmetic to Int. */
enum class IntegerKind(val lower: Long, val upper: Long) {
    Byte(kotlin.Byte.MIN_VALUE.toLong(), kotlin.Byte.MAX_VALUE.toLong()),
    Short(kotlin.Short.MIN_VALUE.toLong(), kotlin.Short.MAX_VALUE.toLong()),
    Int(kotlin.Int.MIN_VALUE.toLong(), kotlin.Int.MAX_VALUE.toLong()),
    Long(kotlin.Long.MIN_VALUE, kotlin.Long.MAX_VALUE),
    Char(kotlin.Char.MIN_VALUE.code.toLong(), kotlin.Char.MAX_VALUE.code.toLong());

    val pretype: PretypeEmbedding
        get() = when (this) {
            Byte -> ByteTypeEmbedding
            Short -> ShortTypeEmbedding
            Int -> IntTypeEmbedding
            Long -> LongTypeEmbedding
            Char -> CharTypeEmbedding
        }
    val type get() = pretype.asTypeEmbedding()

    fun range(value: Exp) = IntegerBounds.between(value, lower, upper)
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

    private fun binaryValue(operation: String, a: Exp, b: Exp): Exp = when (operation) {
        "plus" -> Exp.Add(a, b)
        "minus" -> Exp.Sub(a, b)
        "times" -> Exp.Mul(a, b)
        "div" -> quotient(a, b)
        "rem" -> Exp.Sub(a, Exp.Mul(quotient(a, b), b))
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
        val signed = IntegerKind.entries.filter { it != IntegerKind.Char }
        for (a in signed) for (b in signed) {
            val promoted = if (a == IntegerKind.Long || b == IntegerKind.Long) IntegerKind.Long else IntegerKind.Int
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
                    viperImplementation { binaryValue(operation, args[0], args[1]) }
                    if (operation != "compareTo") additionalConditions {
                        val left = a.type.injection.fromRef(args[0])
                        val right = b.type.injection.fromRef(args[1])
                        if (operation == "div" || operation == "rem") precondition {
                            Exp.WithInfo(right ne 0.toExp(), SourceRole.Arithmetic(KotlinCondition.DivisionByZero(operation)).asInfo)
                        }
                        precondition { result.checkedRange(binaryValue(operation, left, right), operation) }
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

    val wrappingBinary: Map<Binary, BinaryOperatorExpEmbeddingTemplate> = binary.keys.filter {
        it.result == IntegerKind.Int && it.operation in listOf("plus", "minus")
    }.associateWith { op ->
        buildBinaryOperator {
            setName("wrapping${op.operation}${op.receiver.name}${op.argument.name}")
            withSignature {
                withParam { existing(op.receiver.pretype) }
                withParam { existing(op.argument.pretype) }
                withReturnType { int() }
            }
            viperImplementation { OperatorExpEmbeddings.wrapInt(binaryValue(op.operation, args[0], args[1])) }
        }
    }

    fun wrapLong(value: Exp, pos: Position = Position.NoPosition, info: Info = Info.NoInfo): Exp {
        // These are mathematical integers: H and 2*H exceed the host Long range.
        val half = Exp.Minus(Exp.IntLit(kotlin.Long.MIN_VALUE))
        val modulus = Exp.Mul(2.toExp(), half)
        return Exp.Sub(value, Exp.Mul(modulus, Exp.Div(Exp.Add(value, half), modulus)), pos, info)
    }

    val wrappingLongBinary: Map<Binary, BinaryOperatorExpEmbeddingTemplate> = binary.keys.filter {
        it.result == IntegerKind.Long && it.operation in listOf("plus", "minus", "times")
    }.associateWith { op ->
        buildBinaryOperator {
            setName("wrapping${op.operation}${op.receiver.name}${op.argument.name}")
            withSignature {
                withParam { existing(op.receiver.pretype) }
                withParam { existing(op.argument.pretype) }
                withReturnType { existing(IntegerKind.Long.pretype) }
            }
            viperImplementation { wrapLong(binaryValue(op.operation, args[0], args[1]), pos, info) }
        }
    }

    val unary: Map<Unary, UnaryOperatorExpEmbeddingTemplate> = buildMap {
        for (a in IntegerKind.entries) {
            val operations = if (a == IntegerKind.Char) listOf("inc", "dec")
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
            put(Unary(a, "to${b.name}", b), buildUnaryOperator {
                setName("convert${a.name}To${b.name}")
                withSignature {
                    withParam { existing(a.pretype) }; withReturnType { existing(b.pretype) }
                }
                viperImplementation { args[0] }
                if (a.lower < b.lower || a.upper > b.upper) additionalConditions {
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

    val templates get() = binary.values + wrappingBinary.values + wrappingLongBinary.values + unary.values
}
