/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

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
