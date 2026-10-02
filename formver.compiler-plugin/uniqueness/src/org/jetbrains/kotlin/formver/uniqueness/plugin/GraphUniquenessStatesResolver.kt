/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.cfa.util.previousCfgNodes
import org.jetbrains.kotlin.fir.analysis.cfa.util.traverseToFixedPoint
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ControlFlowGraph
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.NormalPath
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
    ): UniquenessStateFlows =
        cache.getValue(graph, context)

    private fun analyzeUniquenessStatesOf(
        graph: ControlFlowGraph,
        context: CheckerContext
    ): UniquenessStateFlows {
        val maxPathLength = context(context) { graph.longestPathLength() }
        return UniquenessStateFlows(
            graph.traverseToFixedPoint(graph.uniquenessStatesAnalyzer(context, maxPathLength)),
            maxPathLength,
        )
    }
}

/**
 * The result of the uniqueness analysis of a graph: the output flow of each node, and the path length that joins
 * truncate to.
 */
class UniquenessStateFlows(
    private val outputs: Map<CFGNode<*>, PathAwareUniquenessStateFlow>,
    private val maxPathLength: Int,
) {
    operator fun get(node: CFGNode<*>): PathAwareUniquenessStateFlow? = outputs[node]

    /**
     * Reads the uniqueness state before [node] by joining the output states of its predecessors, truncated as the
     * analysis truncates its joins.
     */
    fun readInputUniquenessStateOf(node: CFGNode<*>): UniquenessState? {
        val inputs = node.previousCfgNodes.map { predecessor -> outputs[predecessor].joinOverEdgeKinds() }
        return if (inputs.size > 1) inputs.reduce(UniquenessState::join).truncate(maxPathLength) else inputs.singleOrNull()
    }

    /**
     * Reads the uniqueness state after [node] by joining all path edge kinds.
     */
    fun readOutputUniquenessStateOf(node: CFGNode<*>): UniquenessState =
        outputs[node].joinOverEdgeKinds()
}

/**
 * The number of components in the longest path that an expression or assignment target of [this] graph accesses, and
 * at least one.
 */
context(context: CheckerContext)
private fun ControlFlowGraph.longestPathLength(): Int =
    uniquenessAnalysisTargetNodes.maxOfOrNull { node ->
        when (val element = node.fir) {
            is FirVariableAssignment -> element.lValue.resolveAccessState().height
            is FirExpression -> element.resolveAccessState().height
            else -> 0
        }
    }?.coerceAtLeast(1) ?: 1

private fun ControlFlowGraph.uniquenessStatesAnalyzer(
    context: CheckerContext,
    maxPathLength: Int,
): GraphUniquenessStatesAnalyzer {
    val declaration = declaration
    val initialState = if (declaration is FirFunction) {
        context(context) { EmptyUniquenessState.initializeParametersOf(declaration) }
    } else {
        EmptyUniquenessState
    }

    return GraphUniquenessStatesAnalyzer(
        initialState,
        maxPathLength,
        context,
        CallArgumentLocalitiesMapper,
        ReadOnlyContext.of(this, context),
    )
}

/**
 * The uniqueness state after a node of [this] graph, given the state before it.
 */
context(context: CheckerContext)
fun ControlFlowGraph.uniquenessTransfer(): (CFGNode<*>, UniquenessState) -> UniquenessState {
    // A single node joins nothing, so the truncation length is never used.
    val analyzer = uniquenessStatesAnalyzer(context, maxPathLength = Int.MAX_VALUE)
    return { node, state ->
        node.accept(analyzer, persistentMapOf(NormalPath to persistentMapOf(Unit to state))).joinOverEdgeKinds()
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
fun ControlFlowGraph.resolveUniquenessStateFlows(): UniquenessStateFlows =
    context.session.graphUniquenessStatesResolver.resolveUniquenessStateFlowsOf(this, context)
