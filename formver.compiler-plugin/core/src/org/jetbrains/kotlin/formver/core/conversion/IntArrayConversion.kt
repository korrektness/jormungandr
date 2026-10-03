/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirSmartCastExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.getDeclaredConstructors
import org.jetbrains.kotlin.fir.scopes.impl.declaredMemberScope
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.embeddings.callables.insertCall
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.types.IntArrayEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.formver.core.linearization.OwnedShape
import org.jetbrains.kotlin.formver.core.purity.isPure
import org.jetbrains.kotlin.formver.locality.plugin.receiverTemporaryInitializer
import org.jetbrains.kotlin.formver.uniqueness.plugin.intArrayGetId

/**
 * Converts a call to `IntArray.get` or `IntArray.set`, in any syntactic form, to the intrinsic element access.
 */
fun StmtConversionContext.convertIntArrayElementAccess(call: FirFunctionCall): ExpEmbedding {
    val receiver = call.dispatchReceiver
        ?: throw SnaktInternalException(call.source, "An IntArray element access has no receiver.")
    val array = convert(receiver)
    val args = call.argumentList.arguments.map { convert(it) }
    val owned = ownsBefore(call, receiver)
    val symbol = receiver.arraySymbol()
    return if (call.toResolvedCallableSymbol()?.callableId == intArrayGetId) {
        IntArrayGet(array, args[0], owned, symbol)
    } else {
        warnIfUntrackedWrite(call, receiver, owned)
        IntArraySet(array, args[0], args[1], owned, symbol)
    }
}

/**
 * Whether [this] call is the `IntArray(size, init)` constructor.
 */
fun FirFunctionCall.isIntArrayInit(): Boolean {
    val symbol = calleeReference.symbol as? FirConstructorSymbol ?: return false
    return symbol.callableId.classId == IntArrayEmbedding.classId && symbol.valueParameterSymbols.size == 2
}

/**
 * Converts `IntArray(size, init)` to `IntArray(size)` followed by a loop that writes `init(i)` to each element `i`,
 * with `init` inlined. When the body of `init` is a pure expression, the loop keeps the fact that each element written
 * so far equals that expression at its index, so the fact holds of every element after the loop.
 */
fun StmtConversionContext.convertIntArrayInit(call: FirFunctionCall): ExpEmbedding {
    val (sizeArg, initArg) = call.argumentList.arguments
    val sizeValue = convert(sizeArg)
    val init = convert(initArg).ignoringMetaNodes() as? LambdaExp
        ?: throw UnsupportedFeatureException(initArg.source, "an `IntArray` initializer that is not a lambda")
    val arrayClass = session.symbolProvider.getClassLikeSymbolByClassId(IntArrayEmbedding.classId) as FirClassSymbol<*>
    val sizedConstructor = arrayClass.declaredMemberScope(session, memberRequiredPhase = null).getDeclaredConstructors()
        .single { it.valueParameterSymbols.size == 1 }
    val intType = buildType { int() }
    val size = freshAnonVar(intType)
    val array = freshAnonVar(embedType(call))
    val index = freshAnonVar(intType)

    val inScope = retrievePropertiesAndParameters().toList()
    val invariants = buildList {
        inScope.forEach { addAll(it.provenInvariants()) }
        addAll(index.provenInvariants())
        add(OperatorExpEmbeddings.GeIntInt(index, IntLit(0)))
        add(OperatorExpEmbeddings.LeIntInt(index, size))
        add(EqCmp(IntArraySize(array), size))
        initValueAt(init)?.let { (j, value) ->
            val written = OperatorExpEmbeddings.And(
                OperatorExpEmbeddings.GeIntInt(j, IntLit(0)),
                OperatorExpEmbeddings.LtIntInt(j, index),
            )
            add(ForAllEmbedding(j, listOf(OperatorExpEmbeddings.Implies(written, EqCmp(IntArrayGet(array, j, receiverOwned = true), value)))))
        }
    }
    // The array is fresh, so it is owned with no holes throughout the loop.
    val shapes = ownedShapes({ it.stateBefore(call) }, inScope) +
            OwnedShape(array, emptyList())
    val fill = withFreshWhile(label = null) {
        val write = IntArraySet(array, index, withCallSite(call) { init.insertCall(listOf(index), this) }, receiverOwned = true)
        val step = Assign(index, OperatorExpEmbeddings.AddIntInt(index, IntLit(1)))
        While(
            OperatorExpEmbeddings.LtIntInt(index, size),
            blockOf(write, step),
            breakLabelName(),
            continueLabelName(),
            invariants,
            shapes,
            shapes,
        )
    }
    val construct = withCallSite(call) { embedAnyFunction(sizedConstructor).insertCall(listOf(size), this, array.type) }
    return IntArrayInit(
        array,
        blockOf(
            Declare(size, sizeValue),
            Declare(array, construct, targetOwned = true),
            Declare(index, IntLit(0)),
            fill,
        ),
    )
}

/**
 * The value of [init] at a fresh quantified index, with that index, when the body of [init] is a single pure
 * expression; `null` otherwise.
 */
private fun StmtConversionContext.initValueAt(init: LambdaExp): Pair<VariableEmbedding, ExpEmbedding>? {
    val statement = init.function.body?.statements?.singleOrNull() ?: return null
    val result = when (statement) {
        is FirReturnExpression -> statement.result.takeIf { statement.target.labeledElement == init.function }
        is FirExpression -> statement
        else -> null
    } ?: return null
    val parameter = init.function.valueParameters.single().symbol
    val j = freshAnonBuiltinVar(embedType(parameter.resolvedReturnType))
    val methodCtxFactory = MethodContextFactory(
        signature,
        InlineParameterResolver(
            substitutions = mapOf(SubstitutedArgument.ValueParameter(parameter) to j),
            labelName = null,
            defaultResolvedReturnTarget = defaultResolvedReturnTarget,
        ),
        ownershipFrame = ownershipFrame,
        parent = this,
    )
    val value = withNoScope { withMethodCtx(methodCtxFactory) { convert(result) } }
    return if (value.isPure()) j to value else null
}

/** The variable or property that [this] array expression reads, for naming it in a bounds error. */
private fun FirExpression.arraySymbol(): FirBasedSymbol<*>? = when (this) {
    is FirSmartCastExpression -> originalExpression.arraySymbol()
    is FirQualifiedAccessExpression -> when (val symbol = calleeReference.symbol) {
        is FirVariableSymbol<*> -> symbol.receiverTemporaryInitializer?.arraySymbol() ?: symbol
        else -> null
    }
    else -> null
}
