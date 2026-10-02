/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.formver.core.embeddings.ExpVisitor
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType

/**
 * The length of an `IntArray`, which needs no permission.
 */
data class IntArraySize(val array: ExpEmbedding) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { int() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(array)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitIntArraySize(this)
}

/**
 * Every element of an `IntArray` is zero. Reading the elements needs the array's unique predicate.
 */
data class IntArrayAllZero(val array: ExpEmbedding) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { boolean() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(array)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitIntArrayAllZero(this)
}

/**
 * Element [index] of an `IntArray`. [receiverOwned] says whether the uniqueness checker finds [array] `Unique` before
 * the read. [arraySymbol] names the array in a bounds error, when it is a variable.
 */
data class IntArrayGet(
    val array: ExpEmbedding,
    val index: ExpEmbedding,
    val receiverOwned: Boolean,
    val arraySymbol: FirBasedSymbol<*>? = null,
) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { int() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(array, index)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitIntArrayGet(this)
}

/**
 * Writes [value] to element [index] of an `IntArray`, as [IntArrayGet] reads it.
 */
data class IntArraySet(
    val array: ExpEmbedding,
    val index: ExpEmbedding,
    val value: ExpEmbedding,
    val receiverOwned: Boolean,
    val arraySymbol: FirBasedSymbol<*>? = null,
) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { unit() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(array, index, value)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitIntArraySet(this)
}
