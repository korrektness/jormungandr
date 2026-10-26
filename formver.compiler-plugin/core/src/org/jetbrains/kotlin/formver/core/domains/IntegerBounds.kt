package org.jetbrains.kotlin.formver.core.domains

import org.jetbrains.kotlin.formver.viper.ast.*

/** Representable value domains for Kotlin injections and checked arithmetic. */
object IntegerBounds {
    fun int(value: Exp): Exp = Exp.And(value ge Int.MIN_VALUE.toExp(), value le Int.MAX_VALUE.toExp())
    fun char(value: Exp): Exp = between(value, Char.MIN_VALUE.code.toLong(), Char.MAX_VALUE.code.toLong())
    fun long(value: Exp): Exp = between(value, Long.MIN_VALUE, Long.MAX_VALUE)
    fun short(value: Exp): Exp = between(value, Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong())
    fun byte(value: Exp): Exp = between(value, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
    fun sequence(value: Exp): Exp = Exp.SeqLength(value) le Int.MAX_VALUE.toExp()
    fun string(value: Exp): Exp = Exp.And(
        sequence(value),
        Exp.forall(domainVar("index", Type.Int)) { index ->
            assumption { index ge 0.toExp() }
            assumption { index lt Exp.SeqLength(value) }
            char(simpleTrigger { Exp.SeqIndex(value, index) })
        },
    )
    fun between(value: Exp, lower: Long, upper: Long): Exp = Exp.And(value ge Exp.IntLit(lower), value le Exp.IntLit(upper))
}
