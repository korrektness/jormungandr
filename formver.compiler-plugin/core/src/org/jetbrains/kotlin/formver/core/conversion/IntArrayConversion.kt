/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirSmartCastExpression
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.IntArrayGet
import org.jetbrains.kotlin.formver.core.embeddings.expression.IntArraySet
import org.jetbrains.kotlin.formver.uniqueness.plugin.indexedArrayInitializer
import org.jetbrains.kotlin.formver.uniqueness.plugin.intArrayGetId
import org.jetbrains.kotlin.formver.uniqueness.plugin.isCustom

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
 * The initializer that the `<array>` temporary [symbol] aliases, when reading it again in place of the temporary
 * reads the same array: a variable, `this`, or a chain of plain properties on one of them. `null` otherwise, and
 * then the temporary is an ordinary local.
 */
fun indexedArrayAlias(symbol: FirBasedSymbol<*>): FirExpression? =
    symbol.indexedArrayInitializer?.takeIf { it.isStablePath() }

private fun FirExpression.isStablePath(): Boolean = when (this) {
    is FirSmartCastExpression -> originalExpression.isStablePath()
    is FirThisReceiverExpression -> true
    is FirPropertyAccessExpression -> when (val symbol = calleeReference.symbol) {
        is FirValueParameterSymbol -> true
        is FirPropertySymbol -> symbol.isLocal ||
                symbol.backingFieldSymbol != null && !symbol.isCustom && dispatchReceiver?.isStablePath() == true
        else -> false
    }
    else -> false
}

/** The variable or property that [this] array expression reads, for naming it in a bounds error. */
private fun FirExpression.arraySymbol(): FirBasedSymbol<*>? = when (this) {
    is FirSmartCastExpression -> originalExpression.arraySymbol()
    is FirQualifiedAccessExpression -> when (val symbol = calleeReference.symbol) {
        is FirVariableSymbol<*> -> symbol.indexedArrayInitializer?.arraySymbol() ?: symbol
        else -> null
    }
    else -> null
}
