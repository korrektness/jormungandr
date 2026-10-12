/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.formver.uniqueness.plugin.FunctionUniquenessAnalysis
import org.jetbrains.kotlin.formver.uniqueness.plugin.uniquenessFacts

/** The analysis is absent when the function has no body or has uniqueness or locality errors. */
data class UniquenessOutcome(val analysis: FunctionUniquenessAnalysis?, val hasErrors: Boolean)

internal class UniquenessOutcomes(private val session: FirSession, private val context: CheckerContext) {
    private val outcomes = mutableMapOf<FirFunctionSymbol<*>, UniquenessOutcome>()

    fun cached(symbol: FirFunctionSymbol<*>): UniquenessOutcome? = outcomes[symbol]

    operator fun get(declaration: FirFunction): UniquenessOutcome = outcomes.getOrPut(declaration.symbol) {
        if (declaration.body == null && !(declaration is FirConstructor && declaration.origin == FirDeclarationOrigin.Source)) {
            UniquenessOutcome(analysis = null, hasErrors = false)
        } else {
            val analysis = context(context) { session.uniquenessFacts.analysis(declaration) }
            UniquenessOutcome(analysis, hasErrors = analysis == null)
        }
    }
}
