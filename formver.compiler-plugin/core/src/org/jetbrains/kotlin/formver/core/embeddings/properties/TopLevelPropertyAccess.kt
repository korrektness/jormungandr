/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.properties

import org.jetbrains.kotlin.formver.core.conversion.StmtConversionContext
import org.jetbrains.kotlin.formver.core.embeddings.callables.insertCall
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding

/** An access to a top-level [property] of Kotlin type [type]: a read calls its getter, a write its setter. */
class TopLevelPropertyAccess(val property: PropertyEmbedding, val type: TypeEmbedding) : PropertyAccessEmbedding {
    override fun getValue(ctx: StmtConversionContext): ExpEmbedding =
        (property.getter as CustomGetter).getterMethod.insertCall(emptyList(), ctx, type)

    override fun setValue(value: ExpEmbedding, ctx: StmtConversionContext): ExpEmbedding {
        val setter = (property.setter as CustomSetter).setterMethod
        return setter.insertCall(listOf(value), ctx, setter.callableType.returnType)
    }
}
