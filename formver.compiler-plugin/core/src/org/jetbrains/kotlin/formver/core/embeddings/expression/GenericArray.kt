/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.formver.core.embeddings.ExpVisitor
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType

/** Permission-free length of a generic array. */
data class GenericArraySize(val array: ExpEmbedding) : ExpEmbedding {
    override val type = buildType { int() }
    override fun children() = sequenceOf(array)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitGenericArraySize(this)
}

/** Null initialization fact, read under the array's unique predicate. */
data class GenericArrayAllNull(val array: ExpEmbedding) : ExpEmbedding {
    override val type = buildType { boolean() }
    override fun children() = sequenceOf(array)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitGenericArrayAllNull(this)
}

/** A reference read whose static type is supplied by FIR. Shared reads are unconstrained. */
data class GenericArrayGet(
    val array: ExpEmbedding,
    val index: ExpEmbedding,
    val receiverOwned: Boolean,
    override val type: TypeEmbedding,
    val arraySymbol: FirBasedSymbol<*>? = null,
) : ExpEmbedding {
    override fun children() = sequenceOf(array, index)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitGenericArrayGet(this)
}

/** A reference store consumes the value's ownership; only owned receivers track the contents update. */
data class GenericArraySet(
    val array: ExpEmbedding,
    val index: ExpEmbedding,
    val value: ExpEmbedding,
    val receiverOwned: Boolean,
    val arraySymbol: FirBasedSymbol<*>? = null,
) : ExpEmbedding {
    override val type = buildType { unit() }
    override fun children() = sequenceOf(array, index, value)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitGenericArraySet(this)
}
