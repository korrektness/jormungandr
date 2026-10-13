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
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.formver.common.externalSpecifications
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType

private val annotationPackage = FqName("org.jetbrains.kotlin.formver.plugin")

/** Reads the abstract observer from the trusted collection view. */
@OptIn(DirectDeclarationsAccess::class)
fun StmtConversionContext.collectionIterationSequence(receiver: ExpEmbedding, map: Boolean = false): ExpEmbedding {
    val id = ClassId.topLevel(FqName(if (map) "kotlin.collections.Map" else "kotlin.collections.Iterable"))
    val target = session.symbolProvider.getClassLikeSymbolByClassId(id) as FirRegularClassSymbol
    val model = checkNotNull(session.externalSpecifications?.modelClass(target)) { "Missing collection iteration model for $id: ${session.externalSpecifications?.errors}" }
    val observer = model.declarationSymbols.filterIsInstance<FirNamedFunctionSymbol>().single { it.name.asString() == "iterationSequence" }
    typeResolver.markIterationSequenceUsed()
    return embedAnyFunction(observer).insertCall(listOf(receiver), this).withType(buildType { iterationSequence() })
}

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
    if (id.className == null && id.callableName.asString() == "iterationSequence") {
        embedType(call)
        val map = (call.toResolvedCallableSymbol() as FirFunctionSymbol<*>)
            .valueParameterSymbols.single().resolvedReturnType.classId == ClassId.topLevel(FqName("kotlin.collections.Map"))
        return collectionIterationSequence(convert(call.arguments.single()), map)
    }
    if (id.className?.asString() != "IterationSequence") return null
    val receiver = convert(call.dispatchReceiver!!)
    val argument = convert(call.arguments.single())
    return when (id.callableName.asString()) {
        "get" -> IterationSequenceGet(receiver, argument, embedType(call)).let { value ->
            if (signature.isPure || typeResolver.classLawExpansion != null) value
            else value.withInvariants(typeResolver) { proven = true }
        }
        "take" -> IterationSequenceTake(receiver, argument)
        "contains" -> IterationSequenceContains(receiver, argument)
        else -> null
    }
}
