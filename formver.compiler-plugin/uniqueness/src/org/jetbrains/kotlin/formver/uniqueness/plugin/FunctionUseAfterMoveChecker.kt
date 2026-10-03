package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ExitSafeCallNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.QualifiedAccessNode
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors.INVALID_MOVED_ACCESS
import org.jetbrains.kotlin.text

/**
 * Resolves expressions that read paths from the uniqueness state at this CFG node.
 *
 * Currently the expressions are extracted from either [QualifiedAccessNode] and [ExitSafeCallNode], as both node types
 * may represent a field access.
 */
private fun CFGNode<*>.resolveAccess(): FirExpression? =
    when (this) {
        is QualifiedAccessNode -> fir
        is ExitSafeCallNode -> fir
        else -> null
    }

private fun UniquenessState?.isMovedAt(path: Path): Boolean =
    this?.find(path)?.data == Uniqueness.Moved

/**
 * Whether [this] access goes through an implicit `this`, which has no CFG node of its own to report a moved `this` at.
 */
private val FirExpression.hasImplicitThisReceiver: Boolean
    get() = ((this as? FirQualifiedAccessExpression)?.dispatchReceiver as? FirThisReceiverExpression)?.isImplicit == true

/**
 * Renders the source text of [sites] in source order, with each run of whitespace collapsed to one space.
 */
private fun renderMoveSites(sites: Set<CFGNode<*>>): String =
    sites.mapNotNull { it.fir.source }
        .sortedBy { it.startOffset }
        .mapNotNull { source -> source.text?.toString()?.replace(Regex("\\s+"), " ") }
        .distinct()
        .joinToString("', '")

/**
 * Checks that expressions do not read paths that have already been moved.
 *
 * Each moved path is reported once for the moves that reach it, at its first access in source order, and the report
 * names those moves. Later accesses to the same path after the same moves are not reported again.
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
            .mapNotNull { node -> node.resolveAccess()?.let { node to it } }
            .sortedBy { (_, accessExpression) -> accessExpression.source?.startOffset }

        for ((node, accessExpression) in accesses) {
            val accessState = accessExpression.resolveAccessState()
            val uniquenessState = uniquenessStateFlows.readInputUniquenessStateOf(node)

            for (accessedPath in accessState.enumeratePaths()) {
                val path = accessedPath.takeIf { uniquenessState.isMovedAt(it) }
                    ?: accessedPath.take(1).takeIf { accessExpression.hasImplicitThisReceiver && uniquenessState.isMovedAt(it) }
                    ?: continue
                val sites = moveSites.readInputMoveSitesOf(node, path)
                if (reported.add(path to sites)) {
                    reporter.reportOn(accessExpression.source, INVALID_MOVED_ACCESS, path, renderMoveSites(sites))
                }
            }
        }
    }
}
