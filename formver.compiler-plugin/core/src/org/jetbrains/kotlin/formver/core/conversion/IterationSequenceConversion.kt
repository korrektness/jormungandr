/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.isSubtypeOf
import org.jetbrains.kotlin.fir.types.resolvedType
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

/** The classes whose size is an `Int`, with their numbers of type parameters. */
private val intSizedIterables = listOf("kotlin.collections.Collection" to 1, "kotlin.collections.Map" to 2)

/**
 * Rejects iterating [receiver] unless it is a Collection or a Map. An observer's size is an `Int`, which the element
 * count of another Iterable need not be.
 */
fun StmtConversionContext.requireIntSizedIteration(receiver: FirExpression) {
    val sized = intSizedIterables.any { (name, arity) ->
        val type = ClassId.topLevel(FqName(name)).constructClassLikeType(Array(arity) { ConeStarProjection })
        receiver.resolvedType.isSubtypeOf(type, session)
    }
    if (!sized) throw UnsupportedFeatureException(receiver.source, "iteration over an Iterable that is not a Collection")
}

/**
 * The external-specification model of `Map` when [map] holds, else of `Iterable`, or null when no loaded specification
 * binds one.
 */
private fun StmtConversionContext.collectionIterationModel(map: Boolean): FirRegularClassSymbol? {
    val id = ClassId.topLevel(FqName(if (map) "kotlin.collections.Map" else "kotlin.collections.Iterable"))
    val target = session.symbolProvider.getClassLikeSymbolByClassId(id) as? FirRegularClassSymbol ?: return null
    return session.externalSpecifications?.modelClass(target)
}

/**
 * Whether the trusted collection view that a collection template needs is available: that of `Iterable`, which builder
 * results use, and that of `Map` too when [map] holds.
 */
fun StmtConversionContext.hasCollectionIterationModels(map: Boolean): Boolean =
    collectionIterationModel(map = false) != null && (!map || collectionIterationModel(map = true) != null)

/** Reads the abstract observer from the trusted collection view; the call at [source] needs that view. */
@OptIn(DirectDeclarationsAccess::class)
fun StmtConversionContext.collectionIterationSequence(
    receiver: ExpEmbedding, source: KtSourceElement?, map: Boolean = false,
): ExpEmbedding {
    val model = collectionIterationModel(map)
        ?: throw UnsupportedFeatureException(source, "collection iteration without the bundled collection specifications")
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
        requireIntSizedIteration(call.arguments.single())
        return collectionIterationSequence(convert(call.arguments.single()), call.source, map)
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
