/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.formver.core.isLaw

/** The distinct laws declared along a type's supertype graph. */
context(ctx: CheckerContext)
@OptIn(DirectDeclarationsAccess::class)
fun FirRegularClassSymbol.interfaceLaws(): List<FirNamedFunctionSymbol> {
    val visited = mutableSetOf<FirRegularClassSymbol>()
    fun collect(symbol: FirRegularClassSymbol): List<FirNamedFunctionSymbol> {
        if (!visited.add(symbol)) return emptyList()
        val own = if (symbol.classKind.isInterface) symbol.declarationSymbols.filterIsInstance<FirNamedFunctionSymbol>()
            .filter { it.isLaw(ctx.session) } else emptyList()
        return own +
            symbol.resolvedSuperTypes.flatMap { (it.toClassSymbol(ctx.session) as? FirRegularClassSymbol)?.let(::collect).orEmpty() }
    }
    return collect(this)
}
