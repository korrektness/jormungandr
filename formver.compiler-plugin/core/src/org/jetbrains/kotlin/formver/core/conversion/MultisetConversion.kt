/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirSpreadArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.IntArrayContents
import org.jetbrains.kotlin.formver.core.embeddings.expression.MultisetOf
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val formverPackage = FqName("org.jetbrains.kotlin.formver.plugin")
private val multisetOfId = CallableId(formverPackage, Name.identifier("multisetOf"))
private val contentsId = CallableId(formverPackage, Name.identifier("contents"))

/**
 * Converts [call] when it is `multisetOf(...)` or `contents(arr)` of `formver.annotations`; `null` for any other call.
 */
fun StmtConversionContext.convertMultisetIntrinsic(call: FirFunctionCall): ExpEmbedding? =
    when (call.toResolvedCallableSymbol()?.callableId) {
        multisetOfId -> {
            // Embedding the result type rejects element types other than `Int`.
            embedType(call.resolvedType)
            val elements = call.argumentList.arguments.flatMap { arg ->
                if (arg is FirVarargArgumentsExpression) arg.arguments else listOf(arg)
            }
            elements.firstOrNull { it is FirSpreadArgumentExpression }?.let {
                throw UnsupportedFeatureException(it.source, "spread argument to `multisetOf`")
            }
            MultisetOf(elements.map { convert(it) })
        }
        contentsId -> {
            val array = call.argumentList.arguments.single()
            embedType(call.resolvedType)
            IntArrayContents(convert(array), ownsBefore(call, array))
        }
        else -> null
    }
