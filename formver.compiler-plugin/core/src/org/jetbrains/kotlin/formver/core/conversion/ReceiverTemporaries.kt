/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirSmartCastExpression
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.formver.locality.plugin.receiverTemporaryInitializer
import org.jetbrains.kotlin.formver.uniqueness.plugin.isCustom

/**
 * The initializer that the receiver temporary [symbol] aliases (see [receiverTemporaryInitializer]), when reading it
 * again in place of the temporary reads the same object: a variable, `this`, or a chain of plain properties on one of
 * them. `null` otherwise, and then the temporary is an ordinary local.
 */
fun receiverTemporaryAlias(symbol: FirBasedSymbol<*>): FirExpression? =
    symbol.receiverTemporaryInitializer?.takeIf { it.isStablePath() }

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
