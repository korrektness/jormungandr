/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.analysis.cfa.util.ControlFlowInfo
import org.jetbrains.kotlin.fir.analysis.cfa.util.PathAwareControlFlowGraphVisitor
import org.jetbrains.kotlin.fir.analysis.cfa.util.PathAwareControlFlowInfo
import org.jetbrains.kotlin.fir.analysis.cfa.util.merge
import org.jetbrains.kotlin.fir.analysis.cfa.util.transformValues
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.allReceiverExpressions
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNodeWithSubgraphs
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ControlFlowGraph
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ExitDefaultArgumentsNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.FunctionCallEnterNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.FunctionCallExitNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.FunctionEnterNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.JumpNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.QualifiedAccessNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ThrowExceptionNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.VariableAssignmentNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.VariableDeclarationNode
import org.jetbrains.kotlin.formver.locality.plugin.Locality
import org.jetbrains.kotlin.formver.locality.plugin.resolveLocality
import org.jetbrains.kotlin.formver.readonly.plugin.ReadOnlyContext
import org.jetbrains.kotlin.formver.readonly.plugin.isPureCall
import org.jetbrains.kotlin.formver.type.plugin.CallArgumentTypeFactsMapper

typealias UniquenessStateFlow = ControlFlowInfo<Unit, UniquenessState>

typealias PathAwareUniquenessStateFlow = PathAwareControlFlowInfo<Unit, UniquenessState>

/**
 * Returns the join of the [UniquenessStateFlow]s over every path edge kind.
 */
fun PathAwareUniquenessStateFlow?.joinOverEdgeKinds(): UniquenessState =
    this?.values
        ?.map { it[Unit] ?: EmptyUniquenessState }
        ?.reduceOrNull(UniquenessState::join)
        ?: EmptyUniquenessState

/**
 * Whether [this] graph is analyzed as part of the flow of the graph that encloses it: a default argument, or a lambda
 * called in place. The control-flow graph links such a graph into the enclosing flow, with a back edge when it may
 * run more than once.
 */
val ControlFlowGraph.extendsLocalFlow: Boolean
    get() = kind == ControlFlowGraph.Kind.DefaultArgument || kind == ControlFlowGraph.Kind.AnonymousFunctionCalledInPlace

/**
 * Returns the nodes of [this] graph that are analyzed by [GraphUniquenessStatesAnalyzer], including those of the
 * subgraphs that extend its flow.
 */
val ControlFlowGraph.uniquenessAnalysisTargetNodes: Sequence<CFGNode<*>>
    get() = nodes.asSequence().flatMap { node ->
        val subGraphs = (node as? CFGNodeWithSubgraphs<*>)?.subGraphs.orEmpty()
        sequenceOf(node) + subGraphs.asSequence()
            .filter { it.extendsLocalFlow }
            .flatMap { it.uniquenessAnalysisTargetNodes }
    }

/**
 * Initializes the roots for the receiver and value parameters of [function] to their declared uniqueness.
 */
context(context: CheckerContext)
fun UniquenessState.initializeParametersOf(function: FirFunction): UniquenessState {
    var state = this
    function.receiverParameter?.let { state = state.putChild(it.symbol, UniquenessState(it.symbol.resolveUniqueness())) }
    for (valueParameter in function.valueParameters) {
        state = state.putChild(valueParameter.symbol, UniquenessState(valueParameter.symbol.resolveUniqueness()))
    }
    return state
}

/**
 * Data-flow analyzer that tracks the uniqueness state of paths through a CFG.
 *
 * Assignments and declarations initialize their target paths and move their source paths. Function calls move all
 * passed paths on entry, and restore paths whose corresponding parameters are local on exit. A property access that
 * calls an accessor moves its receivers the same way.
 *
 * Default arguments and lambdas called in place are analyzed as part of the enclosing flow; a lambda called in place
 * starts with its own parameters at their declared uniqueness.
 *
 * Calls to `@Pure` functions, and declarations and calls in [readOnlyContext], move nothing.
 */
class GraphUniquenessStatesAnalyzer(
    private val initialState: UniquenessState,
    private val context: CheckerContext,
    private val callArgumentLocalitiesMapper: CallArgumentTypeFactsMapper<Locality>,
    private val readOnlyContext: ReadOnlyContext,
) : PathAwareControlFlowGraphVisitor<Unit, UniquenessState>() {
    override fun mergeInfo(
        a: UniquenessStateFlow,
        b: UniquenessStateFlow,
        node: CFGNode<*>
    ): UniquenessStateFlow =
        a.merge(b) { leftState, rightState ->
            leftState.join(rightState)
        }

    private fun UniquenessStateFlow.getOrInitialize(): UniquenessState =
        this[Unit] ?: initialState

    private val FirFunctionCall.movesArguments: Boolean
        get() = this !in readOnlyContext && !isPureCall(context.session)

    override fun visitSubGraph(node: CFGNodeWithSubgraphs<*>, graph: ControlFlowGraph): Boolean {
        return graph.extendsLocalFlow
    }

    override fun visitFunctionEnterNode(
        node: FunctionEnterNode,
        data: PathAwareUniquenessStateFlow
    ): PathAwareUniquenessStateFlow {
        if (node.owner.kind != ControlFlowGraph.Kind.AnonymousFunctionCalledInPlace) return visitNode(node, data)

        return context(context) {
            data.transformValues { data -> data.put(Unit, data.getOrInitialize().initializeParametersOf(node.fir)) }
        }
    }

    override fun visitNode(
        node: CFGNode<*>,
        data: PathAwareUniquenessStateFlow
    ): PathAwareUniquenessStateFlow {
        return data.transformValues { data -> data.put(Unit, data.getOrInitialize()) }
    }

    override fun visitVariableDeclarationNode(
        node: VariableDeclarationNode,
        data: PathAwareUniquenessStateFlow
    ): PathAwareUniquenessStateFlow {
        val declaration = node.fir
        val initializer = declaration.initializer
        val leftSymbol = declaration.symbol
        val leftAccessState = EmptyAccessState.putChild(
            leftSymbol,
            AccessState(Access.Terminal)
        )

        with(context) {
            val rightAccessState = initializer?.resolveAccessState() ?: EmptyAccessState
            val isWhenSubject = leftSymbol.source?.kind == KtFakeSourceElementKind.WhenGeneratedSubject
            val movesInitializer = !isWhenSubject && declaration !in readOnlyContext

            return data.transformValues { data ->
                val uniquenessState = data.getOrInitialize()
                var newUniquenessState = uniquenessState

                // The source moves before the target is written, so that a source below the target (`p.next`) is
                // resolved against the old target.
                if (movesInitializer) {
                    newUniquenessState = rightAccessState.move(newUniquenessState)
                }

                if (initializer != null) {
                    val rightUniquenessState = rightAccessState.projectTerminalUniquenessState(uniquenessState)
                    newUniquenessState = newUniquenessState.insert(listOf(leftSymbol), rightUniquenessState)
                }

                newUniquenessState = leftAccessState.initialize(newUniquenessState)

                data.put(Unit, newUniquenessState)
            }
        }
    }

    override fun visitVariableAssignmentNode(
        node: VariableAssignmentNode,
        data: PathAwareUniquenessStateFlow
    ): PathAwareUniquenessStateFlow {
        val assignment = node.fir
        val leftValue = assignment.lValue
        val rightValue = assignment.rValue

        with(context) {
            val leftAccessState = leftValue.resolveAccessState()

            return data.transformValues { data ->
                val uniquenessState = data.getOrInitialize()
                val leftAccessPaths = leftAccessState.enumeratePaths()
                val rightAccessState = rightValue.resolveAccessState()

                // The source moves before the target is written; see `visitVariableDeclarationNode`.
                var newUniquenessState = rightAccessState.move(uniquenessState)
                if (leftValue is FirQualifiedAccessExpression) {
                    newUniquenessState = newUniquenessState.passReceiversToAccessor(leftValue)
                }

                val rightUniquenessState = rightAccessState.projectTerminalUniquenessState(uniquenessState)

                if (leftAccessPaths.count() == 1) {
                    newUniquenessState = newUniquenessState.insert(leftAccessPaths.first(), rightUniquenessState)
                    newUniquenessState = leftAccessState.initialize(newUniquenessState)
                } else {
                    // Only one of the paths is written, so each keeps its old state joined with the written one.
                    for (leftPath in leftAccessPaths) {
                        val writtenUniquenessState =
                            rightUniquenessState.copy(data = leftPath.last().resolveDeclaredUniqueness())
                        val oldUniquenessState = newUniquenessState.find(leftPath) ?: EmptyUniquenessState
                        newUniquenessState =
                            newUniquenessState.insert(leftPath, oldUniquenessState.join(writtenUniquenessState))
                    }
                }

                data.put(Unit, newUniquenessState)
            }
        }
    }

    override fun visitQualifiedAccessNode(
        node: QualifiedAccessNode,
        data: PathAwareUniquenessStateFlow
    ): PathAwareUniquenessStateFlow {
        return context(context) {
            data.transformValues { data -> data.put(Unit, data.getOrInitialize().passReceiversToAccessor(node.fir)) }
        }
    }

    /**
     * Moves the receivers that [access] passes to an accessor call, as at a function call. An access that calls no
     * accessor leaves [this] unchanged.
     */
    context(context: CheckerContext)
    private fun UniquenessState.passReceiversToAccessor(access: FirQualifiedAccessExpression): UniquenessState {
        if (access in readOnlyContext) return this
        val property = access.accessorCallProperty(context.session) ?: return this
        var newUniquenessState = this
        for (receiver in listOfNotNull(access.dispatchReceiver, access.extensionReceiver)) {
            newUniquenessState = receiver.resolveAccessState().move(newUniquenessState)
        }
        val extensionReceiver = access.extensionReceiver
        if (extensionReceiver != null && property.receiverParameterSymbol?.resolveLocality() == Locality.Local) {
            newUniquenessState = extensionReceiver.resolveAccessState().initialize(newUniquenessState)
        }
        return newUniquenessState
    }

    override fun visitFunctionCallEnterNode(
        node: FunctionCallEnterNode,
        data: PathAwareUniquenessStateFlow
    ): PathAwareUniquenessStateFlow {
        val call = node.fir
        if (!call.movesArguments) return visitNode(node, data)

        with(context) {
            return data.transformValues { data ->
                var newUniquenessState = data.getOrInitialize()

                // NOTE: `allReceiverExpressions` also includes context arguments.
                for (receiver in call.allReceiverExpressions) {
                    newUniquenessState = receiver.resolveAccessState().move(newUniquenessState)
                }

                for (argument in call.arguments) {
                    newUniquenessState = argument.resolveAccessState().move(newUniquenessState)
                }

                data.put(Unit, newUniquenessState)
            }
        }
    }

    override fun visitFunctionCallExitNode(
        node: FunctionCallExitNode,
        data: PathAwareUniquenessStateFlow
    ): PathAwareUniquenessStateFlow {
        val call = node.fir
        if (!call.movesArguments) return visitNode(node, data)

        with(context) {
            return data.transformValues { data ->
                var newUniquenessState = data.getOrInitialize()
                val explicitReceiver = call.explicitReceiver
                val receiverParameterSymbol = call.toResolvedCallableSymbol()?.receiverParameterSymbol

                if (receiverParameterSymbol != null && explicitReceiver != null && receiverParameterSymbol.resolveLocality() == Locality.Local) {
                    newUniquenessState = explicitReceiver.resolveAccessState().initialize(newUniquenessState)
                }

                for ((argument, requiredLocality) in callArgumentLocalitiesMapper.mapArgumentTypeFactsOf(call)) {
                    if (requiredLocality == Locality.Global) continue

                    newUniquenessState = argument.resolveAccessState().initialize(newUniquenessState)
                }

                data.put(Unit, newUniquenessState)
            }
        }
    }

    override fun visitExitDefaultArgumentsNode(
        node: ExitDefaultArgumentsNode,
        data: PathAwareControlFlowInfo<Unit, UniquenessState>
    ): PathAwareControlFlowInfo<Unit, UniquenessState> {
        val valueParameter = node.fir

        return with(context) {
            data.transformValues { data ->
                var newUniquenessState = data.getOrInitialize()
                val defaultValue = valueParameter.defaultValue ?: return@transformValues data
                val valueParameterSymbol = valueParameter.symbol
                val valueParameterPath = listOf(valueParameterSymbol)
                val defaultValueAccessState = defaultValue.resolveAccessState()
                val defaultValueUniquenessState = defaultValueAccessState.projectTerminalUniquenessState(newUniquenessState)
                newUniquenessState = newUniquenessState.insert(valueParameterPath, defaultValueUniquenessState)
                newUniquenessState = defaultValueAccessState.move(newUniquenessState)
                data.put(Unit, newUniquenessState)
            }
        }
    }

    override fun visitJumpNode(
        node: JumpNode,
        data: PathAwareControlFlowInfo<Unit, UniquenessState>
    ): PathAwareControlFlowInfo<Unit, UniquenessState> {
        return when (val jumpExpression = node.fir) {
            is FirReturnExpression -> {
                with (context) {
                    data.transformValues { data ->
                        var newUniquenessState = data.getOrInitialize()
                        val resultAccessState = jumpExpression.result.resolveAccessState()
                        newUniquenessState = resultAccessState.move(newUniquenessState)

                        data.put(Unit, newUniquenessState)
                    }
                }
            }
            else -> { data }
        }
    }

    override fun visitThrowExceptionNode(
        node: ThrowExceptionNode,
        data: PathAwareControlFlowInfo<Unit, UniquenessState>
    ): PathAwareControlFlowInfo<Unit, UniquenessState> {
        val throwExpression = node.fir

        return with (context) {
            data.transformValues { data ->
                var newUniquenessState = data.getOrInitialize()
                val exceptionAccessState = throwExpression.exception.resolveAccessState()
                newUniquenessState = exceptionAccessState.move(newUniquenessState)

                data.put(Unit, newUniquenessState)
            }
        }
    }
}
