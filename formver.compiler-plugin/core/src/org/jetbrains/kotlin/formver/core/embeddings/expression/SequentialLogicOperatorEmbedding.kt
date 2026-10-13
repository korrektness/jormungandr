/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.formver.core.embeddings.ExpVisitor
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Position

/**
 * `&&` and `||`: the first operand is evaluated, and the second only when the first does not decide the result.
 * Where statements are available they become an [If]; in a single Viper expression they become Viper's own
 * short-circuiting operator.
 */
sealed class SequentialLogicOperatorEmbedding : ExpEmbedding {
    abstract val left: ExpEmbedding
    abstract val right: ExpEmbedding

    override val type
        get() = buildType { boolean() }

    abstract val ifReplacement: ExpEmbedding

    /** Where the right operand is evaluated, given the builtin [left] operand. */
    abstract fun rightEvaluatedWhen(left: Exp): Exp

    /** The operator applied to the builtin operands. */
    abstract fun combine(left: Exp, right: Exp, pos: Position): Exp

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(left, right)
}

data class SequentialAnd(override val left: ExpEmbedding, override val right: ExpEmbedding) :
    SequentialLogicOperatorEmbedding() {
    override val ifReplacement
        get() = If(left, right.withType(type), BooleanLit(false).withType(type), type)

    override fun rightEvaluatedWhen(left: Exp): Exp = left
    override fun combine(left: Exp, right: Exp, pos: Position): Exp = Exp.And(left, right, pos)

    override fun <R> accept(v: ExpVisitor<R>): R = v.visitSequentialAnd(this)
}

data class SequentialOr(override val left: ExpEmbedding, override val right: ExpEmbedding) :
    SequentialLogicOperatorEmbedding() {
    override val ifReplacement
        get() = If(left, BooleanLit(true).withType(type), right.withType(type), type)

    override fun rightEvaluatedWhen(left: Exp): Exp = Exp.Not(left)
    override fun combine(left: Exp, right: Exp, pos: Position): Exp = Exp.Or(left, right, pos)

    override fun <R> accept(v: ExpVisitor<R>): R = v.visitSequentialOr(this)
}
