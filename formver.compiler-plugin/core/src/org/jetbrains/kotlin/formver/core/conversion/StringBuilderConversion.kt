/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.StringBuilderAppend
import org.jetbrains.kotlin.formver.core.embeddings.expression.StringBuilderClear
import org.jetbrains.kotlin.formver.core.embeddings.expression.StringBuilderLength
import org.jetbrains.kotlin.formver.core.embeddings.expression.StringBuilderToString
import org.jetbrains.kotlin.formver.intrinsics.plugin.StringBuilderIntrinsic
import org.jetbrains.kotlin.formver.intrinsics.plugin.stringBuilderReceiver

/**
 * Converts [access], which performs the `StringBuilder` [intrinsic], to the intrinsic operation.
 */
fun StmtConversionContext.convertStringBuilderIntrinsic(
    access: FirQualifiedAccessExpression,
    intrinsic: StringBuilderIntrinsic,
): ExpEmbedding {
    val receiver = access.stringBuilderReceiver
        ?: throw SnaktInternalException(access.source, "A StringBuilder intrinsic has no receiver.")
    val builder = convert(receiver)
    val owned = ownsBefore(access, receiver)
    return when (intrinsic) {
        StringBuilderIntrinsic.LENGTH -> StringBuilderLength(builder, owned)
        StringBuilderIntrinsic.TO_STRING -> StringBuilderToString(builder, owned)
        StringBuilderIntrinsic.APPEND_CHAR, StringBuilderIntrinsic.APPEND_STRING -> {
            val value = (access as FirFunctionCall).arguments.single()
            StringBuilderAppend(builder, convert(value), owned)
        }
        StringBuilderIntrinsic.CLEAR -> StringBuilderClear(builder, owned)
    }
}
