/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.cfa.util.previousCfgNodes
import org.jetbrains.kotlin.fir.analysis.cfa.util.traverseToFixedPoint
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ControlFlowGraph
import org.jetbrains.kotlin.fir.resolve.dfa.controlFlowGraph
import org.jetbrains.kotlin.formver.locality.plugin.CallArgumentLocalitiesMapper
import org.jetbrains.kotlin.formver.readonly.plugin.ReadOnlyContext

/**
 * Session component that caches uniqueness-state flow analysis for control-flow graphs.
 */
class GraphUniquenessStatesResolver(session: FirSession) : FirExtensionSessionComponent(session) {
    companion object {
        fun getFactory(): Factory {
            return Factory { session -> GraphUniquenessStatesResolver(session) }
        }
    }

    private val cache = session.firCachesFactory.createCache { graph: ControlFlowGraph, context: CheckerContext ->
        analyzeUniquenessStatesOf(graph, context)
    }

    fun resolveUniquenessStateFlowsOf(
        graph: ControlFlowGraph,
        context: CheckerContext
    ): Map<CFGNode<*>, PathAwareUniquenessStateFlow> =
        cache.getValue(graph, context)

    private fun analyzeUniquenessStatesOf(
        graph: ControlFlowGraph,
        context: CheckerContext
    ): Map<CFGNode<*>, PathAwareUniquenessStateFlow> {
        val declaration = graph.declaration
        val initialState = if (declaration is FirFunction) {
            context(context) { EmptyUniquenessState.initializeParametersOf(declaration) }
        } else {
            EmptyUniquenessState
        }

        val analyzer = GraphUniquenessStatesAnalyzer(
            initialState,
            context,
            CallArgumentLocalitiesMapper,
            ReadOnlyContext.of(graph, context),
        )

        return graph.traverseToFixedPoint(analyzer)
    }
}

/**
 * The graph whose analysis covers [this] function on its own, or null when the function has no graph or is a lambda
 * called in place, which is analyzed as part of its enclosing function.
 */
val FirFunction.uniquenessAnalysisGraph: ControlFlowGraph?
    get() = controlFlowGraphReference?.controlFlowGraph?.takeUnless { it.extendsLocalFlow }

private val FirSession.graphUniquenessStatesResolver: GraphUniquenessStatesResolver
        by FirSession.sessionComponentAccessor()

/**
 * Resolves the uniqueness-state flow analysis for [this] graph.
 */
context(context: CheckerContext)
fun ControlFlowGraph.resolveUniquenessStateFlows(): Map<CFGNode<*>, PathAwareUniquenessStateFlow> =
    context.session.graphUniquenessStatesResolver.resolveUniquenessStateFlowsOf(this, context)

/**
 * Reads the uniqueness state before [node] by joining the output states of its predecessors.
 */
fun Map<CFGNode<*>, PathAwareUniquenessStateFlow>.readInputUniquenessStateOf(node: CFGNode<*>): UniquenessState? =
    node.previousCfgNodes
        .map { predecessor -> this[predecessor].joinOverEdgeKinds() }
        .reduceOrNull(UniquenessState::join)

/**
 * Reads the uniqueness state after [node] by joining all path edge kinds.
 */
fun Map<CFGNode<*>, PathAwareUniquenessStateFlow>.readOutputUniquenessStateOf(node: CFGNode<*>): UniquenessState =
    this[node].joinOverEdgeKinds()
