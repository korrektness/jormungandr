package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirFunctionTarget
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.BlockExitNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.JumpNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ThrowExceptionNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.render
import org.jetbrains.kotlin.fir.resolve.dfa.controlFlowGraph
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.formver.locality.plugin.Locality
import org.jetbrains.kotlin.formver.locality.plugin.resolveLocality
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors.EXIT_UNIQUENESS_INCONSISTENCY

/**
 * Resolves the locality relevant for deciding whether a symbol must be restored before function exit.
 */
context(context: CheckerContext)
val FirBasedSymbol<*>.locality: Locality
    get() = when (this) {
        is FirVariableSymbol<*> -> resolveLocality()
        is FirReceiverParameterSymbol -> resolveLocality()
        else -> Locality.Global
    }

/**
 * Checks that local roots, and the paths below them, are not moved when a function exits.
 *
 * The parameters of a `@Pure` function are borrowed from its caller, so they are checked as local roots too.
 *
 * TODO: Do not consider locally caught `throw`s as exit operations.
 */
object FunctionExitUniquenessConsistencyChecker : FirFunctionChecker( MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        val graph = declaration.controlFlowGraphReference?.controlFlowGraph ?: return
        val uniquenessStateFlows = graph.resolveUniquenessStateFlows()
        val pureParameters: Set<FirBasedSymbol<*>> =
            if (declaration.symbol.isPure(context.session)) {
                val parameters = listOfNotNull(declaration.receiverParameter) + declaration.valueParameters
                parameters.mapTo(mutableSetOf()) { it.symbol }
            } else {
                emptySet()
            }

        fun CFGNode<*>.isExit(): Boolean =
            when (this) {
                is ThrowExceptionNode -> true
                is JumpNode -> {
                    val jumpTarget = fir.target

                    jumpTarget is FirFunctionTarget && jumpTarget.labeledElement == declaration
                }
                is BlockExitNode -> {
                    !isDead && fir == declaration.body
                }
                else -> false
            }

        for (node in graph.uniquenessAnalysisTargetNodes) {
            if (node.isDead || !node.isExit()) continue

            val outputUniquenessState = uniquenessStateFlows[node]?.joinOverEdgeKinds()
                ?: error("Output uniqueness state for ${node.render()} not present in analysis result.")
            val rootUniquenessStates = outputUniquenessState.children

            for ((symbol, uniquenessState) in rootUniquenessStates) {
                if (symbol !in pureParameters && symbol.locality != Locality.Local) continue

                for (inconsistentPath in uniquenessState.enumerateInconsistentPaths(includeRoot = true)) {
                    reporter.reportOn(
                        node.fir.source ?: declaration.source,
                        EXIT_UNIQUENESS_INCONSISTENCY,
                        listOf(symbol) + inconsistentPath
                    )
                }
            }
        }
    }
}
