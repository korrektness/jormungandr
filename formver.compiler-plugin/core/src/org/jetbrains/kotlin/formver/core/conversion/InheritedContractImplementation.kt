/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.directOverriddenSymbolsSafe
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides

/** Inherited bodies have no refinement obligation under the receiving class's observer bindings. */
context(context: CheckerContext)
fun FirNamedFunctionSymbol.inheritedContractImplementation(owner: FirRegularClassSymbol): Boolean {
    if (origin == FirDeclarationOrigin.Source && getContainingClassSymbol() == owner) return false
    val overridden = directOverriddenSymbolsSafe().filterIsInstance<FirNamedFunctionSymbol>()
    val inheritedBody = (listOf(this) + overridden).map { it.unwrapFakeOverrides() }
        .any { !it.isAbstract && it.getContainingClassSymbol() != owner }
    return inheritedBody && overridden.any { it.specificationOwner() != null }
}
