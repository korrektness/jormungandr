/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.formver.core.embeddings.ExpVisitor
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.formver.intrinsics.plugin.GenericArrayBulk

/** Arguments retain source evaluation order; omitted bounds are supplied by the intrinsic. */
data class GenericArrayBulkUpdate(
    val kind: GenericArrayBulk,
    val array: ExpEmbedding,
    val arguments: List<Pair<String, ExpEmbedding>>,
) : ExpEmbedding {
    val destination: ExpEmbedding get() = arguments.single { it.first == "destination" }.second
    override val type get() = if (kind == GenericArrayBulk.CopyInto) destination.type else buildType { unit() }
    override fun children() = sequenceOf(array) + arguments.asSequence().map { it.second }
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitGenericArrayBulkUpdate(this)
}
