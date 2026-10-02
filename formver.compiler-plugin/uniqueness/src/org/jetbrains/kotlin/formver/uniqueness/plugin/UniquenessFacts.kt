/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.diagnostics.DiagnosticContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContextForProvider
import org.jetbrains.kotlin.fir.analysis.checkers.context.MutableCheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckersDiagnosticComponent
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckersDiagnosticComponent
import org.jetbrains.kotlin.fir.analysis.checkers.type.TypeCheckersDiagnosticComponent
import org.jetbrains.kotlin.fir.analysis.collectors.AbstractDiagnosticCollectorVisitor
import org.jetbrains.kotlin.fir.analysis.collectors.components.AbstractDiagnosticCollectorComponent
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirWhileLoop
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNode
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.locality.plugin.localityCheckerFactories

/**
 * Uniqueness facts about one function that has no uniqueness or locality diagnostic.
 *
 * States are read from the uniqueness-state flow analysis of the function's control-flow graph. An element with no
 * CFG node of its own gets the state before the nearest enclosing element that has one.
 */
class FunctionUniquenessAnalysis internal constructor(
    private val context: CheckerContext,
    private val statesBefore: Map<FirElement, UniquenessState>,
    private val statesAfter: Map<FirElement, UniquenessState>,
) {
    /**
     * The state on entry to [element]: the join of the states its first CFG node's predecessors produce.
     */
    fun stateBefore(element: FirElement): UniquenessState =
        statesBefore[element] ?: error("No uniqueness state recorded for ${element.render()}.")

    /**
     * The state on exit from [element]: the state its last CFG node produces.
     */
    fun stateAfter(element: FirElement): UniquenessState =
        statesAfter[element] ?: error("No uniqueness state recorded for ${element.render()}.")

    fun declaredUniqueness(symbol: FirBasedSymbol<*>): Uniqueness =
        context(context) { symbol.resolveDeclaredUniqueness() }

    /**
     * Whether some root is `Unique` somewhere in the function.
     */
    val ownsAnyPath: Boolean by lazy {
        (statesBefore.values + statesAfter.values).any { state -> state.children.values.any { it.data == Uniqueness.Unique } }
    }

    /**
     * Whether every path is `Shared` everywhere in the function: no path is ever `Unique`, `Unknown` or `Moved`.
     */
    val sharesEveryPath: Boolean by lazy {
        (statesBefore.values + statesAfter.values).all { state -> state.children.values.all { it.isSharedThroughout() } }
    }

    private fun UniquenessState.isSharedThroughout(): Boolean =
        data == Uniqueness.Shared && children.values.all { it.isSharedThroughout() }

    fun hasState(element: FirElement): Boolean = element in statesBefore

    /**
     * The path [expression] denotes, or `null` when it denotes none or more than one.
     */
    fun pathOf(expression: FirExpression): Path? =
        context(context) { expression.resolveAccessState().enumeratePaths().singleOrNull() }

    /**
     * Whether [path] is `Unique` on entry to [element].
     */
    fun ownsBefore(element: FirElement, path: Path): Boolean = owns(stateBefore(element), path)

    /**
     * The state at the head of [loop]: before its condition, where the entry and back edges join. The state before
     * [loop] itself is the entry state alone.
     */
    fun stateAtLoopHead(loop: FirWhileLoop): UniquenessState = stateBefore(loop.condition)

    /**
     * Whether [path] is `Unique` in [state].
     */
    fun owns(state: UniquenessState, path: Path): Boolean = state.uniquenessOf(path) == Uniqueness.Unique

    /**
     * The paths below [symbol] that are `Moved` in [state], each given by the symbols after [symbol]. None extends
     * another.
     */
    fun movedBelow(state: UniquenessState, symbol: FirBasedSymbol<*>): List<Path> {
        val moved = state.children[symbol]?.enumerateInconsistentPaths()?.toList() ?: return emptyList()
        return moved.filter { path -> (1 until path.size).none { path.subList(0, it) in moved } }
    }

    /**
     * Whether [path] is `Unique` on exit from [element].
     */
    fun ownsAfter(element: FirElement, path: Path): Boolean = owns(stateAfter(element), path)

    /**
     * The uniqueness of [path]: the join along the path, where a component with no entry has its declared uniqueness.
     */
    private fun UniquenessState.uniquenessOf(path: Path): Uniqueness {
        var node: UniquenessState? = this
        var uniqueness = data
        for (symbol in path) {
            node = node?.children[symbol]
            uniqueness = uniqueness.join(node?.data ?: declaredUniqueness(symbol))
        }
        return uniqueness
    }

    private fun FirElement.render(): String = "${this::class.simpleName} at ${source?.startOffset}"
}

/**
 * Session component through which the converter reads the uniqueness checker's results.
 */
class UniquenessFacts(session: FirSession) : FirExtensionSessionComponent(session) {
    companion object {
        fun getFactory(): Factory {
            return Factory { session -> UniquenessFacts(session) }
        }
    }

    private val checkerExtensions: List<FirAdditionalCheckersExtension> =
        (uniquenessCheckerFactories + localityCheckerFactories).map { it.create(session) }

    private val cache = session.firCachesFactory.createCache { function: FirFunction, context: CheckerContext ->
        context(context) {
            if (function.hasUniquenessDiagnostics()) null else function.buildAnalysis()
        }
    }

    /**
     * Returns the analysis of [function], or `null` when the uniqueness or locality checkers report an error in it.
     */
    context(context: CheckerContext)
    fun analysis(function: FirFunction): FunctionUniquenessAnalysis? =
        cache.getValue(function, context)

    /**
     * Runs the uniqueness and locality checkers over [this] function, nested declarations included, and tells
     * whether any of them reports an error. The checkers registered with the session report the same errors to the
     * user; this run only counts them.
     *
     * The run walks down from the containing file so that the checkers see the same containing declarations and
     * elements as in the session's own run.
     */
    @OptIn(SymbolInternals::class)
    context(context: CheckerContext)
    private fun FirFunction.hasUniquenessDiagnostics(): Boolean {
        val reporter = ErrorCountingReporter()
        val components = checkerExtensions.flatMap { extension ->
            listOf(
                DeclarationCheckersDiagnosticComponent(session, reporter, extension.declarationCheckers),
                ExpressionCheckersDiagnosticComponent(session, reporter, extension.expressionCheckers),
                TypeCheckersDiagnosticComponent(session, reporter, extension.typeCheckers),
            )
        }
        val providerContext = context as? CheckerContextForProvider
            ?: error("Expected a ${CheckerContextForProvider::class.simpleName}, got ${context::class.simpleName}.")
        // A local function is only converted from within its own file, which is the file being checked.
        val file = moduleData.session.firProvider.getFirCallableContainerFile(symbol)
            ?: context.containingFileSymbol?.fir
            ?: error("No containing file for ${symbol.callableId}.")
        val visitor = TargetCheckerRunningVisitor(
            MutableCheckerContext(providerContext.sessionHolder, providerContext.returnTypeCalculator),
            components,
            this,
        )
        file.accept(visitor, null)
        check(visitor.targetVisited) { "${symbol.callableId} was not found in ${file.name}." }
        return reporter.errorCount > 0
    }

    context(context: CheckerContext)
    private fun FirFunction.buildAnalysis(): FunctionUniquenessAnalysis {
        val graph = uniquenessAnalysisGraph
            ?: error("Function ${symbol.callableId} has no control-flow graph of its own.")
        val flows = graph.resolveUniquenessStateFlows()

        val firstNodes = mutableMapOf<FirElement, CFGNode<*>>()
        val lastNodes = mutableMapOf<FirElement, CFGNode<*>>()
        for (node in graph.uniquenessAnalysisTargetNodes) {
            firstNodes.putIfAbsent(node.fir, node)
            lastNodes[node.fir] = node
        }

        val statesBefore = mutableMapOf<FirElement, UniquenessState>()
        val statesAfter = mutableMapOf<FirElement, UniquenessState>()
        for ((element, node) in firstNodes) {
            // Only an enter node has no predecessors, and it leaves the state unchanged.
            statesBefore[element] = flows.readInputUniquenessStateOf(node) ?: flows.readOutputUniquenessStateOf(node)
        }
        for ((element, node) in lastNodes) {
            statesAfter[element] = flows.readOutputUniquenessStateOf(node)
        }

        acceptChildren(object : FirVisitorVoid() {
            private var enclosingState: UniquenessState? = statesBefore[this@buildAnalysis]

            override fun visitElement(element: FirElement) {
                val ownState = statesBefore[element]
                if (ownState == null) {
                    val inherited = enclosingState ?: error("No enclosing uniqueness state for ${element::class.simpleName}.")
                    statesBefore[element] = inherited
                    statesAfter[element] = inherited
                }
                val outer = enclosingState
                enclosingState = statesBefore[element]
                element.acceptChildren(this)
                enclosingState = outer
            }
        })

        return FunctionUniquenessAnalysis(context, statesBefore, statesAfter)
    }
}

private class ErrorCountingReporter : DiagnosticReporter() {
    var errorCount: Int = 0
        private set

    override fun report(diagnostic: KtDiagnostic?, context: DiagnosticContext) {
        if (diagnostic?.severity == Severity.ERROR) errorCount++
    }
}

/**
 * Runs [components] on [target] and everything nested in it, visiting only the declarations that enclose [target]
 * on the way down.
 */
private class TargetCheckerRunningVisitor(
    context: CheckerContextForProvider,
    private val components: List<AbstractDiagnosticCollectorComponent>,
    private val target: FirFunction,
) : AbstractDiagnosticCollectorVisitor(context) {
    private var insideTarget = false

    var targetVisited = false
        private set

    override fun shouldVisitDeclaration(declaration: FirDeclaration): Boolean =
        insideTarget || declaration is FirFile || declaration === target || declaration.encloses(target)

    override fun checkElement(element: FirElement) {
        if (element === target) {
            insideTarget = true
            targetVisited = true
        }
        if (!insideTarget) return
        for (component in components) {
            element.accept(component, context)
        }
    }

    override fun onDeclarationExit(declaration: FirDeclaration) {
        if (declaration === target) insideTarget = false
    }

    private fun FirDeclaration.encloses(other: FirDeclaration): Boolean {
        val outer = source ?: return false
        val inner = other.source ?: return false
        return outer.startOffset <= inner.startOffset && inner.endOffset <= outer.endOffset
    }
}

val FirSession.uniquenessFacts: UniquenessFacts by FirSession.sessionComponentAccessor()
