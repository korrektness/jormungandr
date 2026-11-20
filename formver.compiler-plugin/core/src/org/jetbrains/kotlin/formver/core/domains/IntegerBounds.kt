package org.jetbrains.kotlin.formver.core.domains

import org.jetbrains.kotlin.formver.viper.ast.*
import java.math.BigInteger

/** Representable value domains for Kotlin injections and checked arithmetic. */
object IntegerBounds {
    fun int(value: Exp): Exp = Exp.And(value ge Int.MIN_VALUE.toExp(), value le Int.MAX_VALUE.toExp())
    fun char(value: Exp): Exp = between(value, Char.MIN_VALUE.code.toLong(), Char.MAX_VALUE.code.toLong())
    fun long(value: Exp): Exp = between(value, Long.MIN_VALUE, Long.MAX_VALUE)
    fun short(value: Exp): Exp = between(value, Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong())
    fun byte(value: Exp): Exp = between(value, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
    fun ubyte(value: Exp): Exp = between(value, 0, UByte.MAX_VALUE.toLong())
    fun ushort(value: Exp): Exp = between(value, 0, UShort.MAX_VALUE.toLong())
    fun uint(value: Exp): Exp = between(value, 0, UInt.MAX_VALUE.toLong())
    fun ulong(value: Exp): Exp = Exp.And(value ge Exp.IntLit(0), value le literal(BigInteger(ULong.MAX_VALUE.toString())))
    fun sequence(value: Exp): Exp = Exp.SeqLength(value) le Int.MAX_VALUE.toExp()
    fun string(value: Exp): Exp = Exp.And(
        sequence(value),
        Exp.forall(domainVar("index", Type.Int)) { index ->
            assumption { index ge 0.toExp() }
            assumption { index lt Exp.SeqLength(value) }
            char(simpleTrigger { Exp.SeqIndex(value, index) })
        },
    )
    /** The integer [value]; Viper literals hold host `Long`s, so a larger value is a sum of literals. */
    fun literal(value: BigInteger): Exp = when {
        value.bitLength() < Long.SIZE_BITS -> Exp.IntLit(value.toLong())
        value.signum() > 0 -> Exp.Add(Exp.IntLit(Long.MAX_VALUE), literal(value - BigInteger.valueOf(Long.MAX_VALUE)))
        else -> Exp.Sub(Exp.IntLit(Long.MIN_VALUE), literal(BigInteger.valueOf(Long.MIN_VALUE) - value))
    }
    fun between(value: Exp, lower: Long, upper: Long): Exp = Exp.And(value ge Exp.IntLit(lower), value le Exp.IntLit(upper))
}
