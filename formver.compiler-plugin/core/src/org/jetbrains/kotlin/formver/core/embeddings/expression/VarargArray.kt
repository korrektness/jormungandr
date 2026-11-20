/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.formver.core.embeddings.ExpVisitor

/** One argument given for a vararg parameter: a single element, or a spread `*array` contributing all its elements. */
sealed interface VarargPart {
    val value: ExpEmbedding

    data class Element(override val value: ExpEmbedding) : VarargPart

    /** [sourceOwned] says whether the uniqueness checker finds the spread array `Unique` before the call. */
    data class Spread(override val value: ExpEmbedding, val sourceOwned: Boolean) : VarargPart
}

/**
 * The fresh array [result] that a call passes for a vararg parameter, holding the elements of [parts] in order.
 *
 * The parts are evaluated in order. For a modelled primitive array or `Array<T>`, [result] holds its unique
 * predicate, its size is the number of elements, each single element is stored at its index and each spread from an
 * owned array is copied; the elements copied from a shared array are unknown. Another array class has no element
 * model, so only freshness is known.
 */
data class VarargArray(
    val result: VariableEmbedding,
    val parts: List<VarargPart>,
) : ExpEmbedding {
    override val type = result.type
    override fun children() = parts.asSequence().map { it.value }
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitVarargArray(this)
}
