/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.formver.core.embeddings.ExpVisitor
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.formver.viper.ast.EqAny
import org.jetbrains.kotlin.formver.viper.ast.NeAny
import org.jetbrains.kotlin.formver.viper.ast.Operator

sealed interface AnyComparisonExpression : ExpEmbedding {
    val left: ExpEmbedding
    val right: ExpEmbedding

    override val type
        get() = buildType { boolean() }

    val comparisonOperation: Operator

    /** Whether to compare the operands at their builtin type; they are compared as `Ref`s otherwise. */
    val comparesUnwrapped: Boolean
        get() = left.type == right.type

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(left, right)
}

data class EqCmp(
    override val left: ExpEmbedding,
    override val right: ExpEmbedding,
    override val sourceRole: SourceRole? = null,
) : AnyComparisonExpression {
    override val comparisonOperation = EqAny
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitEqCmp(this)
}

data class NeCmp(
    override val left: ExpEmbedding,
    override val right: ExpEmbedding,
    override val sourceRole: SourceRole? = null,
) : AnyComparisonExpression {

    override val comparisonOperation = NeAny
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitNeCmp(this)
}

/** Model reference equality, with [executionRejection] enforced outside specification evaluation. */
data class IdentityCmp(
    override val left: ExpEmbedding,
    override val right: ExpEmbedding,
    override val sourceRole: SourceRole? = null,
    val executionRejection: String? = null,
) : AnyComparisonExpression {
    override val comparisonOperation = EqAny
    override val comparesUnwrapped = false
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitIdentityCmp(this)
}

/**
 * Kotlin `left == right` where `left`'s `equals` is neither identity nor value equality. Executed code evaluates
 * [executed], or rejects the comparison with the reason [unsupportedExecution] when [executed] is null. A
 * specification evaluates [specified].
 */
data class ObjectEquality(
    val left: ExpEmbedding,
    val right: ExpEmbedding,
    val executed: ExpEmbedding?,
    val specified: ExpEmbedding,
    val unsupportedExecution: String? = null,
    override val sourceRole: SourceRole? = null,
) : ExpEmbedding {
    override val type = buildType { boolean() }
    override fun children(): Sequence<ExpEmbedding> = sequenceOf(left, right)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitObjectEquality(this)
}

/**
 * The relation a specification states with Kotlin `left == right` when no `@Pure` function computes it. It is fixed
 * per pair of references and known only where `left` is null or of a type whose `equals` is identity or value
 * equality.
 */
data class EqualsRelation(val left: ExpEmbedding, val right: ExpEmbedding) : ExpEmbedding {
    override val type = buildType { boolean() }
    override fun children(): Sequence<ExpEmbedding> = sequenceOf(left, right)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitEqualsRelation(this)
}

fun ExpEmbedding.notNullCmp(): ExpEmbedding = NeCmp(this, NullLit)
