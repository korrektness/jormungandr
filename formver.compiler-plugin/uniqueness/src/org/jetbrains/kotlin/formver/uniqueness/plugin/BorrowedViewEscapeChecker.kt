package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.readonly.plugin.*

/** Classifies each owned-view use in its lexical read-only region; inline formals are an explicit boundary. */
object BorrowedViewEscapeChecker : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        if (declaration is FirAnonymousFunction) return // The enclosing body's walk classifies capture boundaries.
        val graph = declaration.uniquenessAnalysisGraph ?: return
        val flows = graph.resolveUniquenessStateFlows()
        val states = graph.uniquenessAnalysisTargetNodes.associate { it.fir to flows.readInputUniquenessStateOf(it) }
        BorrowedViewUses(declaration, borrowedViews(), ReadOnlyContext.of(graph, context), states)
            .walk(declaration, false, EmptyUniquenessState.initializeParametersOf(declaration))
    }
}

private class BorrowedViewUses(
    val declaration: FirFunction,
    val views: BorrowedViews,
    val readOnly: ReadOnlyContext,
    val states: Map<FirElement, UniquenessState?>,
) {
    private fun bearing(e: FirExpression): Boolean = when (e) {
        is FirSmartCastExpression -> bearing(e.originalExpression)
        is FirCheckedSafeCallSubject -> bearing(e.originalReceiverRef.value)
        is FirCheckNotNullCall -> true
        is FirTypeOperatorCall -> e.operation in setOf(FirOperation.AS, FirOperation.SAFE_AS)
        is FirSafeCallExpression -> bearing(e.receiver)
        is FirQualifiedAccessExpression -> e.calleeReference.symbol?.let { views.source(it) } != null ||
                e.pathReceiver?.let(::bearing) == true
        else -> false
    }

    private fun untrackedLocal(e: FirExpression): Boolean = when (e) {
        is FirSmartCastExpression -> untrackedLocal(e.originalExpression)
        is FirCheckNotNullCall -> untrackedLocal(e.arguments.single())
        is FirTypeOperatorCall -> untrackedLocal(e.argument)
        is FirQualifiedAccessExpression -> (e.calleeReference.symbol as? org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol)
            ?.let { views.isReadOnlyLocal(it) && views.source(it) == null } == true || e.pathReceiver?.let(::untrackedLocal) == true
        else -> false
    }

    context(context: CheckerContext)
    private fun isCandidate(element: FirElement): Boolean {
        if (element !is FirExpression) return false
        if (element !in readOnly || element.resolvedType.isValueType()) return false
        return bearing(element)
    }

    private fun isEscape(element: FirExpression, allowed: Boolean, captured: Boolean): Boolean {
        if (captured || !allowed) return true
        val refines = element is FirCheckNotNullCall || element is FirTypeOperatorCall
        return refines && untrackedLocal(element)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun rejectEscape(element: FirExpression, owned: Path) {
        val root = owned.first()
        val path = ConversionOwnershipPath(ConversionOwnershipPath.Root(
            if (root is org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol<*>) ConversionOwnershipPath.Kind.DISPATCH_RECEIVER
            else ConversionOwnershipPath.Kind.LOCAL, root,
            (root as? org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol<*>)?.name?.asString()),
            owned.drop(1).map { val property = it as org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
                ConversionOwnershipPath.Member(property, property.name.asString()) })
        reporter.reportFailure(unsupportedOwnershipFailure(element.source,
            FoldOwnershipCondition(FoldOwnershipRule.BORROWED_VIEW_ESCAPE, path)))
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    fun walk(element: FirElement, allowed: Boolean, inherited: UniquenessState, captured: Boolean = false) {
        val state = states[element] ?: inherited
        if (isCandidate(element)) {
            element as FirExpression
            val owned = element.resolveAccessState().enumeratePaths().firstOrNull { path ->
                var node: UniquenessState? = state
                var uniqueness = state.data
                for (symbol in path) {
                    node = node?.children[symbol]
                    uniqueness = uniqueness.join(node?.data ?: symbol.resolveDeclaredUniqueness())
                }
                uniqueness == Uniqueness.Unique
            }
            if (owned != null && isEscape(element, allowed, captured)) {
                rejectEscape(element, owned)
                return
            }
        }
        walkChildren(element, allowed, state, captured)
    }

    context(context: CheckerContext)
    private fun FirFunctionCall.borrowsViews(): Boolean {
        // UniquePredPlacementChecker validates predicate operand placement.
        if (isUniquePredConstruction()) return true
        return isSpecificationCall() ||
                isPureCall(context.session) && (toResolvedCallableSymbol() as? FirFunctionSymbol<*>)?.isInline != true
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun walkChildren(element: FirElement, allowed: Boolean, state: UniquenessState, captured: Boolean) {
        fun child(e: FirElement?, permits: Boolean = false, capture: Boolean = captured) {
            if (e != null) walk(e, permits, state, capture)
        }
        when (element) {
            is FirProperty -> child(element.initializer, views.source(element.symbol) != null)
            is FirFunctionCall -> {
                val borrows = element.borrowsViews()
                child(element.dispatchReceiver, borrows)
                if (element.extensionReceiver !== element.dispatchReceiver) child(element.extensionReceiver, borrows)
                element.contextArguments.forEach { child(it, borrows) }
                element.arguments.forEach {
                    val argument = it.unwrapArgument()
                    if (element.isSpecificationCall() && argument is FirAnonymousFunctionExpression) child(argument.anonymousFunction.body)
                    else child(argument, borrows)
                }
            }
            is FirQualifiedAccessExpression -> {
                child(element.dispatchReceiver, true)
                if (element.extensionReceiver !== element.dispatchReceiver) child(element.extensionReceiver, true)
                element.contextArguments.forEach { child(it) }
            }
            is FirWrappedArgumentExpression -> child(element.expression, allowed)
            is FirTypeOperatorCall -> child(element.argument, true)
            is FirCheckNotNullCall -> element.arguments.forEach { child(it, true) }
            is FirSmartCastExpression -> child(element.originalExpression, allowed)
            is FirEqualityOperatorCall -> element.arguments.forEach { child(it, true) }
            is FirSafeCallExpression -> {
                val selector = element.selector
                child(element.receiver, selector !is FirFunctionCall || selector.borrowsViews())
                child(selector, allowed)
            }
            is FirElvisExpression -> { child(element.lhs, allowed); child(element.rhs, allowed) }
            is FirBlock -> element.statements.forEachIndexed { index, statement ->
                child(statement, statement is FirElvisExpression && index != element.statements.lastIndex)
            }
            is FirAnonymousFunction -> {
                val inline = element.inlineStatus == InlineStatus.Inline
                child(element.body, capture = captured || !inline)
            }
            is FirFunction -> child(element.body, capture = captured || element !== declaration)
            else -> element.acceptChildren(object : FirVisitorVoid() {
                override fun visitElement(element: FirElement) = child(element)
            })
        }
    }
}
