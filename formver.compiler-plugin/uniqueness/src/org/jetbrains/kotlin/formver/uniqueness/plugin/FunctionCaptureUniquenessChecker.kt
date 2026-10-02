/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNodeWithSubgraphs
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.formver.locality.plugin.resolveCapturedSymbols
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors.INVALID_UNIQUENESS_CAPTURE

/**
 * Checks that no lambda, anonymous object, local class or local function that is analyzed apart from [FirFunction]'s
 * flow captures a root that is [Uniqueness.Unique] or [Uniqueness.Unknown] at any point of that flow. Such a
 * declaration may run at any time, so the analysis cannot account for its uses of the root.
 *
 * Lambdas in specifications are exempt: they are never run.
 */
object FunctionCaptureUniquenessChecker : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        val graph = declaration.uniquenessAnalysisGraph ?: return
        val uniquenessStateFlows = graph.resolveUniquenessStateFlows()
        val nodes = graph.uniquenessAnalysisTargetNodes

        val ownedRoots = mutableSetOf<FirBasedSymbol<*>>()
        for (node in nodes) {
            val rootUniquenessStates = uniquenessStateFlows[node].joinOverEdgeKinds().children
            for ((symbol, uniquenessState) in rootUniquenessStates) {
                if (uniquenessState.data <= Uniqueness.Unknown) ownedRoots.add(symbol)
            }
        }

        val specifications = ReadOnlyContext.specificationsOf(graph, context)
        val separateGraphs = nodes
            .flatMap { node -> (node as? CFGNodeWithSubgraphs<*>)?.subGraphs.orEmpty() }
            .filterNot { it.extendsLocalFlow }
            .filterNot { separateGraph -> separateGraph.declaration.let { it != null && it in specifications } }
            .toSet()

        for (separateGraph in separateGraphs) {
            for (symbol in separateGraph.resolveCapturedSymbols()) {
                if (symbol !in ownedRoots) continue

                reporter.reportOn(
                    separateGraph.declaration?.source ?: declaration.source,
                    INVALID_UNIQUENESS_CAPTURE,
                    listOf(symbol),
                )
            }
        }
    }
}
