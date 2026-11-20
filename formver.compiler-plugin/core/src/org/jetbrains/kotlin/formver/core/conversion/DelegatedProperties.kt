/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirDelegateFieldSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.embeddings.callables.insertCall
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.toBlock
import org.jetbrains.kotlin.formver.core.embeddings.properties.PropertyAccessEmbedding
import org.jetbrains.kotlin.formver.core.functionCallArguments

/**
 * The delegate of a delegated property. A local delegate is the local variable [declareLocalDelegate] declares. Any
 * other delegate is set during construction, which SnaKt does not convert, so a read gives an arbitrary value of its
 * type after evaluating the receiver.
 */
internal fun StmtConversionContext.delegateAccess(
    access: FirPropertyAccessExpression,
    symbol: FirDelegateFieldSymbol,
): PropertyAccessEmbedding {
    if (symbol.correspondingPropertySymbol.isLocal) return resolveLocal(symbol)
    val receiver = access.dispatchReceiver?.let { convert(it) }
    return object : PropertyAccessEmbedding {
        override fun getValue(ctx: StmtConversionContext): ExpEmbedding =
            listOfNotNull(receiver, ctx.unconstrainedValue(ctx.embedType(access))).toBlock()

        override fun setValue(value: ExpEmbedding, ctx: StmtConversionContext): ExpEmbedding =
            throw UnsupportedFeatureException(access.source, "a write to the delegate of a delegated property")
    }
}

/** Declares the local variable holding the delegate of the local delegated property [symbol]. */
internal fun StmtConversionContext.declareLocalDelegate(symbol: FirPropertySymbol): ExpEmbedding =
    declareLocalVariable(symbol.delegateFieldSymbol!!, convert(symbol.delegate!!), embedType(symbol.delegate!!))

/**
 * Whether reads and writes of [symbol] call its delegate directly: it is a delegated property, local or final, with at
 * most one receiver.
 */
private fun isCalledThroughDelegate(symbol: Any?): Boolean =
    symbol is FirPropertySymbol && symbol.hasDelegate && (symbol.isLocal || symbol.isFinal) &&
        (symbol.dispatchReceiverType == null || symbol.receiverParameterSymbol == null)

/**
 * A read of the delegated property [access] denotes, or a write of the value [value] converts to it: the `getValue` or
 * `setValue` call of its accessor, under that function's contract, with the receiver of [access] as `thisRef`. The
 * receiver is evaluated before [value], and null-checked as an [CallOperand] after it.
 */
private fun StmtConversionContext.callDelegate(
    access: FirPropertyAccessExpression,
    value: (StmtConversionContext.() -> ExpEmbedding)?,
): ExpEmbedding {
    val symbol = access.calleeReference.symbol as FirPropertySymbol
    val accessor = if (value == null) symbol.getterSymbol else symbol.setterSymbol
    val call = delegateCall(accessor)
    val parameter = accessor?.valueParameterSymbols?.singleOrNull()
    val callee = call.toResolvedCallableSymbol() as? FirFunctionSymbol<*>
        ?: throw SnaktInternalException(call.source, "A delegated property accessor calls no function.")
    val receiver = access.dispatchReceiver?.let { operand(it, symbol.dispatchReceiverType) }
        ?: access.extensionReceiver?.let { operand(it, symbol.resolvedReceiverType) }
    val receiverDeclaration = receiver?.let { declareAnonVar(it.value.type, it.value) }
    val check = receiver?.deferredCheck
    val writtenValue = value?.invoke(this)
    val writtenDeclaration = writtenValue?.takeIf { check != null }?.let { declareAnonVar(it.type, it) }
    val written = writtenDeclaration?.variable ?: writtenValue
    val thisRef = receiverDeclaration?.variable?.let { variable -> check?.let { checkedCast(variable, embedType(it)) } ?: variable }
    val embedding = embedAnyFunction(callee)
    reportOpaqueLibraryCall(call, callee, embedding)
    val args = call.functionCallArguments.map { argument ->
        val argumentSymbol = (argument as? FirPropertyAccessExpression)?.calleeReference?.symbol
        when {
            argument is FirThisReceiverExpression -> thisRef ?: throw SnaktInternalException(
                argument.source, "A delegated property accessor reads a receiver its access does not have."
            )
            argumentSymbol == parameter && written != null -> written
            argumentSymbol is FirDelegateFieldSymbol && !symbol.isLocal -> unconstrainedValue(embedType(argument))
            else -> convert(argument)
        }
    }
    val result = withCallSite(call) { embedding.insertCall(args, this, embedType(call.resolvedType)) }
    return listOfNotNull(receiverDeclaration, writtenDeclaration, result).toBlock()
}

@OptIn(SymbolInternals::class)
private fun delegateCall(accessor: FirPropertyAccessorSymbol?): FirFunctionCall {
    val statement = accessor?.fir?.body?.statements?.singleOrNull()
    val call = (statement as? FirReturnExpression)?.result ?: statement
    return call as? FirFunctionCall
        ?: throw SnaktInternalException(accessor?.source, "A delegated property accessor does not call its delegate.")
}

/** A read of [access] through its delegate, or null when [access] is not called through its delegate. */
internal fun StmtConversionContext.delegatedRead(access: FirPropertyAccessExpression): ExpEmbedding? =
    if (isCalledThroughDelegate(access.calleeReference.symbol)) callDelegate(access, value = null) else null

/**
 * A write of [rValue] to [access] through its delegate, with the platform null check of an assignment, or null when
 * [access] is not called through its delegate.
 */
internal fun StmtConversionContext.delegatedWrite(access: FirPropertyAccessExpression, rValue: FirExpression): ExpEmbedding? =
    if (isCalledThroughDelegate(access.calleeReference.symbol)) {
        callDelegate(access) { platformNullCheck(rValue, convert(rValue), access.resolvedType) }
    } else null

/**
 * An arbitrary value of the type of [reference] when it is the property reference a delegated property accessor passes
 * to `getValue` or `setValue`, else null.
 */
internal fun StmtConversionContext.delegateAccessorReference(reference: FirCallableReferenceAccess): ExpEmbedding? =
    if (reference.source?.kind == KtFakeSourceElementKind.DelegatedPropertyAccessor) {
        unconstrainedValue(embedType(reference.resolvedType))
    } else null
