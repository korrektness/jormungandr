/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.formver.core.embeddings.ExpVisitor
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType

/**
 * The multiset holding [elements], each `Int`, once per occurrence.
 */
data class MultisetOf(val elements: List<ExpEmbedding>) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { multiset() }

    override fun children(): Sequence<ExpEmbedding> = elements.asSequence()
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitMultisetOf(this)
}

/**
 * The elements of an `IntArray` as a multiset. Reading them needs the array's unique predicate, as [IntArrayGet] does;
 * [receiverOwned] says whether the uniqueness checker finds [array] `Unique` before the read.
 */
data class IntArrayContents(val array: ExpEmbedding, val receiverOwned: Boolean) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { multiset() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(array)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitIntArrayContents(this)
}
