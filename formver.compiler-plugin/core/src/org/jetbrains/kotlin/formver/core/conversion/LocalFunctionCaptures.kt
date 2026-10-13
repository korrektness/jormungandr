/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.locality.plugin.resolveCapturedSymbols
import org.jetbrains.kotlin.formver.uniqueness.plugin.uniquenessAnalysisGraph

/**
 * A local function is verified as a declaration of its own, where its enclosing function's parameters and locals
 * are not in scope.
 */
context(context: CheckerContext)
fun FirFunction.rejectLocalCaptures() {
    if (symbol.callableId?.isLocal != true) return
    val graph = uniquenessAnalysisGraph ?: return
    val captured = graph.resolveCapturedSymbols()
        .filter { it is FirValueParameterSymbol || it is FirPropertySymbol && it.isLocal }
    if (captured.isNotEmpty()) {
        throw UnsupportedFeatureException(source, "a local function that captures a variable")
    }
}
