/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides

/** The body that can replace an observation inside a representation predicate. */
@OptIn(SymbolInternals::class)
fun classLawObserverBody(symbol: FirCallableSymbol<*>): FirFunctionSymbol<*>? = when (symbol) {
    is FirFunctionSymbol<*> -> symbol.unwrapFakeOverrides()
    is FirPropertySymbol -> symbol.unwrapFakeOverrides().fir.getter?.symbol
    else -> null
}

@OptIn(SymbolInternals::class)
fun classLawObserverFailure(symbol: FirCallableSymbol<*>): String? =
    if (classLawObserverBody(symbol)?.fir?.body == null) "Abstract observer in class law" else null
