/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.properties

import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.PrimitiveArraySize
import org.jetbrains.kotlin.formver.core.embeddings.expression.GenericArraySize
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings

object LengthFieldGetter : GetterEmbedding {
    override fun getValue(receiver: ExpEmbedding, ctx: TypeResolver, receiverOwned: Boolean) =
        OperatorExpEmbeddings.StringLength(receiver)

    override fun getValueSimple(
        receiver: ExpEmbedding,
        ctx: TypeResolver
    ): ExpEmbedding = OperatorExpEmbeddings.StringLength(receiver)
}

object MultisetSizeGetter : GetterEmbedding {
    override fun getValue(receiver: ExpEmbedding, ctx: TypeResolver, receiverOwned: Boolean) =
        OperatorExpEmbeddings.MultisetSize(receiver)

    override fun getValueSimple(receiver: ExpEmbedding, ctx: TypeResolver) = OperatorExpEmbeddings.MultisetSize(receiver)
}

object GenericArraySizeGetter : GetterEmbedding {
    override fun getValue(receiver: ExpEmbedding, ctx: TypeResolver, receiverOwned: Boolean) = GenericArraySize(receiver)
    override fun getValueSimple(receiver: ExpEmbedding, ctx: TypeResolver) = GenericArraySize(receiver)
}

object PrimitiveArraySizeGetter : GetterEmbedding {
    override fun getValue(receiver: ExpEmbedding, ctx: TypeResolver, receiverOwned: Boolean) = PrimitiveArraySize(receiver)

    override fun getValueSimple(receiver: ExpEmbedding, ctx: TypeResolver) = PrimitiveArraySize(receiver)
}
