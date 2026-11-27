/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNodeWithSubgraphs
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ControlFlowGraph
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.locality.plugin.resolveCapturedSymbols
import org.jetbrains.kotlin.formver.readonly.plugin.ReadOnlyContext

/**
 * The classes whose `this` the declaration of [this] graph references, explicitly or as an implicit receiver.
 */
internal fun ControlFlowGraph.capturedReceivers(): Set<FirBasedSymbol<*>> {
    val receivers = mutableSetOf<FirBasedSymbol<*>>()
    declaration?.accept(object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            if (element is FirThisReceiverExpression) {
                (element.calleeReference.symbol as? FirClassSymbol<*>)?.let { receivers.add(it) }
            }
            element.acceptChildren(this)
        }
    })
    return receivers
}

/**
 * Whether [this] root's type is written in the source, rather than inferred from an initializer, and declares it owned.
 */
context(context: CheckerContext)
private fun FirBasedSymbol<*>.isAnnotatedOwned(): Boolean {
    if (this is FirVariableSymbol<*> && resolvedReturnTypeRef.source?.kind is KtFakeSourceElementKind.ImplicitTypeRef) return false
    return resolveDeclaredUniqueness() <= Uniqueness.Unknown
}

/**
 * Rejects a declaration that is not called in place when it captures an owned root. A lambda may capture a root the
 * analysis only inferred owned; unless it is inlined, the root is shared from the capture on (see
 * [GraphUniquenessStatesAnalyzer]).
 */
object FunctionCaptureUniquenessChecker : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        val graph = declaration.uniquenessAnalysisGraph ?: return
        val uniquenessStateFlows = graph.resolveUniquenessStateFlows()
        val nodes = graph.uniquenessAnalysisTargetNodes

        val specifications = ReadOnlyContext.specificationsOf(graph, context)
        val specifiedLambdas = declaration.specifiedLambdas()
        val separateGraphs = nodes
            .flatMap { node -> (node as? CFGNodeWithSubgraphs<*>)?.subGraphs.orEmpty() }
            .filterNot { it.extendsLocalFlow || it.declaration in specifiedLambdas }
            .filterNot { separateGraph -> separateGraph.declaration.let { it != null && it in specifications } }
            .toSet()

        for (separateGraph in separateGraphs) {
            for (symbol in separateGraph.resolveCapturedSymbols() + separateGraph.capturedReceivers()) {
                val source = symbol.borrowedViewSource()
                if (source != null && source.resolveDeclaredUniqueness() != Uniqueness.Unique) continue
                val root = source?.firstOrNull() ?: symbol
                var isOwnedRoot = false
                for (node in nodes) {
                    val state = uniquenessStateFlows[node].joinOverEdgeKinds().children[root]
                    if (state != null && state.data <= Uniqueness.Unknown) {
                        isOwnedRoot = true
                        break
                    }
                }
                if (!isOwnedRoot) continue
                if (separateGraph.declaration is FirAnonymousFunction && !root.isAnnotatedOwned()) continue

                val boundary = separateGraph.declaration?.source ?: declaration.source
                reporter.reportFailure(uniqueCaptureFailure(boundary, listOf(root), boundary))
            }
        }
    }
}
