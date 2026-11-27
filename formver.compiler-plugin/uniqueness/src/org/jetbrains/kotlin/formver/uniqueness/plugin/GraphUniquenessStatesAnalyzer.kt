/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.common.externalSpecificationSymbol
import org.jetbrains.kotlin.formver.locality.plugin.resolveLocality
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.formver.intrinsics.plugin.isPrimitiveArrayElementAccess
import org.jetbrains.kotlin.formver.intrinsics.plugin.isGenericArrayElementAccess
import org.jetbrains.kotlin.formver.intrinsics.plugin.genericArraySetId
import org.jetbrains.kotlin.formver.intrinsics.plugin.GenericArrayBulk
import org.jetbrains.kotlin.formver.intrinsics.plugin.genericArrayBulk
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.formver.intrinsics.plugin.isPrimitiveArrayLoop
import org.jetbrains.kotlin.formver.intrinsics.plugin.isCollectionLoop

import org.jetbrains.kotlin.formver.locality.plugin.receiverTemporaryInitializer
import org.jetbrains.kotlin.formver.intrinsics.plugin.stringBuilderIntrinsic
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.analysis.cfa.util.ControlFlowInfo
import org.jetbrains.kotlin.fir.analysis.cfa.util.PathAwareControlFlowGraphVisitor
import org.jetbrains.kotlin.fir.analysis.cfa.util.PathAwareControlFlowInfo
import org.jetbrains.kotlin.fir.analysis.cfa.util.merge
import org.jetbrains.kotlin.fir.analysis.cfa.util.transformValues
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import kotlinx.collections.immutable.mutate
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.InlineStatus
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.allReceiverExpressions
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.AnonymousFunctionCaptureNode
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
import org.jetbrains.kotlin.fir.resolve.dfa.controlFlowGraph
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.formver.locality.plugin.Locality
import org.jetbrains.kotlin.formver.locality.plugin.QualifiedAccessArgumentLocalitiesMapper
import org.jetbrains.kotlin.formver.locality.plugin.resolveCapturedSymbols
import org.jetbrains.kotlin.formver.readonly.plugin.ReadOnlyContext
import org.jetbrains.kotlin.formver.readonly.plugin.isPureCall
import org.jetbrains.kotlin.formver.readonly.plugin.isPure
import org.jetbrains.kotlin.formver.type.plugin.CallArgumentTypeFactsMapper

typealias UniquenessStateFlow = ControlFlowInfo<Unit, UniquenessFlowFacts>

typealias PathAwareUniquenessStateFlow = PathAwareControlFlowInfo<Unit, UniquenessFlowFacts>

fun PathAwareUniquenessStateFlow?.joinFactsOverEdgeKinds(): UniquenessFlowFacts? =
    this?.values?.mapNotNull { it[Unit] }?.reduceOrNull(UniquenessFlowFacts::join)

fun PathAwareUniquenessStateFlow?.joinOverEdgeKinds(): UniquenessState =
    joinFactsOverEdgeKinds()?.paths ?: EmptyUniquenessState

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
 * Initializes the roots for the receivers, context parameters and value parameters of [function] to their declared
 * uniqueness. The root of a dispatch receiver is the class, and only a receiver that is not shared is a path.
 */
context(context: CheckerContext)
fun UniquenessState.initializeParametersOf(function: FirFunction): UniquenessState {
    var state = this
    val receiverClass = function.dispatchReceiverType?.toClassSymbol(context.session)
    val receiverUniqueness = function.symbol.resolveDispatchReceiverUniqueness()
    if (receiverClass != null && function !is FirConstructor && receiverUniqueness != Uniqueness.Shared) {
        state = state.putChild(receiverClass, UniquenessState(receiverUniqueness))
    }
    function.receiverParameter?.let { state = state.putChild(it.symbol, UniquenessState(it.symbol.resolveUniqueness())) }
    for (valueParameter in function.contextParameters + function.valueParameters) {
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
 * Calls to `@Pure` functions, element accesses of a modelled primitive array, `StringBuilder` intrinsics, and declarations and calls
 * in [readOnlyContext], move nothing. A receiver temporary of a compound assignment or an increment (see
 * [receiverTemporaryInitializer]) is not tracked: its accesses resolve to its initializer.
 *
 * A join truncates its result to [maxPathLength] components (see [truncate]). An assignment places the source's
 * subtree under the target, so a loop such as `cur.next = acc; acc = cur` would otherwise deepen the state at its head
 * on every pass and never reach a fixed point.
 */
class GraphUniquenessStatesAnalyzer(
    private val initialState: UniquenessState,
    private val maxPathLength: Int,
    private val context: CheckerContext,
    private val callArgumentLocalitiesMapper: CallArgumentTypeFactsMapper<Locality>,
    private val readOnlyContext: ReadOnlyContext,
    private val specifiedLambdas: Set<FirAnonymousFunction>,
) : PathAwareControlFlowGraphVisitor<Unit, UniquenessFlowFacts>() {
    override fun mergeInfo(
        a: UniquenessStateFlow,
        b: UniquenessStateFlow,
        node: CFGNode<*>
    ): UniquenessStateFlow =
        a.merge(b) { leftState, rightState ->
            val joined = leftState.join(rightState)
            joined.copy(paths = joined.paths.truncate(maxPathLength))
        }

    private fun UniquenessStateFlow.facts(): UniquenessFlowFacts = this[Unit] ?: UniquenessFlowFacts(initialState)

    private fun UniquenessStateFlow.getOrInitialize(): UniquenessState = facts().paths

    private fun UniquenessStateFlow.withPaths(paths: UniquenessState): UniquenessStateFlow =
        put(Unit, facts().copy(paths = paths))

    private val FirFunctionCall.movesArguments: Boolean
        get() = this !in readOnlyContext && !isPureCall(context.session) && !isPrimitiveArrayElementAccess() && !isGenericArrayElementAccess() &&
                !isPrimitiveArrayLoop() && !isCollectionLoop() && genericArrayBulk() == null && stringBuilderIntrinsic(context.session) == null

    override fun visitSubGraph(node: CFGNodeWithSubgraphs<*>, graph: ControlFlowGraph): Boolean {
        return graph.extendsLocalFlow
    }

    override fun visitFunctionEnterNode(
        node: FunctionEnterNode,
        data: PathAwareUniquenessStateFlow
    ): PathAwareUniquenessStateFlow {
        if (node.owner.kind != ControlFlowGraph.Kind.AnonymousFunctionCalledInPlace) return visitNode(node, data)

        return context(context) {
            data.transformValues { data -> data.withPaths(data.getOrInitialize().initializeParametersOf(node.fir)) }
        }
    }

    override fun visitNode(
        node: CFGNode<*>,
        data: PathAwareUniquenessStateFlow
    ): PathAwareUniquenessStateFlow {
        return data.transformValues { data -> data.put(Unit, data.facts()) }
    }

    override fun visitAnonymousFunctionCaptureNode(
        node: AnonymousFunctionCaptureNode,
        data: PathAwareUniquenessStateFlow,
    ): PathAwareUniquenessStateFlow {
        val lambda = node.fir.anonymousFunction
        if (node.fir in readOnlyContext) return visitNode(node, data)
        if (lambda !in specifiedLambdas) {
            if (lambda.inlineStatus == InlineStatus.Inline) return visitNode(node, data)
            return shareCaptures(node, lambda, data)
        }
        val graph = lambda.controlFlowGraphReference?.controlFlowGraph ?: error("Specified lambda has no CFG")
        return context(context) {
            val captures = graph.resolveCapturedSymbols() + graph.capturedReceivers()
            data.transformValues { flow ->
                var state = flow.getOrInitialize()
                for (symbol in captures) {
                    val root = state.children[symbol] ?: UniquenessState(symbol.resolveDeclaredUniqueness())
                    val mutable = (symbol as? FirPropertySymbol)?.isVar == true
                    val copied = !mutable && (symbol as? FirVariableSymbol<*>)?.resolvedReturnType?.isValueType() == true
                    if (!copied && (mutable || root.data <= Uniqueness.Unknown)) {
                        state = state.putChild(symbol, root.copy(data = Uniqueness.Moved))
                    }
                }
                flow.withPaths(state)
            }
        }
    }

    /**
     * Makes each root that [lambda], when not called in place, captures and the analysis finds owned shared from here
     * on. [FunctionCaptureUniquenessChecker] rejects capturing a root annotated as owned.
     */
    private fun shareCaptures(
        node: AnonymousFunctionCaptureNode,
        lambda: FirAnonymousFunction,
        data: PathAwareUniquenessStateFlow,
    ): PathAwareUniquenessStateFlow {
        val graph = lambda.controlFlowGraphReference?.controlFlowGraph
            ?: throw SnaktInternalException(lambda.source, "Lambda has no control-flow graph")
        if (graph.extendsLocalFlow) return visitNode(node, data)
        return context(context) {
            val captures = graph.resolveCapturedSymbols() + graph.capturedReceivers()
            data.transformValues { flow ->
                var state = flow.getOrInitialize()
                for (symbol in captures) {
                    val root = state.children[symbol] ?: UniquenessState(symbol.resolveDeclaredUniqueness())
                    if (root.data <= Uniqueness.Unknown) state = state.putChild(symbol, root.sharedKeepingMoves())
                }
                flow.withPaths(state)
            }
        }
    }

    /** This state with every path that has not moved `Shared`. */
    private fun UniquenessState.sharedKeepingMoves(): UniquenessState =
        if (data == Uniqueness.Moved) this
        else UniquenessState(Uniqueness.Shared, children.mutate { shared ->
            for ((key, child) in children) shared[key] = child.sharedKeepingMoves()
        })

    override fun visitVariableDeclarationNode(
        node: VariableDeclarationNode,
        data: PathAwareUniquenessStateFlow
    ): PathAwareUniquenessStateFlow {
        val declaration = node.fir
        if (declaration.symbol.receiverTemporaryInitializer != null ||
            context(context) { declaration.symbol.borrowedViewSource() != null }) return visitNode(node, data)
        val initializer = declaration.initializer
        val leftSymbol = declaration.symbol
        val leftAccessState = EmptyAccessState.putChild(
            leftSymbol,
            AccessState(Access.Terminal)
        )

        with(context) {
            val rightAccessState = initializer?.resolveAccessState()
            val isWhenSubject = leftSymbol.source?.kind == KtFakeSourceElementKind.WhenGeneratedSubject
            val movesInitializer = !isWhenSubject && declaration !in readOnlyContext
            val sharesInitializer = movesInitializer &&
                    (leftSymbol.hasInferredUniqueness || leftSymbol.resolveDeclaredUniqueness() == Uniqueness.Shared)
            val source = initializer?.takeIf { sharesInitializer }?.wholeInferredLocal()

            return data.transformValues { data ->
                val uniquenessState = data.getOrInitialize()
                var newUniquenessState = uniquenessState.assign(leftAccessState, rightAccessState, movesInitializer)
                if (initializer != null && leftSymbol.hasInferredUniqueness) {
                    newUniquenessState = newUniquenessState.weakenInferredLocal(leftSymbol, initializer, data.facts())
                }
                if (source != null) {
                    newUniquenessState = newUniquenessState.shareInferredLocal(source, uniquenessState)
                    if (leftSymbol.hasInferredUniqueness) {
                        newUniquenessState = newUniquenessState.shareInferredLocal(leftSymbol, newUniquenessState)
                    }
                }
                data.withPaths(newUniquenessState)
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
                val rightAccessState = rightValue.resolveAccessState()
                var newUniquenessState = uniquenessState.assign(leftAccessState, rightAccessState, movesSource = true) {
                    if (leftValue is FirQualifiedAccessExpression) it.passReceiversToAccessor(leftValue) else it
                }
                val target = leftValue.wholeInferredLocal()
                if (target != null) {
                    newUniquenessState = newUniquenessState.weakenInferredLocal(target, rightValue, data.facts())
                }
                val source = rightValue.wholeInferredLocal()
                if (source != null && (target != null || leftValue.resolveUniqueness() == Uniqueness.Shared)) {
                    newUniquenessState = newUniquenessState.shareInferredLocal(source, uniquenessState)
                    if (target != null) newUniquenessState = newUniquenessState.shareInferredLocal(target, newUniquenessState)
                }
                data.withPaths(newUniquenessState)
            }
        }
    }

    override fun visitQualifiedAccessNode(
        node: QualifiedAccessNode,
        data: PathAwareUniquenessStateFlow
    ): PathAwareUniquenessStateFlow = context(context) {
        val access = node.fir
        val fact = data.joinFactsOverEdgeKinds()?.classify(access)
        data.transformValues { flow ->
            val facts = flow.facts()
            val paths = facts.paths.passReceiversToAccessor(access)
            flow.put(Unit, facts.copy(paths = paths, accesses = fact?.let { facts.accesses.put(access, it) } ?: facts.accesses))
        }
    }

    /**
     * Moves the receivers that [access] passes to an accessor call, as at a function call. An access that calls no
     * accessor leaves [this] unchanged.
     */
    context(context: CheckerContext)
    private fun UniquenessState.passReceiversToAccessor(access: FirQualifiedAccessExpression): UniquenessState {
        if (access in readOnlyContext || access.stringBuilderIntrinsic(context.session) != null) return this
        val property = access.accessorCallProperty(context.session) ?: return this
        if (property.isPure(context.session)) return this
        var newUniquenessState = this
        for (receiver in access.allReceiverExpressions) {
            newUniquenessState = receiver.resolveAccessState().move(newUniquenessState)
        }
        return newUniquenessState.restoreBorrowedReceivers(access, this)
    }

    context(context: CheckerContext)
    private fun AccessState.restoreBorrow(state: UniquenessState, before: UniquenessState): UniquenessState =
        enumeratePaths().fold(state) { result, path ->
            val current = result.find(path) ?: return@fold result
            if (current.data != Uniqueness.Moved) result
            else result.insert(path, current.copy(data = before.uniquenessAlong(path)))
        }

    context(context: CheckerContext)
    private fun UniquenessState.restoreBorrowedReceivers(
        access: FirQualifiedAccessExpression,
        before: UniquenessState,
        except: FirExpression? = null,
    ): UniquenessState {
        var state = this
        for ((argument, locality) in QualifiedAccessArgumentLocalitiesMapper.mapArgumentTypeFactsOf(access)) {
            if (argument !== except && locality == Locality.Local) {
                state = argument.resolveAccessState().restoreBorrow(state, before)
            }
        }
        return state
    }

    override fun visitFunctionCallEnterNode(
        node: FunctionCallEnterNode,
        data: PathAwareUniquenessStateFlow
    ): PathAwareUniquenessStateFlow {
        val call = node.fir
        if (call !in readOnlyContext && (call.toResolvedCallableSymbol()?.callableId == genericArraySetId ||
                    call.genericArrayBulk() == GenericArrayBulk.Fill)) {
            val parameterName = if (call.genericArrayBulk() == GenericArrayBulk.Fill) "element" else "value"
            val value = call.resolvedArgumentMapping!!.entries.single { it.value.name.asString() == parameterName }.key
            return with(context) {
                data.transformValues { flow ->
                    flow.withPaths(value.resolveAccessState().move(flow.getOrInitialize()))
                }
            }
        }
        return with(context) {
            val input = data.joinFactsOverEdgeKinds()
            val fact = input?.classify(call)
            val receiver = call.toResolvedCallableSymbol()?.let { call.receiverRequiringModelOwnership(it) }
            val sharedLocals = if (call.movesArguments) {
                call.sharedInferredLocals(callArgumentLocalitiesMapper, receiver.takeIf { fact?.checked == true })
            } else emptySet()
            data.transformValues { flow ->
                val before = flow.facts()
                var paths = before.paths
                if (call.movesArguments) {
                    for (operand in call.allReceiverExpressions + call.arguments) {
                        val access = operand.resolveAccessState()
                        val movable = if (operand === receiver && fact?.checked != true) {
                            access.copy(children = access.children.mutate { roots ->
                                roots.keys.removeAll { operand.isOnlyUnownedPropertyRoot(it) }
                            })
                        } else access
                        paths = movable.move(paths)
                    }
                    for (symbol in sharedLocals) paths = paths.shareInferredLocal(symbol, before.paths)
                }
                flow.put(Unit, before.copy(
                    paths = paths,
                    accesses = if (fact != null) before.accesses.remove(call) else before.accesses,
                    callEntries = if (call.movesArguments || fact != null) {
                        before.callEntries.put(call, CallEntryFact(before.paths, fact))
                    } else before.callEntries,
                ))
            }
        }
    }

    override fun visitFunctionCallExitNode(
        node: FunctionCallExitNode,
        data: PathAwareUniquenessStateFlow
    ): PathAwareUniquenessStateFlow {
        val call = node.fir
        with(context) {
            val callee = call.toResolvedCallableSymbol()
            val gatedReceiver = callee?.let { call.receiverRequiringModelOwnership(it) }
            val gatedExtension = gatedReceiver?.takeIf { it === call.extensionReceiver }
            val modelBorrowsExtension = gatedExtension != null && callee.externalSpecificationSymbol(context.session)
                ?.receiverParameterSymbol?.resolveLocality() == Locality.Local
            return data.transformValues { data ->
                val facts = data.facts()
                val entry = facts.callEntries[call] ?: return@transformValues data.withPaths(facts.paths)
                val checked = entry.model?.checked == true
                var paths = facts.paths
                if (call.movesArguments) {
                    paths = paths.restoreBorrowedReceivers(call, entry.paths, gatedExtension)
                    if (checked && gatedExtension != null && modelBorrowsExtension) {
                        paths = gatedExtension.resolveAccessState().restoreBorrow(paths, entry.paths)
                    }
                    for ((argument, locality) in callArgumentLocalitiesMapper.mapArgumentTypeFactsOf(call)) {
                        if (locality == Locality.Local) paths = argument.resolveAccessState().restoreBorrow(paths, entry.paths)
                    }
                }
                data.put(Unit, facts.copy(
                    paths = paths,
                    accesses = entry.model?.let { facts.accesses.put(call, it) } ?: facts.accesses,
                    callEntries = facts.callEntries.remove(call),
                ))
            }
        }
    }

    override fun visitExitDefaultArgumentsNode(
        node: ExitDefaultArgumentsNode,
        data: PathAwareUniquenessStateFlow,
    ): PathAwareUniquenessStateFlow {
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
                data.withPaths(newUniquenessState)
            }
        }
    }

    override fun visitJumpNode(
        node: JumpNode,
        data: PathAwareUniquenessStateFlow,
    ): PathAwareUniquenessStateFlow {
        return when (val jumpExpression = node.fir) {
            is FirReturnExpression -> {
                with (context) {
                    data.transformValues { data ->
                        var newUniquenessState = data.getOrInitialize()
                        val resultAccessState = jumpExpression.result.resolveAccessState()
                        newUniquenessState = resultAccessState.move(newUniquenessState)

                        data.withPaths(newUniquenessState)
                    }
                }
            }
            else -> { data }
        }
    }

    override fun visitThrowExceptionNode(
        node: ThrowExceptionNode,
        data: PathAwareUniquenessStateFlow,
    ): PathAwareUniquenessStateFlow {
        val throwExpression = node.fir

        return with (context) {
            data.transformValues { data ->
                var newUniquenessState = data.getOrInitialize()
                val exceptionAccessState = throwExpression.exception.resolveAccessState()
                newUniquenessState = exceptionAccessState.move(newUniquenessState)

                data.withPaths(newUniquenessState)
            }
        }
    }
}
