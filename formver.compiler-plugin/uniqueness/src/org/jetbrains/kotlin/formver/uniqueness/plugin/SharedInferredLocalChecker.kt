/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.FunctionCallEnterNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.JumpNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.VariableAssignmentNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.VariableDeclarationNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.render
import org.jetbrains.kotlin.formver.common.FactPosition
import org.jetbrains.kotlin.formver.locality.plugin.specifiesOwnedReceiver
import org.jetbrains.kotlin.formver.type.plugin.reportTypeFactFailure

/**
 * Reports an inferred local that has become shared or borrowed (see [hasInferredUniqueness]) where a declaration
 * requires a unique value: an argument or receiver of a parameter that owns it, the result of a function returning
 * `@Unique`, and a value written to a `@Unique` variable or property. A receiver of a member whose external
 * specification owns it is exempt: a call on a receiver that is not unique is the unchecked library call. The
 * flow-insensitive checkers take such a local as unique, and report one inferred shared from its initializer
 * themselves.
 */
object SharedInferredLocalChecker : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        val graph = declaration.uniquenessAnalysisGraph ?: return
        val flows by lazy { graph.resolveUniquenessStateFlows() }
        for (node in graph.uniquenessAnalysisTargetNodes) {
            if (node.isDead) continue
            val required = node.uniqueValues()
            if (required.isEmpty()) continue
            val state = flows.readInputUniquenessStateOf(node)
                ?: error("No uniqueness state flows into ${node.render()} of ${declaration.symbol}.")
            for ((expression, position) in required) {
                if (expression.readsSharedInferredLocal(state)) {
                    reporter.reportTypeFactFailure(
                        expression.source, UniquenessErrors.UNIQUENESS_MISMATCH, position,
                        Uniqueness.Unique, Uniqueness.Shared,
                    )
                }
            }
        }
    }

    context(context: CheckerContext)
    private fun CFGNode<*>.uniqueValues(): List<Pair<FirExpression, FactPosition>> =
        when (this) {
            is FunctionCallEnterNode -> {
                val call = fir
                val callee = call.toResolvedCallableSymbol()
                val ownedAnyway = call.dispatchReceiver.takeIf { callee?.specifiesOwnedReceiver(context.session) == true }
                CallArgumentUniquenessesMapper.mapArgumentTypeFactsOf(call)
                    .filter { (_, uniqueness) -> uniqueness == Uniqueness.Unique }
                    .map { (argument, _) -> argument to FactPosition.ARGUMENT } +
                        QualifiedAccessArgumentUniquenessMapper.mapArgumentTypeFactsOf(call)
                            .filter { (receiver, uniqueness) -> uniqueness == Uniqueness.Unique && receiver !== ownedAnyway }
                            .map { (receiver, _) -> receiver to FactPosition.RECEIVER }
            }
            is VariableAssignmentNode -> {
                val target = fir.lValue
                if (target.wholeInferredLocal() == null && target.resolveUniqueness() == Uniqueness.Unique)
                    listOf(fir.rValue to FactPosition.ASSIGNMENT)
                else emptyList()
            }
            is VariableDeclarationNode -> {
                val initializer = fir.initializer
                if (initializer != null && !fir.symbol.hasInferredUniqueness && fir.symbol.resolveUniqueness() == Uniqueness.Unique)
                    listOf(initializer to FactPosition.INITIALIZER)
                else emptyList()
            }
            is JumpNode -> {
                val jump = fir as? FirReturnExpression
                if (jump != null && jump.resolveResultUniqueness() == Uniqueness.Unique) listOf(jump.result to FactPosition.RETURN)
                else emptyList()
            }
            else -> emptyList()
        }

    context(context: CheckerContext)
    private fun FirExpression.readsSharedInferredLocal(state: UniquenessState): Boolean =
        resolveAccessState().enumeratePaths().any { path ->
            val root = path.first()
            root.hasInferredUniqueness && root.resolveDeclaredUniqueness() == Uniqueness.Unique &&
                    state.children[root]?.data.let { it == Uniqueness.Shared || it == Uniqueness.Unknown }
        }
}
