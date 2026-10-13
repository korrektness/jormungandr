/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.formver.core.embeddings.ExpVisitor
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding

data class IterationSequenceGet(
    val sequence: ExpEmbedding,
    val index: ExpEmbedding,
    override val type: TypeEmbedding,
) : ExpEmbedding {
    override fun children() = sequenceOf(sequence, index)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitIterationSequenceGet(this)
}

data class IterationSequenceContains(val sequence: ExpEmbedding, val element: ExpEmbedding) : ExpEmbedding {
    override val type = buildType { boolean() }
    override fun children() = sequenceOf(sequence, element)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitIterationSequenceContains(this)
}

data class IterationSequenceOf(val elements: List<ExpEmbedding>) : ExpEmbedding {
    override val type = buildType { iterationSequence() }
    override fun children() = elements.asSequence()
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitIterationSequenceOf(this)
}
