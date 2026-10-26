/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.locality.plugin

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.unwrapExpression
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CallableReferenceNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ControlFlowGraph
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.FunctionCallExitNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.QualifiedAccessNode
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.utils.addIfNotNull

/**
 * Resolver for the captured symbols of a graph.
 *
 * @param session The session argument for initializing the [FirExtensionSessionComponent] and the cache.
 */
class GraphCapturedSymbolsResolver(session: FirSession) : FirExtensionSessionComponent(session) {
    companion object {
        fun getFactory(): Factory =
            Factory { currentSession -> GraphCapturedSymbolsResolver(currentSession) }
    }

    private val cache = session.firCachesFactory.createCache { graph: ControlFlowGraph, context: CheckerContext ->
        with(context) {
            extractCapturedSymbolsOf(graph)
        }
    }

    fun resolveCapturedSymbolsOf(graph: ControlFlowGraph, context: CheckerContext): Set<FirBasedSymbol<*>> =
        cache.getValue(graph, context)

    context(context: CheckerContext)
    private fun extractCapturedSymbolsOf(graph: ControlFlowGraph): Set<FirBasedSymbol<*>> {
        val capturedSymbols = mutableSetOf<FirBasedSymbol<*>>()

        for (node in graph.nodes) {
            when (node) {
                is QualifiedAccessNode -> {
                    capturedSymbols.addIfNotNull(node.fir.calleeReference.symbol)
                    node.fir.collectReceiverSymbolsTo(capturedSymbols)
                }

                is CallableReferenceNode -> {
                    capturedSymbols.addIfNotNull(node.fir.calleeReference.symbol)
                    node.fir.collectReceiverSymbolsTo(capturedSymbols)
                }

                is FunctionCallExitNode -> {
                    node.fir.collectReceiverSymbolsTo(capturedSymbols)
                    val callee = node.fir.calleeReference.symbol as? FirNamedFunctionSymbol
                    // A call to a local function retains that function's lexical captures.
                    if (callee?.callableId?.isLocal == true) capturedSymbols.add(callee)
                }

                else -> { }
            }
        }

        graph.subGraphs.flatMapTo(capturedSymbols, { it.resolveCapturedSymbols() })
        val declaredSymbols = graph.resolveDeclaredSymbols()

        return capturedSymbols - declaredSymbols
    }

    private fun FirQualifiedAccessExpression.collectReceiverSymbolsTo(symbols: MutableSet<FirBasedSymbol<*>>) {
        // Implicit `this` receivers need not have their own access node in the control-flow graph.
        for (receiver in listOfNotNull(dispatchReceiver, extensionReceiver)) {
            val thisReceiver = receiver.unwrapExpression() as? FirThisReceiverExpression ?: continue
            symbols.addIfNotNull(thisReceiver.calleeReference.symbol)
        }
    }
}

private val FirSession.graphCapturedSymbolsResolver: GraphCapturedSymbolsResolver
    by FirSession.sessionComponentAccessor()

/**
 * Resolves symbols captured by [this] graph.
 *
 * Captures are computed as symbols used by graph accesses (including nested sub-graphs in the current implementation)
 * minus symbols declared by [resolveDeclaredSymbols].
 *
 * @param context Is used for resolving the declared symbols with [resolveDeclaredSymbols] and to access this session's
 *  [GraphCapturedSymbolsResolver].
 *
 * The result is memoized by a session cache bound by the [GraphCapturedSymbolsResolver] corresponding to [context]s
 * session.
 */
context(context: CheckerContext)
fun ControlFlowGraph.resolveCapturedSymbols(): Set<FirBasedSymbol<*>> =
    context.session.graphCapturedSymbolsResolver.resolveCapturedSymbolsOf(this, context)
