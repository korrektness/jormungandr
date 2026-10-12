/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.directOverriddenSymbolsSafe
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.scopes.ScopeFunctionRequiresPrewarm
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.formver.core.isObserverOrOverride

/** A runtime implementation and the contracts inherited by its receiving class. */
data class InheritedImplementationObligation(
    val implementation: FirNamedFunctionSymbol,
    val contracts: List<FirNamedFunctionSymbol>,
)

context(context: CheckerContext)
@OptIn(ScopeFunctionRequiresPrewarm::class, SymbolInternals::class)
fun FirRegularClassSymbol.inheritedImplementationObligations(): List<InheritedImplementationObligation> {
    val scope = unsubstitutedScope()
    val obligations = mutableListOf<InheritedImplementationObligation>()
    for (name in scope.getCallableNames()) {
        scope.processFunctionsByName(name) { member ->
            if (member.isObserverOrOverride()) return@processFunctionsByName
            val declarations = linkedSetOf<FirNamedFunctionSymbol>()
            fun collect(symbol: FirNamedFunctionSymbol) {
                if (!declarations.add(symbol)) return
                symbol.directOverriddenSymbolsSafe().filterIsInstance<FirNamedFunctionSymbol>().forEach(::collect)
            }
            collect(member)
            val implementation = declarations.asSequence().map { it.unwrapFakeOverrides() }
                .firstOrNull { !it.isAbstract && it.fir.body != null } ?: return@processFunctionsByName
            if (implementation.getContainingClassSymbol() == this) return@processFunctionsByName
            val contracts = declarations.mapNotNull { it.specificationOwner() }.distinct()
            if (contracts.isNotEmpty()) obligations += InheritedImplementationObligation(implementation, contracts)
        }
    }
    return obligations.groupBy { it.implementation }.map { (implementation, entries) ->
        InheritedImplementationObligation(implementation, entries.flatMap { it.contracts }.distinct())
    }
}
