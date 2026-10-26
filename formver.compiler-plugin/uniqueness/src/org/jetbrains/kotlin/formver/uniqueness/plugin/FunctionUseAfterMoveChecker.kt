package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.AnonymousFunctionCaptureNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ExitSafeCallNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.FunctionCallEnterNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.QualifiedAccessNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.VariableAssignmentNode
import org.jetbrains.kotlin.fir.resolve.dfa.controlFlowGraph
import org.jetbrains.kotlin.formver.locality.plugin.resolveCapturedSymbols

/**
 * Resolves path accesses and assignment targets at this CFG node.
 */
private fun CFGNode<*>.resolveAccess(): FirExpression? =
    when (this) {
        is QualifiedAccessNode -> fir
        is ExitSafeCallNode -> fir
        is VariableAssignmentNode -> fir.lValue
        else -> null
    }

private data class NodeAccess(val node: CFGNode<*>, val expression: FirExpression, val source: KtSourceElement?)

/** Implicit context arguments have no access node of their own in the CFG. */
private fun CFGNode<*>.resolveAccesses(): Sequence<NodeAccess> = sequence {
    val access = resolveAccess()
    if (access != null) yield(NodeAccess(this@resolveAccesses, access, access.source))
    val qualified = (if (this@resolveAccesses is FunctionCallEnterNode) fir else access) as? FirQualifiedAccessExpression
        ?: return@sequence
    for (argument in qualified.contextArguments) {
        if (argument.source?.kind is KtFakeSourceElementKind.ImplicitContextParameterArgument) {
            yield(NodeAccess(this@resolveAccesses, argument, qualified.source))
        }
    }
}

private fun UniquenessState?.isMovedAt(path: Path): Boolean =
    this?.find(path)?.data == Uniqueness.Moved

/**
 * Whether [this] access goes through an implicit `this`, which has no CFG node of its own to report a moved `this` at.
 */
private val FirExpression.hasImplicitThisReceiver: Boolean
    get() = ((this as? FirQualifiedAccessExpression)?.dispatchReceiver as? FirThisReceiverExpression)?.isImplicit == true

/**
 * Checks that expressions do not read paths that have already been moved.
 *
 * Each moved path is reported once for the moves that reach it, at its first access in source order, and the report
 * retains those move locations. Later accesses to the same path after the same moves are not reported again.
 */
object FunctionUseAfterMoveChecker : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        val graph = declaration.uniquenessAnalysisGraph ?: return
        val uniquenessStateFlows by lazy { graph.resolveUniquenessStateFlows() }
        val moveSites by lazy { graph.resolveMoveSites(uniquenessStateFlows) }
        val reported = mutableSetOf<Pair<Path, Set<CFGNode<*>>>>()

        val accesses = graph.uniquenessAnalysisTargetNodes
            .filterNot { it.isDead }
            .flatMap { it.resolveAccesses() }
            .sortedBy { it.source?.startOffset }

        for ((node, accessExpression, source) in accesses) {
            val accessState = accessExpression.resolveAccessState()
            val uniquenessState = uniquenessStateFlows.readInputUniquenessStateOf(node)

            for (accessedPath in accessState.enumeratePaths()) {
                val path = accessedPath.takeIf { uniquenessState.isMovedAt(it) }
                    ?: accessedPath.take(1).takeIf { accessExpression.hasImplicitThisReceiver && uniquenessState.isMovedAt(it) }
                    ?: continue
                val sites = moveSites.readInputMoveSitesOf(node, path)
                if (node is VariableAssignmentNode && accessExpression === node.fir.lValue &&
                    sites.none { it is AnonymousFunctionCaptureNode }) continue
                if (reported.add(path to sites)) {
                    reporter.reportFailure(movedAccessFailure(source, path, sites.mapNotNull { it.fir.source }))
                }
            }
        }

        val specified = declaration.specifiedLambdas()
        for (node in graph.uniquenessAnalysisTargetNodes.filterIsInstance<AnonymousFunctionCaptureNode>()) {
            val lambda = node.fir.anonymousFunction
            if (node.isDead || lambda !in specified) continue
            val captures = lambda.controlFlowGraphReference?.controlFlowGraph ?: error("Specified lambda has no CFG")
            val input = uniquenessStateFlows.readInputUniquenessStateOf(node)
            for (symbol in captures.resolveCapturedSymbols() + captures.capturedReceivers()) {
                val root = input?.children?.get(symbol) ?: continue
                for (moved in root.enumerateInconsistentPaths(includeRoot = true)) {
                    val path = listOf(symbol) + moved
                    val sites = moveSites.readInputMoveSitesOf(node, path)
                    if (reported.add(path to sites)) {
                        reporter.reportFailure(movedAccessFailure(node.fir.source, path, sites.mapNotNull { it.fir.source }))
                    }
                }
            }
        }
    }
}
