/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.expressions.unwrapAndFlattenArgument
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ExitDefaultArgumentsNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.FunctionCallEnterNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.JumpNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.QualifiedAccessNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.VariableAssignmentNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.VariableDeclarationNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.render
import org.jetbrains.kotlin.fir.resolve.dfa.controlFlowGraph
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.common.FactPosition
import org.jetbrains.kotlin.formver.type.plugin.reportTypeFactFailure

/** Reports ownership lost in flow at a use whose declared types require ownership. */
object EffectiveUniquenessChecker : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirDeclaration) {
        val graph = when (declaration) {
            is FirFunction -> declaration.uniquenessAnalysisGraph
            is FirProperty -> declaration.takeUnless { it.symbol.isLocal }?.controlFlowGraphReference?.controlFlowGraph
            is FirAnonymousInitializer -> declaration.controlFlowGraphReference?.controlFlowGraph
            else -> null
        } ?: return
        val flows by lazy { graph.resolveUniquenessStateFlows() }
        for (node in graph.uniquenessAnalysisTargetNodes) {
            if (node.isDead) continue
            val required = node.uniqueValues()
            if (required.isEmpty()) continue
            val state = flows.readInputFactsOf(node)
                ?: error("No uniqueness state flows into ${node.render()} of ${declaration.symbol}.")
            for (use in required) use.check(state)
        }
        if (declaration is FirProperty && declaration.symbol.resolveUniqueness() == Uniqueness.Unique) {
            val initializer = declaration.initializer ?: return
            val state = flows.readInputFactsOf(graph.exitNode) ?: return
            UniqueUse(initializer, FactPosition.INITIALIZER).check(state)
        }
    }

    private class UniqueUse(
        val expression: FirExpression,
        val position: FactPosition,
        val source: KtSourceElement? = expression.source,
        val contextArgument: Boolean = false,
    ) {
        context(context: CheckerContext, reporter: DiagnosticReporter)
        fun check(state: UniquenessFlowFacts) {
            val sharedInferredRoot = expression.resolveAccessState().children.keys.any { root ->
                val fact = state.paths.children[root]?.data
                (fact == Uniqueness.Shared || fact == Uniqueness.Unknown) &&
                    root.hasInferredUniqueness && root.resolveDeclaredUniqueness() == Uniqueness.Unique
            }
            if (!sharedInferredRoot && !state.hasSharedModelResult(expression)) return
            if (expression.resolveUniqueness() != Uniqueness.Unique) return
            val actual = state.uniquenessOf(expression).takeUnless { it == Uniqueness.Unique || it == Uniqueness.Moved }
                ?: Uniqueness.Shared
            if (contextArgument) {
                reporter.reportTypeFactFailure(source, UniquenessErrors.CONTEXT_UNIQUENESS_MISMATCH,
                    expression.resolvedType, Uniqueness.Unique, actual)
            } else {
                reporter.reportTypeFactFailure(source, UniquenessErrors.UNIQUENESS_MISMATCH,
                    position, Uniqueness.Unique, actual)
            }
        }
    }

    context(context: CheckerContext)
    private fun FirQualifiedAccessExpression.receiverUses(): List<UniqueUse> {
        val gated = toResolvedCallableSymbol()?.let { receiverRequiringModelOwnership(it) }
        return QualifiedAccessArgumentUniquenessMapper.mapArgumentTypeFactsOf(this)
            .filter { (argument, uniqueness) -> argument !== gated && uniqueness == Uniqueness.Unique }
            .map { (argument, _) ->
                val isContext = argument in contextArguments
                UniqueUse(argument, FactPosition.RECEIVER, if (isContext) source else argument.source ?: source, isContext)
            }
    }

    context(context: CheckerContext)
    private fun CFGNode<*>.uniqueValues(): List<UniqueUse> = when (this) {
        is FunctionCallEnterNode -> {
            val call = fir
            CallArgumentUniquenessesMapper.mapArgumentTypeFactsOf(call)
                .filter { (_, uniqueness) -> uniqueness == Uniqueness.Unique }
                .flatMap { (argument, _) ->
                    val isContext = argument.source?.kind is KtFakeSourceElementKind.ImplicitContextParameterArgument
                    argument.unwrapAndFlattenArgument(flattenArrays = false).map {
                        UniqueUse(it, FactPosition.ARGUMENT, if (isContext) call.source else it.source ?: argument.source, isContext)
                    }
                } + call.receiverUses()
        }
        is QualifiedAccessNode -> fir.receiverUses()
        is VariableAssignmentNode -> {
            val target = fir.lValue
            val receiverUses = (target as? FirQualifiedAccessExpression)?.receiverUses().orEmpty()
            if (target.wholeInferredLocal() == null && target.resolveUniqueness() == Uniqueness.Unique)
                receiverUses + UniqueUse(fir.rValue, FactPosition.ASSIGNMENT)
            else receiverUses
        }
        is VariableDeclarationNode -> {
            val initializer = fir.initializer
            if (initializer != null && !fir.symbol.hasInferredUniqueness && fir.symbol.resolveUniqueness() == Uniqueness.Unique)
                listOf(UniqueUse(initializer, FactPosition.INITIALIZER))
            else emptyList()
        }
        is ExitDefaultArgumentsNode -> {
            val default = fir.defaultValue
            if (default != null && ParameterUniquenessResolver.resolveTypeFactOf(fir.symbol) == Uniqueness.Unique)
                listOf(UniqueUse(default, FactPosition.INITIALIZER))
            else emptyList()
        }
        is JumpNode -> {
            val jump = fir as? FirReturnExpression
            if (jump != null && jump.resolveResultUniqueness() == Uniqueness.Unique) listOf(UniqueUse(jump.result, FactPosition.RETURN))
            else emptyList()
        }
        else -> emptyList()
    }
}
