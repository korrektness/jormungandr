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
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.IterationSequenceTake
import org.jetbrains.kotlin.name.FqName

private val annotationPackage = FqName("org.jetbrains.kotlin.formver.plugin")

fun StmtConversionContext.convertIterationSequenceIntrinsic(call: FirFunctionCall): ExpEmbedding? {
    val id = call.toResolvedCallableSymbol()?.callableId ?: return null
    if (id.packageName != annotationPackage) return null
    if (id.className == null && id.callableName.asString() == "iterationSequenceOf") {
        embedType(call)
        val elements = call.arguments.flatMap { if (it is FirVarargArgumentsExpression) it.arguments else listOf(it) }
        elements.firstOrNull { it is FirSpreadArgumentExpression }?.let {
            throw UnsupportedFeatureException(it.source, "spread argument to `iterationSequenceOf`")
        }
        return IterationSequenceOf(elements.map(::convert))
    }
    if (id.className?.asString() != "IterationSequence") return null
    val receiver = convert(call.dispatchReceiver!!)
    val argument = convert(call.arguments.single())
    return when (id.callableName.asString()) {
        "get" -> IterationSequenceGet(receiver, argument, embedType(call))
        "take" -> IterationSequenceTake(receiver, argument)
        "contains" -> IterationSequenceContains(receiver, argument)
        else -> null
    }
}
