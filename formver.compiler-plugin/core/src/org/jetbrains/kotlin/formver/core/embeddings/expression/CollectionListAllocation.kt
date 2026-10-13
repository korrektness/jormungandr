/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.formver.core.embeddings.ExpVisitor

/** A fresh owned List with the iteration observer constrained by [sequenceBinding]. */
data class CollectionListAllocation(
    val result: VariableEmbedding,
    val sequenceBinding: ExpEmbedding,
) : ExpEmbedding {
    override val type = result.type
    override fun children() = sequenceOf(sequenceBinding)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitCollectionListAllocation(this)
}
