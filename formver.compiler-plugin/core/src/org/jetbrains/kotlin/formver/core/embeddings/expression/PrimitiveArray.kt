/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.formver.core.embeddings.ExpVisitor
import org.jetbrains.kotlin.formver.core.embeddings.types.PrimitiveArrayEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType

/**
 * The length of a modelled primitive array, which needs no permission.
 */
data class PrimitiveArraySize(val array: ExpEmbedding) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { int() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(array)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitPrimitiveArraySize(this)
}

/**
 * Every element of [array], of class [kind], is zero. Reading the elements needs the array's unique predicate.
 */
data class PrimitiveArrayAllZero(val kind: PrimitiveArrayEmbedding, val array: ExpEmbedding) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { boolean() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(array)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitPrimitiveArrayAllZero(this)
}

/**
 * Element [index] of [array], of class [kind]. [receiverOwned] says whether the uniqueness checker finds [array]
 * `Unique` before the read. [arraySymbol] names the array in a bounds error, when it is a variable.
 */
data class PrimitiveArrayGet(
    val kind: PrimitiveArrayEmbedding,
    val array: ExpEmbedding,
    val index: ExpEmbedding,
    val receiverOwned: Boolean,
    val arraySymbol: FirBasedSymbol<*>? = null,
) : ExpEmbedding {
    override val type: TypeEmbedding = kind.element.type

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(array, index)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitPrimitiveArrayGet(this)
}

/**
 * Writes [value] to element [index] of a modelled primitive array, as [PrimitiveArrayGet] reads it.
 */
data class PrimitiveArraySet(
    val array: ExpEmbedding,
    val index: ExpEmbedding,
    val value: ExpEmbedding,
    val receiverOwned: Boolean,
    val arraySymbol: FirBasedSymbol<*>? = null,
) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { unit() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(array, index, value)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitPrimitiveArraySet(this)
}

/**
 * A modelled primitive array created and filled by [initialization], which leaves it in [array] holding its unique predicate. The
 * value is [array], and a move out of this expression moves [array].
 */
data class PrimitiveArrayInit(
    val array: VariableEmbedding,
    val initialization: ExpEmbedding,
) : ExpEmbedding {
    override val type: TypeEmbedding = array.type

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(initialization)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitPrimitiveArrayInit(this)
}
