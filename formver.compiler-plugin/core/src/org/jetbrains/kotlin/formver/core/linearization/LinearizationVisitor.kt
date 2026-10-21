/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.formver.core.embeddings.types.StringBuilderEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.CharTypeEmbedding
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.common.FoldOwnershipCondition
import org.jetbrains.kotlin.formver.common.FoldOwnershipRule
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.common.attributingFailuresTo
import org.jetbrains.kotlin.formver.core.asPosition
import org.jetbrains.kotlin.formver.core.conversion.constructedOpen
import org.jetbrains.kotlin.formver.core.embeddings.callables.NamedFunctionSignature
import org.jetbrains.kotlin.formver.core.conversion.AccessPolicy
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.domains.InjectionImageFunction
import org.jetbrains.kotlin.formver.core.domains.IntegerBounds
import org.jetbrains.kotlin.formver.core.embeddings.types.StringTypeEmbedding
import org.jetbrains.kotlin.formver.core.domains.SeqMultisetDomain
import org.jetbrains.kotlin.formver.core.domains.EqualsRelationDomain
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain.Companion.isOf
import org.jetbrains.kotlin.formver.core.embeddings.*
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.core.embeddings.asInfo
import org.jetbrains.kotlin.formver.core.embeddings.callables.toFuncApp
import org.jetbrains.kotlin.formver.core.embeddings.callables.toMethodCall
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.viper.ast.PermExp
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.formver.core.embeddings.types.asTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.IntArrayEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.GenericArrayEmbedding
import org.jetbrains.kotlin.formver.intrinsics.plugin.GenericArrayBulk
import org.jetbrains.kotlin.formver.core.embeddings.types.contentsField
import org.jetbrains.kotlin.formver.core.embeddings.types.fillHoles
import org.jetbrains.kotlin.formver.core.embeddings.types.injection
import org.jetbrains.kotlin.formver.core.embeddings.types.injectionOrNull
import org.jetbrains.kotlin.formver.core.names.sourceSpelling
import org.jetbrains.kotlin.formver.core.names.AnonymousBuiltinName
import org.jetbrains.kotlin.formver.viper.ast.Declaration
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Method
import org.jetbrains.kotlin.formver.viper.ast.Exp.Companion.toConjunction
import org.jetbrains.kotlin.formver.viper.ast.Stmt
import org.jetbrains.kotlin.formver.viper.ast.Type
import org.jetbrains.kotlin.formver.viper.ast.viperLiteral
import org.jetbrains.kotlin.formver.viper.ast.ge
import org.jetbrains.kotlin.formver.viper.ast.le

data class LinearizationVisitor(
    val source: KtSourceElement? = null,
) : ExpVisitor<Linearizable> {

    /**
     * Linearize a child expression, propagating visitor state (including position).
     */
    private fun ExpEmbedding.linearize(): Linearizable = accept(this@LinearizationVisitor)

    private fun getQuantifierParts(
        variable: VariableEmbedding,
        conditions: List<ExpEmbedding>,
        triggerExpressions: List<ExpEmbedding>,
        ctx: LinearizationContext,
    ): Pair<Exp, List<Exp.Trigger>> {
        val lawView = when (ctx) {
            is PureFunBodyLinearizer -> ctx.lawView
            is PureExpLinearizer -> ctx.lawView
            else -> null
        }
        val conjunction = if (lawView != null) {
            val lawContext = PureExpLinearizer(ctx.source, ctx.typeResolver, lawView = lawView)
            conditions.map { it.linearize().toViperBuiltinType(lawContext) }.toConjunction()
        } else if (ctx is PureExpLinearizer && ctx.observerReceiver != null) {
            conditions.map { it.linearize().toViperBuiltinType(ctx) }.toConjunction()
        } else {
            conditions.pureToViper(true, ctx.typeResolver, ctx.source).toConjunction()
        }
        if (triggerExpressions.isEmpty()) return conjunction to derivedTriggers(variable.name, conjunction)
        val triggerContext = if (ctx is PureExpLinearizer) ctx.copy(isTrigger = true)
        else PureExpLinearizer(ctx.source, ctx.typeResolver, isTrigger = true)
        val viperTriggers = triggerExpressions.map { triggerExpr ->
            Exp.Trigger(listOf(triggerExpr.linearize().toViperBuiltinType(triggerContext)))
        }
        return conjunction to viperTriggers
    }

    // region Control Flow

    override fun visitBlock(e: Block): Linearizable = object : OptionalResultLinearizable(e) {
        override fun toViperMaybeStoringIn(result: VariableEmbedding?, ctx: LinearizationContext) {
            if (e.exps.isEmpty()) return
            for (exp in e.exps.take(e.exps.size - 1)) {
                exp.linearize().toViperUnusedResult(ctx)
            }
            e.exps.last().linearize().toViperMaybeStoringIn(result, ctx)
        }

        private fun value(ctx: LinearizationContext, builtinType: TypeEmbedding?): Exp =
            ctx.addBlock(e.exps.dropLast(1).map { it.linearize() }, e.exps.last().linearize(), e.type, builtinType)

        override fun toViper(ctx: LinearizationContext): Exp =
            if (e.exps.isEmpty()) super.toViper(ctx) else value(ctx, builtinType = null)

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
            if (e.exps.isEmpty()) super.toViperBuiltinType(ctx) else value(ctx, e.type)

        override fun toViperBuiltinTypeAs(type: TypeEmbedding, ctx: LinearizationContext): Exp =
            if (e.exps.isEmpty()) super.toViperBuiltinTypeAs(type, ctx) else value(ctx, type)
    }

    override fun visitIf(e: If): Linearizable = object : OptionalResultLinearizable(e) {
        override fun toViperMaybeStoringIn(result: VariableEmbedding?, ctx: LinearizationContext) {
            ctx.addBranch(e.condition.linearize(), e.thenBranch.linearize(), e.elseBranch.linearize(), result)
        }

        override fun toViper(ctx: LinearizationContext): Exp =
            ctx.addConditional(e.condition.linearize(), e.thenBranch.linearize(), e.elseBranch.linearize(), e.type)
    }

    override fun visitWhile(e: While): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            val foldState = ctx.foldState
            val headShapes = foldState?.enterLoop(
                ctx, e.continueLabel.name, e.headShapes, e.framedShapes, e.breakLabel.name, e.exitShapes,
            ).orEmpty()
            ctx.requireFoldedReads(e.invariants, headShapes + e.exitShapes)
            // The permissions come first, since the user's invariants may read through them.
            val headPermissions = headShapes.mapNotNull { foldState?.permission(it) }
            ctx.addLabel(e.continueLabel.copy(invariants = headPermissions + e.continueLabel.invariants).toViper(ctx, e.invariants))
            withinScope(ctx, e.placedLabels(), leave = { _, state -> state.leaveLoop(e.breakLabel.name) }) {
                val condVar = ctx.freshAnonVar { boolean() }
                e.condition.linearize().toViperStoringIn(condVar, ctx)
                ctx.addStatement {
                    ctx.foldState?.normalize(ctx)
                    val bodyBlock = ctx.withFoldStateRestored {
                        asBlock {
                            e.body.linearize().toViperUnusedResult(this)
                            addStatement { e.continueLabel.toLink().toViperGoto(this) }
                        }
                    }
                    Stmt.If(condVar.linearize().toViperBuiltinType(ctx), bodyBlock, els = Stmt.Seqn(), ctx.source.asPosition)
                }
            }
            ctx.addLabel(e.breakLabel.toViper(ctx, e.invariants))

            e.invariants.forEach {
                val invariant = e.exitGuard?.let { guard -> OperatorExpEmbeddings.Implies(guard, it) } ?: it
                ctx.addStatement {
                    Stmt.Assert(invariant.pureToViper(toBuiltin = true, ctx.typeResolver),
                        info = SourceRole.Proof(KotlinCondition.ProofClause.LOOP_INVARIANT).asInfo)
                }
            }
        }
    }

    override fun visitGoto(e: Goto): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            ctx.addStatement { e.target.toViperGoto(ctx) }
        }
    }

    override fun visitLabelExp(e: LabelExp): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            ctx.addLabel(e.label.toViper(ctx))
        }
    }

    override fun visitTryFinally(e: TryFinally): Linearizable = FinallyLinearizable(e, this)

    override fun visitGotoChainNode(e: GotoChainNode): Linearizable = object : OptionalResultLinearizable(e) {
        override fun toViperMaybeStoringIn(result: VariableEmbedding?, ctx: LinearizationContext) {
            e.label?.let { ctx.addLabel(it.toViper(ctx)) }
            ctx.addStatement {
                e.exp.linearize().toViperMaybeStoringIn(result, ctx)
                e.next.toViperGoto(ctx)
            }
        }
    }

    override fun visitNonDeterministically(e: NonDeterministically): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            ctx.addStatement {
                val choice = ctx.freshAnonVar { boolean() }
                val (expViper, skipViper) = ctx.branchBlocks({ e.exp.linearize().toViper(this) }, {})
                Stmt.If(choice.linearize().toViperBuiltinType(ctx), expViper, skipViper, ctx.source.asPosition)
            }
        }
    }

    override fun visitMethodCall(e: MethodCall): Linearizable = object : StoredResultLinearizable(e) {
        override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) {
            val args = mutableListOf<ExpEmbedding>()
            val argsViper = e.args.map {
                val value = runUpdatesOfPath(it, ctx)
                args += value
                value.linearize().toViper(ctx)
            }
            e.exceptionalExit?.let { exit ->
                NonDeterministically(exit).linearize().toViperUnusedResult(ctx)
            }
            val freshLoans = ctx.coerceFreshArguments(args, e.method.formalArgs, argsViper)
            val heldArgs = ctx.heldPaths(args, e.method.formalArgs)
            for ((path, formal) in heldArgs) {
                when {
                    formal.isUnique -> ctx.exposeFor(path, formal)
                    formal.isBorrowed -> if (ctx.foldState?.isConstructionRoot(path) == true) ctx.foldState?.tidy(ctx, path)
                        else ctx.foldState?.close(ctx, path)
                }
            }
            ctx.addStatement {
                e.method.toMethodCall(argsViper, result.toLocalVarUse(ctx.source.asPosition), ctx.source.asPosition)
            }
            freshLoans.forEach { (index, from, to) ->
                val restore = ctx.typeResolver.familyRestore(from, to)
                ctx.addFamilyCall(argsViper[index], args[index].type.isNullable, restore)
            }
            e.method.constructedOpen?.let { ctx.foldConstructed(result, it) }
            for ((path, formal) in heldArgs) {
                when {
                    !formal.isBorrowed -> ctx.foldState?.release(ctx, path)
                    // The callee may write through its shared view of the argument, so the caller's values are stale.
                    !formal.isUnique -> ctx.foldState?.refresh(ctx, path)
                    else -> ctx.foldState?.restoreBorrowed(ctx, path, ctx.foldState?.trackedClass(formal.type))
                }
            }
        }
    }

    override fun visitRepresentationObserverCall(e: RepresentationObserverCall): Linearizable =
        object : DirectResultLinearizable(e, this@LinearizationVisitor) {
            override fun toViper(ctx: LinearizationContext): Exp = e.expand(e.args.map { it.linearize().toViper(ctx) })
        }

    override fun visitFunctionCall(e: FunctionCall): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            // Evaluating a discarded observation still checks its domain.
            toViperStoringIn(ctx.freshAnonVar(e.type), ctx)
        }

        override fun toViper(ctx: LinearizationContext): Exp {
            val argsViper = e.args.map { it.linearize().toViper(ctx) }
            val lawView = when (ctx) {
                is PureFunBodyLinearizer -> ctx.lawView
                is PureExpLinearizer -> ctx.lawView
                else -> null
            }
            if (lawView != null) {
                val binding = if (ctx.typeResolver.isInterface(lawView)) ctx.typeResolver.observerBinding(lawView, e.function.name)
                else ctx.typeResolver.observerImplementation(lawView, e.function.name)
                if (binding != null) return if (ctx.typeResolver.isInterface(lawView)) binding.trigger(argsViper, ctx.source.asPosition)
                else binding.application(argsViper, ctx.source.asPosition)
            }
            if (ctx is PureExpLinearizer) {
                ctx.contractObserverApplication(e.function.name, argsViper)?.let { return it }
                val view = e.args.firstOrNull()?.type?.pretype as? ClassTypeEmbedding
                val binding = view?.let { ctx.typeResolver.observerBinding(it, e.function.name) }
                if (binding != null) {
                    val trigger = binding.trigger(argsViper)
                    binding.signature.formalArgs.forEachIndexed { index, formal ->
                        if (formal.isUnique) ctx.addPredicateParameter(PredicateParameter(trigger.functionName, index))
                    }
                    return binding.application(argsViper, ctx.source.asPosition)
                }
            }
            val view = e.args.firstOrNull()?.type?.pretype as? ClassTypeEmbedding
            val viewBinding = view?.let { receiverType -> ctx.typeResolver.observerBinding(receiverType, e.function.name) }
            val formals = if (viewBinding == null) e.function.formalArgs else e.function.formalArgs.mapIndexed { index, formal ->
                if (index == 0) PlaceholderVariableEmbedding(formal.name, view!!.asTypeEmbedding(),
                    isUnique = formal.isUnique || ctx.typeResolver.isInterface(view)) else formal
            }
            val exposed = ctx.heldPaths(e.args, formals).filter { (_, formal) -> formal.isUnique }
            for ((path, formal) in exposed) ctx.exposeFor(path, formal)
            val app = viewBinding?.application(argsViper, ctx.source.asPosition)
                ?: e.function.toFuncApp(argsViper, ctx.source.asPosition)
            if (app is Exp.FuncApp) e.function.formalArgs.forEachIndexed { index, formal ->
                if (formal.isUnique) ctx.addPredicateParameter(PredicateParameter(app.functionName, index))
            }
            if (exposed.isEmpty()) return app
            // The application is evaluated here, where the predicates its preconditions take are folded: a later
            // operand of the enclosing expression may unfold them.
            val result = ctx.freshAnonVar(e.type)
            ctx.addStatement { Stmt.assign(result.toViperExp(this), app, source.asPosition) }
            for ((path, formal) in exposed) {
                val state = ctx.foldState ?: continue
                val cls = state.trackedClass(formal.type) ?: continue
                val own = state.trackedClass(path.fields.lastOrNull()?.type ?: path.root.type)
                if (own != cls && ctx.typeResolver.isFamilyView(cls)) state.restoreBorrowed(ctx, path, cls)
            }
            return result.toViperExp(ctx)
        }
    }

    override fun visitInvokeFunctionObject(e: InvokeFunctionObject): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp {
            val variable = ctx.freshAnonVar(e.type)
            e.receiver.linearize().toViperUnusedResult(ctx)
            val args = e.args.map { arg ->
                runUpdatesOfPath(arg, ctx).also { it.linearize().toViperUnusedResult(ctx) }
            }
            e.exceptionalExit?.let { exit ->
                NonDeterministically(exit).linearize().toViperUnusedResult(ctx)
            }
            val state = ctx.foldState
            val heldArgs = if (state == null) emptyList() else args.mapNotNull { arg -> arg.ownedPath()?.takeIf(state::holds) }
            for (path in heldArgs) {
                if (state!!.isConstructionRoot(path)) state.tidy(ctx, path) else state.close(ctx, path)
            }
            // The function has no contract and may write to every owned argument it borrows: the caller keeps the
            // argument's predicate with fresh values under it, as after a call with no postcondition.
            for (path in heldArgs) state!!.refresh(ctx, path)
            return variable.withInvariants(ctx.typeResolver) {
                proven = true
                access = true
            }.linearize().toViper(ctx)
        }

        // Must call toViper (not just iterate children) to emit the invariant inhales.
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            toViper(ctx)
        }
    }

    override fun visitFunctionExp(e: FunctionExp): Linearizable = object : OptionalResultLinearizable(e) {
        override fun toViperMaybeStoringIn(result: VariableEmbedding?, ctx: LinearizationContext) {
            val foldState = ctx.foldState
            val signature = e.signature
            if (foldState != null && signature != null) {
                signature.formalArgs.filter { it.isUnique }.forEach { foldState.acquire(ctx, OwnedPath(it)) }
            }
            e.body.linearize().toViperMaybeStoringIn(result, ctx)
            ctx.returnSites?.leaveAtEnd(e.returnLabel, ctx)
            ctx.addLabel(e.returnLabel.toViper(ctx))
            if (foldState != null && signature != null) {
                signature.formalArgs.filter { it.isUnique && it.isBorrowed }.forEach { foldState.close(ctx, OwnedPath(it)) }
                val receiver = signature.dispatchReceiver
                val receiverClass = receiver?.type?.pretype as? ClassTypeEmbedding
                if (receiver?.isUnique == true && !receiver.isBorrowed &&
                    receiverClass?.let(ctx.typeResolver::hasClassLaws) == true) {
                    val path = OwnedPath(receiver)
                    if (foldState.holds(path)) foldState.close(ctx, path)
                }
                if (signature.callableType.returnsUnique) {
                    foldState.close(ctx, OwnedPath(signature.returns))
                    val constructed = (signature as? NamedFunctionSignature)?.constructedOpen
                    // A manual class's construction holds its fields unfolded throughout.
                    if (constructed != null && !with(ctx.typeResolver) { constructed.isManual }) {
                        val returned = signature.returns.toLocalVarUse()
                        for (cls in generateSequence(constructed, ctx.typeResolver::superClass)) {
                            ctx.addStatement { Stmt.Unfold(hierarchyPredicateAccess(returned, cls, ctx.source), ctx.source.asPosition) }
                        }
                    }
                }
            }
        }
    }

    /**
     * The temporaries for the arguments, the entry operation of each binding, the body, the exit operation of each
     * binding and the fold of the result when the callee returns it `@Unique`, then the result, as a block.
     */
    override fun visitInlineCall(e: InlineCall): Linearizable = object : OptionalResultLinearizable(e) {
        private val steps: List<Linearizable> = buildList {
            add(Declare(e.returnVariable, null).linearize())
            e.declarations.forEach { add(it.linearize()) }
            add(foldOperations(e) { ctx, state -> e.bindings.forEach { ctx.enterBinding(it, state) } })
            add(object : UnitResultLinearizable(e) {
                override fun toViperUnusedResult(ctx: LinearizationContext) {
                    withinScope(ctx, e.body.placedLabels(), leave = { jumpCtx, state ->
                        e.bindings.forEach { jumpCtx.exitBinding(it, state) }
                    }) { e.body.linearize().toViperUnusedResult(ctx) }
                }
            })
            add(foldOperations(e) { ctx, state ->
                e.bindings.forEach { ctx.exitBinding(it, state) }
                val returned = OwnedPath(e.returnVariable)
                if (e.returnsUnique) state.close(ctx, returned) else state.release(ctx, returned)
            })
        }

        override fun toViperMaybeStoringIn(result: VariableEmbedding?, ctx: LinearizationContext) {
            steps.forEach { it.toViperUnusedResult(ctx) }
            e.result.linearize().toViperMaybeStoringIn(result, ctx)
        }

        private fun value(ctx: LinearizationContext, builtinType: TypeEmbedding?): Exp =
            ctx.addBlock(steps, e.result.linearize(), e.type, builtinType)

        override fun toViper(ctx: LinearizationContext): Exp = value(ctx, builtinType = null)
        override fun toViperBuiltinType(ctx: LinearizationContext): Exp = value(ctx, e.type)
        override fun toViperBuiltinTypeAs(type: TypeEmbedding, ctx: LinearizationContext): Exp = value(ctx, type)
    }

    /**
     * Runs [action] inside a scope whose labels are [labels], when there is a fold state: a jump to another label first
     * runs [leave] with the jump's context and the fold state.
     */
    private fun withinScope(
        ctx: LinearizationContext,
        labels: Set<SymbolicName>,
        leave: (LinearizationContext, FoldState) -> Unit,
        action: () -> Unit,
    ) {
        val state = ctx.foldState ?: return action()
        state.withinScope(labels, { leave(it, state) }, action)
    }

    /** A step of [e] that runs [operations] on the fold state, when there is one. */
    private fun foldOperations(e: ExpEmbedding, operations: (LinearizationContext, FoldState) -> Unit): Linearizable =
        object : UnitResultLinearizable(e) {
            override fun toViperUnusedResult(ctx: LinearizationContext) {
                ctx.foldState?.let { operations(ctx, it) }
            }
        }

    override fun visitElvis(e: Elvis): Linearizable = object : StoredResultLinearizable(e) {
        private fun conditional(ctx: LinearizationContext): Linearizable {
            val leftWrapped = ExpWrapper(e.left.linearize().toViper(ctx), e.left.type)
            return If(leftWrapped.notNullCmp(), leftWrapped.withType(e.type), e.right.withType(e.type), e.type).linearize()
        }

        override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) {
            conditional(ctx).toViperStoringIn(result, ctx)
        }

        override fun toViper(ctx: LinearizationContext): Exp = conditional(ctx).toViper(ctx)
    }

    override fun visitReturn(e: Return): Linearizable = object : OptionalResultLinearizable(e) {
        override fun toViperMaybeStoringIn(result: VariableEmbedding?, ctx: LinearizationContext) {
            val returnExp = runUpdatesOfPath(e.returnExp, ctx)
            val source = ctx.moveSource(returnExp, targetOwned = true)
            val value = returnExp.linearize()
            ctx.addReturn(object : Linearizable by value {
                override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) {
                    value.toViperStoringIn(result, ctx)
                    ctx.finishMove(returnExp, source, targetOwned = true, OwnedPath(e.target.variable))
                    ctx.returnSites?.leaveThroughReturn(e.target, this@LinearizationVisitor.source, ctx)
                }
            }, e.target)
        }
    }

    // endregion

    // region Literals

    override fun visitLiteralEmbedding(e: LiteralEmbedding): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp =
            if (e is NullLit) RuntimeTypeDomain.nullValue(pos = ctx.source.asPosition)
            else if (e is StringLit) ctx.typeResolver.stringLiteral(e.value)
                .toFuncApp(emptyList(), pos = ctx.source.asPosition, info = e.sourceRole.asInfo)
            else e.type.injection.toRef(
                e.value.viperLiteral(ctx.source.asPosition, e.sourceRole.asInfo),
                pos = ctx.source.asPosition,
                info = e.sourceRole.asInfo,
            )
    }

    override fun visitUnitLit(e: UnitLit): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) = Unit
    }

    // endregion

    // region Variables

    override fun visitVariableEmbedding(e: VariableEmbedding): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp {
            val exp = e.toViperExp(ctx)
            return if (exp is Exp.Result && ctx is PureExpLinearizer) ctx.functionResult ?: exp else exp
        }
    }

    // endregion

    // region Special

    override fun visitExpWrapper(e: ExpWrapper): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp = e.value
    }

    override fun visitUnreachable(e: Unreachable): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            ctx.addStatement { Stmt.Inhale(Exp.BoolLit(false, ctx.source.asPosition), ctx.source.asPosition) }
        }
    }

    override fun visitUnsupportedPlaceholder(e: UnsupportedPlaceholder): Linearizable =
        throw SnaktInternalException(source, "A function with an unsupported construct reached linearization.")

    override fun visitAssert(e: Assert): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            ctx.addStatement { Stmt.Assert(e.exp.linearize().toViperBuiltinType(ctx.forSpecification()), ctx.source.asPosition,
                SourceRole.Proof(org.jetbrains.kotlin.formver.common.KotlinCondition.ProofClause.ASSERTION).asInfo) }
        }
    }

    override fun visitRefute(e: Refute): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            ctx.addStatement { Stmt.Refute(e.exp.linearize().toViperBuiltinType(ctx.forSpecification()), ctx.source.asPosition,
                SourceRole.Proof(org.jetbrains.kotlin.formver.common.KotlinCondition.ProofClause.REFUTATION).asInfo) }
        }
    }

    override fun visitBindOwnedField(e: BindOwnedField): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            ctx.foldState?.transfer(ctx, OwnedPath(e.value), OwnedPath(e.receiver) + e.step)
        }
    }

    override fun visitAllocateObject(e: AllocateObject): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            val result = e.result.toLocalVarUse()
            ctx.addStatement { Stmt.New(result, e.fields.map { it.toViper() }, ctx.source.asPosition) }
            ctx.foldState?.acquireConstruction(ctx, OwnedPath(e.result), e.constructed)
            ctx.addStatement {
                Stmt.Inhale(Exp.EqCmp(RuntimeTypeDomain.typeOf(result), e.constructed.runtimeType), ctx.source.asPosition)
            }
        }
    }

    override fun visitEstablishConstruction(e: EstablishConstruction): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp {
            ctx.foldState?.establishConstruction(ctx, OwnedPath(e.receiver), e.declaring)
            return e.result.linearize().toViper(ctx)
        }

        override fun toViperUnusedResult(ctx: LinearizationContext) {
            ctx.foldState?.establishConstruction(ctx, OwnedPath(e.receiver), e.declaring)
            e.result.linearize().toViperUnusedResult(ctx)
        }
    }

    override fun visitInhaleDirect(e: InhaleDirect): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            ctx.addStatement { Stmt.Inhale(e.exp.linearize().toViperBuiltinType(ctx.forSpecification()), ctx.source.asPosition) }
        }
    }

    // endregion

    // region Type Operations

    override fun visitIs(e: Is): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp =
            RuntimeTypeDomain.boolInjection.toRef(
                RuntimeTypeDomain.isSubtype(
                    RuntimeTypeDomain.typeOf(e.inner.linearize().toViper(ctx), pos = ctx.source.asPosition),
                    e.comparisonType.runtimeType,
                    pos = ctx.source.asPosition,
                    info = e.sourceRole.asInfo
                ),
                pos = ctx.source.asPosition,
                info = e.sourceRole.asInfo
            )
    }

    override fun visitCast(e: Cast): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp = e.inner.linearize().toViper(ctx)

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp = toViperBuiltinTypeAs(e.type, ctx)

        override fun toViperBuiltinTypeAs(type: TypeEmbedding, ctx: LinearizationContext): Exp =
            e.inner.linearize().toViperInBuiltinForm(e.inner.type, type, ctx)
    }

    override fun visitSafeCast(e: SafeCast): Linearizable = object : StoredResultLinearizable(e) {
        override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) {
            conditional(ctx).toViperStoringIn(result, ctx)
        }

        override fun toViper(ctx: LinearizationContext): Exp = conditional(ctx).toViper(ctx)

        private fun conditional(ctx: LinearizationContext): Linearizable {
            val expWrapped = ExpWrapper(e.exp.linearize().toViper(ctx), e.exp.type)
            return If(Is(expWrapped, e.targetType), expWrapped.withType(e.type), NullLit.withType(e.type), e.type).linearize()
        }
    }

    override fun visitInhaleInvariants(e: InhaleInvariants): Linearizable {
        val inhaling = inhalingInvariants(e)
        val value = e.exp.linearize()
        fun LinearizationContext.chosen() = if (inhalesInvariants) inhaling else value
        return object : Linearizable {
            override fun toViper(ctx: LinearizationContext): Exp = ctx.chosen().toViper(ctx)

            override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) =
                ctx.chosen().toViperStoringIn(result, ctx)

            override fun toViperMaybeStoringIn(result: VariableEmbedding?, ctx: LinearizationContext) =
                ctx.chosen().toViperMaybeStoringIn(result, ctx)

            override fun toViperBuiltinType(ctx: LinearizationContext): Exp = ctx.chosen().toViperBuiltinType(ctx)

            override fun toViperBuiltinTypeAs(type: TypeEmbedding, ctx: LinearizationContext): Exp =
                ctx.chosen().toViperBuiltinTypeAs(type, ctx)

            override fun toViperUnusedResult(ctx: LinearizationContext) = ctx.chosen().toViperUnusedResult(ctx)
        }
    }

    private fun inhalingInvariants(e: InhaleInvariants): Linearizable {
        // InhaleInvariantsForVariable: expression is a variable, use OnlyToViper-style
        // (toViperUnusedResult must call toViper, not just iterate children, to emit the inhales)
        if (e.exp.underlyingVariable != null) {
            return object : DirectResultLinearizable(e, this@LinearizationVisitor) {
                override fun toViper(ctx: LinearizationContext): Exp {
                    val variable = e.exp.underlyingVariable ?: error("Use of InhaleInvariantsForVariable for non-variable")
                    for (invariant in e.invariants.fillHoles(variable)) {
                        ctx.addStatement {
                            Stmt.Inhale(
                                invariant.pureToViper(
                                    toBuiltin = true,
                                    ctx.typeResolver,
                                    ctx.source
                                ), ctx.source.asPosition
                            )
                        }
                    }
                    return e.exp.linearize().toViper(ctx)
                }

                override fun toViperUnusedResult(ctx: LinearizationContext) {
                    toViper(ctx)
                }
            }
        }
        // InhaleInvariantsForExp: store result then inhale invariants
        return object : StoredResultLinearizable(e) {
            override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) {
                e.exp.linearize().toViperStoringIn(result, ctx)
                for (invariant in e.invariants.fillHoles(result)) {
                    ctx.addStatement {
                        Stmt.Inhale(
                            invariant.pureToViper(
                                toBuiltin = true,
                                ctx.typeResolver,
                                ctx.source
                            ), ctx.source.asPosition
                        )
                    }
                }
            }
        }
    }

    // endregion

    // region Operators

    /** Check helper definedness while exposing interpreted terms to quantifier reasoning. */
    private fun checkedBuiltinValue(operation: InjectionImageFunction, call: Exp, ctx: LinearizationContext): Exp {
        if (ctx.isTrigger || operation.normalReturnCondition != null) return operation.resultInjection.fromRef(call)
        val application = call as Exp.FuncApp
        val values = application.args.zip(operation.argsInjections) { arg, injection ->
            val native = if (arg is Exp.DomainFuncApp && arg.function == injection.toRef) arg.args.single() else null
            // quantifierDomain permits the native binder to remain visible to matching.
            if (native is Exp.LocalVar && native.name is AnonymousBuiltinName) native else injection.fromRef(arg)
        }
        return Exp.LetBinding(
            Declaration.LocalVarDecl(AnonymousBuiltinName(-1), Type.Ref),
            application,
            operation.original.toFuncApp(values, call.pos, call.info),
            call.pos,
        )
    }

    override fun visitBinaryOperatorExpEmbedding(e: BinaryOperatorExpEmbedding): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            if (e.refsOperation.checked) ctx.evaluateUnused(toViper(ctx))
            else super.toViperUnusedResult(ctx)
        }

        override fun toViper(ctx: LinearizationContext): Exp {
            val args = listOf(e.left.linearize().toViper(ctx), e.right.linearize().toViper(ctx))
            val normalReturn = e.refsOperation.normalReturnCondition
            val exceptionalExit = e.exceptionalExit
            if (normalReturn != null && exceptionalExit != null && ctx.logicOperatorPolicy == LogicOperatorPolicy.CONVERT_TO_IF) {
                val values = args.zip(e.refsOperation.argsInjections) { arg, injection -> injection.fromRef(arg) }
                val abnormal = ctx.asBlock { exceptionalExit.linearize().toViperUnusedResult(this) }
                ctx.addStatement { Stmt.If(Exp.Not(normalReturn(values)), abnormal, null, source.asPosition) }
            }
            return e.refsOperation.toFuncApp(args, pos = ctx.source.asPosition, info = e.sourceRole.asInfo)
        }

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
            if (e.refsOperation.checked) checkedBuiltinValue(e.refsOperation, toViper(ctx), ctx)
            else e.builtinsOperation(
                e.left.linearize().toViperBuiltinType(ctx),
                e.right.linearize().toViperBuiltinType(ctx),
                pos = ctx.source.asPosition,
                info = e.sourceRole.asInfo
            )
    }

    override fun visitUnaryOperatorExpEmbedding(e: UnaryOperatorExpEmbedding): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            if (e.refsOperation.checked) ctx.evaluateUnused(toViper(ctx))
            else super.toViperUnusedResult(ctx)
        }

        override fun toViper(ctx: LinearizationContext): Exp =
            e.refsOperation(e.inner.linearize().toViper(ctx), pos = ctx.source.asPosition, info = e.sourceRole.asInfo)

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
            if (e.refsOperation.checked) checkedBuiltinValue(e.refsOperation, toViper(ctx), ctx)
            else e.builtinsOperation(e.inner.linearize().toViperBuiltinType(ctx), pos = ctx.source.asPosition, info = e.sourceRole.asInfo)
    }

    override fun visitInjectionBasedExpEmbedding(e: InjectionBasedExpEmbedding): Linearizable =
        error("visitInjectionBasedExpEmbedding should not be called directly")

    override fun visitSequentialAnd(e: SequentialAnd): Linearizable = sequentialLogicOperator(e)
    override fun visitSequentialOr(e: SequentialOr): Linearizable = sequentialLogicOperator(e)

    /** In a single expression, the right operand's locals are bound where the left operand does not decide the result. */
    private fun sequentialLogicOperator(e: SequentialLogicOperatorEmbedding): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        private fun LinearizationContext.asIf() = logicOperatorPolicy == LogicOperatorPolicy.CONVERT_TO_IF

        private fun shortCircuit(ctx: LinearizationContext): Exp {
            val left = e.left.linearize().toViperBuiltinType(ctx)
            val right = ctx.addGuarded(e.rightEvaluatedWhen(left)) { e.right.linearize().toViperBuiltinType(this) }
            return e.combine(left, right, ctx.source.asPosition)
        }

        override fun toViper(ctx: LinearizationContext): Exp =
            if (ctx.asIf()) e.ifReplacement.linearize().toViper(ctx)
            else e.type.injection.toRef(shortCircuit(ctx), pos = ctx.source.asPosition)

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
            if (ctx.asIf()) e.ifReplacement.linearize().toViperBuiltinType(ctx) else shortCircuit(ctx)

        override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) {
            if (ctx.asIf()) e.ifReplacement.linearize().toViperStoringIn(result, ctx) else super.toViperStoringIn(result, ctx)
        }

        override fun toViperUnusedResult(ctx: LinearizationContext) {
            if (ctx.asIf()) e.ifReplacement.linearize().toViperUnusedResult(ctx) else shortCircuit(ctx)
        }
    }

    // endregion

    // region Comparisons

    override fun visitEqCmp(e: EqCmp): Linearizable = comparisonLinearizable(e)
    override fun visitNeCmp(e: NeCmp): Linearizable = comparisonLinearizable(e)

    override fun visitIdentityCmp(e: IdentityCmp): Linearizable = comparisonLinearizable(e)

    override fun visitObjectEquality(e: ObjectEquality): Linearizable {
        fun LinearizationContext.chosen(): Linearizable = when (equalityEvaluation) {
            EqualityEvaluation.EXECUTED ->
                (e.executed ?: throw UnsupportedFeatureException(source, e.unsupportedExecution ?: "this `==`")).linearize()
            EqualityEvaluation.SPECIFIED, EqualityEvaluation.PURE_FUNCTION -> e.specified.linearize()
        }
        return object : Linearizable {
            override fun toViper(ctx: LinearizationContext): Exp = ctx.chosen().toViper(ctx)

            override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) =
                ctx.chosen().toViperStoringIn(result, ctx)

            override fun toViperMaybeStoringIn(result: VariableEmbedding?, ctx: LinearizationContext) =
                ctx.chosen().toViperMaybeStoringIn(result, ctx)

            override fun toViperBuiltinType(ctx: LinearizationContext): Exp = ctx.chosen().toViperBuiltinType(ctx)

            override fun toViperBuiltinTypeAs(type: TypeEmbedding, ctx: LinearizationContext): Exp =
                ctx.chosen().toViperBuiltinTypeAs(type, ctx)

            override fun toViperUnusedResult(ctx: LinearizationContext) = ctx.chosen().toViperUnusedResult(ctx)
        }
    }

    override fun visitEqualsRelation(e: EqualsRelation): Linearizable =
        object : DirectResultLinearizable(e, this@LinearizationVisitor) {
            override fun toViper(ctx: LinearizationContext): Exp =
                RuntimeTypeDomain.boolInjection.toRef(toViperBuiltinType(ctx), pos = ctx.source.asPosition)

            override fun toViperBuiltinType(ctx: LinearizationContext): Exp {
                if (ctx.equalityEvaluation == EqualityEvaluation.PURE_FUNCTION) {
                    throw UnsupportedFeatureException(
                        ctx.source, "`==` in a `@Pure` function on a type whose `equals` is neither `@Pure` nor identity",
                    )
                }
                ctx.typeResolver.markEqualsRelationUsed()
                return EqualsRelationDomain.relation(
                    e.left.linearize().toViper(ctx), e.right.linearize().toViper(ctx), pos = ctx.source.asPosition,
                )
            }
        }

    private fun comparisonLinearizable(e: AnyComparisonExpression): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp =
            RuntimeTypeDomain.boolInjection.toRef(
                toViperBuiltinType(ctx),
                pos = ctx.source.asPosition,
                info = e.sourceRole.asInfo
            )

        private fun checkExecution(ctx: LinearizationContext) {
            if (e is IdentityCmp && ctx.equalityEvaluation != EqualityEvaluation.SPECIFIED) {
                e.executionRejection?.let { throw UnsupportedFeatureException(ctx.source, it) }
            }
        }

        override fun toViperUnusedResult(ctx: LinearizationContext) {
            checkExecution(ctx)
            super.toViperUnusedResult(ctx)
        }

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp {
            checkExecution(ctx)
            fun ExpEmbedding.operand(): Exp =
                if (e.comparesUnwrapped) linearize().toViperBuiltinType(ctx) else linearize().toViper(ctx)
            return e.comparisonOperation(
                e.left.operand(),
                e.right.operand(),
                pos = ctx.source.asPosition,
                info = e.sourceRole.asInfo
            )
        }
    }

    // endregion

    // region Invariant

    override fun visitOld(e: Old): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp =
            Exp.Old(e.inner.linearize().toViper(ctx.forSpecification()), ctx.source.asPosition)

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
            Exp.Old(e.inner.linearize().toViperBuiltinType(ctx.forSpecification()), ctx.source.asPosition)
    }

    // endregion

    // region Field Access

    override fun visitPrimitiveFieldAccess(e: PrimitiveFieldAccess): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp =
            Exp.FieldAccess(e.inner.linearize().toViper(ctx), e.field.toViper(), ctx.source.asPosition)
    }

    override fun visitFieldAccess(e: FieldAccess): Linearizable = object : Linearizable {
        private val receiverLinearizable = e.receiver.linearize()

        override fun toViper(ctx: LinearizationContext): Exp {
            if (e.field.accessPolicy == AccessPolicy.ALWAYS_WRITEABLE) {
                return PrimitiveFieldAccess(e.receiver, e.field).linearize().toViper(ctx)
            }
            return ctx.addFieldAccess(receiverLinearizable, e.receiver.type, e.field, ctx.ownedReceiverPath(e.receiver, e.receiverOwned, isWrite = false))
        }

        override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) {
            val receiverPath = ctx.ownedReceiverPath(e.receiver, e.receiverOwned, isWrite = false)
            ctx.addFieldAccessStoringIn(receiverLinearizable, e.receiver.type, e.field, result, receiverPath)
        }

        override fun toViperMaybeStoringIn(result: VariableEmbedding?, ctx: LinearizationContext) {
            if (result != null) toViperStoringIn(result, ctx)
            else toViperUnusedResult(ctx)
        }

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
            defaultToViperBuiltinType(::toViper, e.type, e.sourceRole, ctx)

        override fun toViperUnusedResult(ctx: LinearizationContext) {
            receiverLinearizable.toViperUnusedResult(ctx)
        }
    }

    override fun visitUniqueValAccess(e: UniqueValAccess): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp =
            ctx.addUniqueValAccess(e.receiver.linearize(), e.receiver.type, e.step, e.receiverOwned)
    }

    override fun visitFieldModification(e: FieldModification): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            val receiverPath = ctx.ownedReceiverPath(e.receiver, e.receiverOwned, isWrite = true)
            if (e.dropsWrite) {
                e.receiver.linearize().toViperUnusedResult(ctx)
                val newValue = runUpdatesOfPath(e.newValue, ctx)
                val source = ctx.moveSource(newValue, targetOwned = false)
                newValue.linearize().toViperUnusedResult(ctx)
                ctx.finishMove(newValue, source, targetOwned = false, target = null)
                return
            }
            val receiverViper = e.receiver.linearize().toViper(ctx)
            val targetOwned = receiverPath != null && e.field.isUnique
            val newValue = runUpdatesOfPath(e.newValue, ctx)
            val source = ctx.moveSource(newValue, targetOwned)
            val newValueViper = newValue.linearize().toViper(ctx)
            // The value is evaluated before the target is opened, since control flow in it may fold the target again.
            receiverPath?.let { ctx.foldState?.open(ctx, it, e.field) }
            ctx.addStatement {
                Stmt.FieldAssign(
                    Exp.FieldAccess(receiverViper, e.field.toViper()),
                    newValueViper,
                    ctx.source.asPosition
                )
            }
            ctx.finishMove(newValue, source, targetOwned, receiverPath?.plus(e.field))
        }
    }

    override fun visitFieldAccessPermissions(e: FieldAccessPermissions): Linearizable = object : OnlyToBuiltinLinearizable(e, this@LinearizationVisitor) {
        override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
            e.inner.linearize().toViper(ctx).fieldAccessPredicate(e.field.toViper(), e.perm, ctx.source.asPosition)
    }

    override fun visitPredicateAccessPermissions(e: PredicateAccessPermissions): Linearizable = object : OnlyToBuiltinLinearizable(e, this@LinearizationVisitor) {
        override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
            Exp.PredicateAccess(
                e.predicateName, e.args.map { it.linearize().toViper(ctx) }, e.perm, ctx.source.asPosition, e.sourceRole.asInfo,
            )
    }

    override fun visitUnfold(e: Unfold): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            ctx.addStatement {
                Stmt.Unfold(e.pred.linearize().toViperBuiltinType(ctx) as Exp.PredicateAccess)
            }
        }
    }

    override fun visitFold(e: Fold): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            ctx.addStatement {
                Stmt.Fold(e.pred.linearize().toViperBuiltinType(ctx) as Exp.PredicateAccess)
            }
        }
    }

    override fun visitGenericArrayBulkUpdate(e: GenericArrayBulkUpdate): Linearizable =
        object : DirectResultLinearizable(e, this@LinearizationVisitor) {
            override fun toViper(ctx: LinearizationContext): Exp {
                val pos = ctx.source.asPosition
                val expressions = e.arguments.map { it.second }
                val array = snapshotBefore(e.array.linearize().toViper(ctx), e.array.type, expressions, ctx)
                val values = e.arguments.mapIndexed { i, (name, expression) ->
                    name to snapshotBefore(expression.linearize().toViper(ctx), expression.type, expressions.drop(i + 1), ctx)
                }.toMap()
                fun index(name: String, default: Exp) = values[name]?.let { RuntimeTypeDomain.intInjection.fromRef(it) } ?: default
                val from = index("startIndex", index("fromIndex", Exp.IntLit(0)))
                val to = index("endIndex", index("toIndex", GenericArrayEmbedding.arraySize(array, pos)))
                fun check(condition: Exp) = ctx.addStatement {
                    Stmt.Assert(condition, pos, SourceRole.Proof(org.jetbrains.kotlin.formver.common.KotlinCondition.ProofClause.ASSERTION).asInfo)
                }
                check(from ge Exp.IntLit(0))
                check(from le to)
                check(to le GenericArrayEmbedding.arraySize(array, pos))
                val receiverPath = ctx.ownedReceiverPath(e.array, receiverOwned = true, isWrite = e.kind == GenericArrayBulk.Fill)!!
                ctx.foldState?.openOwn(ctx, receiverPath)
                val contents = array.fieldAccess(GenericArrayEmbedding.contentsField, pos)
                val count = Exp.Sub(to, from, pos)
                val target: Exp.FieldAccess
                val offset: Exp
                val replacement: Exp
                val source: OwnedPath?
                val destination: Exp
                var destinationPath: OwnedPath? = null
                if (e.kind == GenericArrayBulk.CopyInto) {
                    destination = values.getValue("destination")
                    offset = index("destinationOffset", Exp.IntLit(0))
                    val size = GenericArrayEmbedding.arraySize(destination, pos)
                    check(offset ge Exp.IntLit(0))
                    check(offset le size)
                    check(count le Exp.Sub(size, offset, pos))
                    destinationPath = ctx.ownedReceiverPath(e.destination, receiverOwned = true, isWrite = true)!!
                    ctx.foldState?.openOwn(ctx, destinationPath)
                    target = destination.fieldAccess(GenericArrayEmbedding.contentsField, pos)
                    replacement = Exp.SeqTake(Exp.SeqDrop(contents, from, pos), count, pos)
                    source = null
                } else {
                    destination = array
                    offset = from
                    target = contents
                    val value = e.arguments.single { it.first == "element" }.second
                    source = ctx.moveSource(value, targetOwned = false)
                    replacement = GenericArrayEmbedding.repeatFunction.toFuncApp(listOf(values.getValue("element"), count), pos)
                }
                val updated = Exp.SeqAppend(Exp.SeqAppend(Exp.SeqTake(target, offset, pos), replacement, pos),
                    Exp.SeqDrop(target, Exp.Add(offset, count, pos), pos), pos)
                ctx.addStatement { Stmt.FieldAssign(target, updated, pos) }
                ctx.foldState?.close(ctx, receiverPath)
                destinationPath?.let { ctx.foldState?.close(ctx, it) }
                if (e.kind == GenericArrayBulk.Fill)
                    ctx.finishMove(e.arguments.single { it.first == "element" }.second, source, targetOwned = false, target = null)
                return if (e.kind == GenericArrayBulk.CopyInto) destination else RuntimeTypeDomain.unitValue(pos = pos)
            }

            override fun toViperUnusedResult(ctx: LinearizationContext) { toViper(ctx) }
        }

    override fun visitGenericArraySize(e: GenericArraySize): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp = RuntimeTypeDomain.intInjection.toRef(
            GenericArrayEmbedding.arraySize(e.array.withoutOwnerUnfoldings().linearize().toViper(ctx), ctx.source.asPosition),
            pos = ctx.source.asPosition,
        )
    }

    override fun visitGenericArrayAllNull(e: GenericArrayAllNull): Linearizable = object : OnlyToBuiltinLinearizable(e, this@LinearizationVisitor) {
        override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
            GenericArrayEmbedding.allNull(e.array.linearize().toViper(ctx), ctx.source.asPosition)
    }

    override fun visitGenericArrayGet(e: GenericArrayGet): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            val value = toViper(ctx)
            if (ctx is PureExpLinearizer) ctx.evaluateUnused(value)
        }

        override fun toViper(ctx: LinearizationContext): Exp {
            val array = snapshotBefore(e.array.linearize().toViper(ctx), e.array.type, listOf(e.index), ctx)
            val index = e.index.linearize().toViperBuiltinType(ctx)
            val path = ctx.ownedReceiverPath(e.array, e.receiverOwned, isWrite = false)
            if (ctx.logicOperatorPolicy == LogicOperatorPolicy.CONVERT_TO_IF)
                ctx.assertInBounds(array, index, e.arraySymbol, generic = true)
            return ctx.addOwnedRead(GenericArrayEmbedding.uniquePredicateAccess(array, ctx.source.asPosition),
                GenericArrayEmbedding.element(array, index, ctx.source.asPosition), e.type, path, valueIsReference = true)
        }
    }

    override fun visitGenericArraySet(e: GenericArraySet): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            val path = ctx.ownedReceiverPath(e.array, e.receiverOwned, isWrite = true)
            val array = snapshotBefore(e.array.linearize().toViper(ctx), e.array.type, listOf(e.index, e.value), ctx)
            val index = atomicIndex(e.index, ctx, e.value)
            val source = ctx.moveSource(e.value, targetOwned = false)
            val value = e.value.linearize().toViper(ctx)
            ctx.assertInBounds(array, index, e.arraySymbol, generic = true)
            ctx.finishMove(e.value, source, targetOwned = false, target = null)
            if (path == null) return
            ctx.foldState?.openOwn(ctx, path)
            val contents = array.fieldAccess(GenericArrayEmbedding.contentsField, ctx.source.asPosition)
            ctx.addStatement {
                Stmt.FieldAssign(contents, Exp.SeqUpdate(contents, index, value, ctx.source.asPosition), ctx.source.asPosition)
            }
        }
    }

    override fun visitIntArraySize(e: IntArraySize): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp = RuntimeTypeDomain.intInjection.toRef(
            IntArrayEmbedding.arraySize(e.array.withoutOwnerUnfoldings().linearize().toViper(ctx), ctx.source.asPosition),
            pos = ctx.source.asPosition,
        )
    }

    /**
     * [this] with each trailing `@Unique` `val` read treated as unowned, so that it is not wrapped in the owner's
     * `unfolding`: the getter is heap-independent, and so is a consumer such as `arraySize` that reads no further.
     * Casts and meta nodes above such a read are dropped, since only its value is used.
     */
    private fun ExpEmbedding.withoutOwnerUnfoldings(): ExpEmbedding = when (val exp = ignoringCastsAndMetaNodes()) {
        is UniqueValAccess -> exp.copy(receiver = exp.receiver.withoutOwnerUnfoldings(), receiverOwned = false)
        else -> this
    }

    override fun visitIntArrayAllZero(e: IntArrayAllZero): Linearizable = object : OnlyToBuiltinLinearizable(e, this@LinearizationVisitor) {
        override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
            IntArrayEmbedding.allZero(e.array.linearize().toViper(ctx), ctx.source.asPosition)
    }

    override fun visitIntArrayGet(e: IntArrayGet): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            val value = toViper(ctx)
            if (ctx is PureExpLinearizer) ctx.evaluateUnused(value)
        }

        override fun toViper(ctx: LinearizationContext): Exp {
            val array = snapshotBefore(e.array.linearize().toViper(ctx), e.array.type, listOf(e.index), ctx)
            val index = e.index.linearize().toViperBuiltinType(ctx)
            val arrayPath = ctx.ownedReceiverPath(e.array, e.receiverOwned, isWrite = false)
            if (ctx.logicOperatorPolicy == LogicOperatorPolicy.CONVERT_TO_IF) ctx.assertInBounds(array, index, e.arraySymbol)
            val pos = ctx.source.asPosition
            return ctx.addOwnedRead(IntArrayEmbedding.uniquePredicateAccess(array, pos), IntArrayEmbedding.element(array, index, pos), e.type, arrayPath)
        }
    }

    override fun visitCollectionListAllocation(e: CollectionListAllocation): Linearizable =
        object : DirectResultLinearizable(e, this@LinearizationVisitor) {
            override fun toViper(ctx: LinearizationContext): Exp {
                val result = e.result.toLocalVarUse()
                val cls = e.type.pretype as ClassTypeEmbedding
                ctx.addDeclaration(e.result.toLocalVarDecl(ctx.source.asPosition))
                ctx.addStatement { Stmt.New(result, emptyList(), ctx.source.asPosition) }
                ctx.addStatement {
                    Stmt.Inhale(Exp.PredicateAccess(cls.uniquePredicateName, listOf(result), PermExp.FullPerm()), ctx.source.asPosition)
                }
                ctx.foldState?.acquire(ctx, OwnedPath(e.result))
                e.sequenceBinding.linearize().toViperBuiltinType(ctx).let { binding ->
                    ctx.addStatement { Stmt.Inhale(binding, ctx.source.asPosition) }
                }
                return result
            }

            override fun toViperUnusedResult(ctx: LinearizationContext) {
                toViper(ctx)
            }
        }

    override fun visitIntArrayInit(e: IntArrayInit): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp {
            e.initialization.linearize().toViperUnusedResult(ctx)
            return e.array.linearize().toViper(ctx)
        }

        override fun toViperUnusedResult(ctx: LinearizationContext) {
            toViper(ctx)
        }
    }

    override fun visitIntArrayContents(e: IntArrayContents): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp {
            val array = e.array.linearize().toViper(ctx)
            val arrayPath = ctx.ownedReceiverPath(e.array, e.receiverOwned, isWrite = false)
            val pos = ctx.source.asPosition
            val contents = SeqMultisetDomain.toMultiset(array.fieldAccess(contentsField, pos), pos = pos)
            return ctx.addOwnedRead(IntArrayEmbedding.uniquePredicateAccess(array, pos), contents, e.type, arrayPath)
        }
    }

    override fun visitIterationSequenceContains(e: IterationSequenceContains): Linearizable =
        object : DirectResultLinearizable(e, this@LinearizationVisitor) {
            override fun toViper(ctx: LinearizationContext): Exp =
                RuntimeTypeDomain.boolInjection.toRef(toViperBuiltinType(ctx), pos = ctx.source.asPosition)

            override fun toViperBuiltinType(ctx: LinearizationContext): Exp {
                val reference = e.element.linearize().toViper(ctx)
                val injection = e.element.type.injectionOrNull
                // Expose the inverse term when comparing boxed primitive values in a Ref sequence.
                val element = injection?.let { it.toRef(it.fromRef(reference)) } ?: reference
                return Exp.SeqContains(element, e.sequence.linearize().toViperBuiltinType(ctx), ctx.source.asPosition)
            }
        }

    override fun visitIterationSequenceGet(e: IterationSequenceGet): Linearizable =
        object : DirectResultLinearizable(e, this@LinearizationVisitor) {
            override fun toViper(ctx: LinearizationContext): Exp =
                if (e.direct) Exp.SeqIndex(
                    e.sequence.linearize().toViperBuiltinType(ctx), e.index.linearize().toViperBuiltinType(ctx), ctx.source.asPosition,
                ) else org.jetbrains.kotlin.formver.core.domains.IterationSequenceElementFunction.toFuncApp(
                    listOf(e.sequence.linearize().toViper(ctx), e.index.linearize().toViper(ctx)),
                    ctx.source.asPosition,
                )
        }

    /**
     * In an expression, the unmatched path is a call whose precondition proves it unreachable. In a method body it is
     * closed by statements instead: assuming the guaranteed facts and asserting `false` also cuts the path off, so
     * that later code does not see it.
     */
    override fun visitUnmatchedWhen(e: UnmatchedWhen): Linearizable =
        object : DirectResultLinearizable(e, this@LinearizationVisitor) {
            private val proof = SourceRole.Proof(org.jetbrains.kotlin.formver.common.KotlinCondition.ProofClause.EXHAUSTIVE_WHEN).asInfo

            override fun toViper(ctx: LinearizationContext): Exp =
                ctx.typeResolver.unmatchedWhen(e.type).toFuncApp(
                    listOf(e.guaranteed.linearize().toViperBuiltinType(ctx)), ctx.source.asPosition, proof,
                )

            override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) = toViperUnusedResult(ctx)

            override fun toViperUnusedResult(ctx: LinearizationContext) {
                val guaranteed = e.guaranteed.linearize().toViperBuiltinType(ctx)
                ctx.addStatement { Stmt.Inhale(guaranteed, ctx.source.asPosition) }
                ctx.addStatement { Stmt.Assert(Exp.BoolLit(false, ctx.source.asPosition), ctx.source.asPosition, proof) }
            }
        }

    override fun visitIterationSequenceOf(e: IterationSequenceOf): Linearizable =
        object : DirectResultLinearizable(e, this@LinearizationVisitor) {
            override fun toViper(ctx: LinearizationContext): Exp =
                RuntimeTypeDomain.iterationSequenceInjection.toRef(toViperBuiltinType(ctx), pos = ctx.source.asPosition)

            override fun toViperBuiltinType(ctx: LinearizationContext): Exp {
                val pos = ctx.source.asPosition
                val elements = e.elements.map { it.linearize().toViper(ctx) }
                return if (elements.isEmpty()) Exp.EmptySeq(Type.Ref, pos) else Exp.ExplicitSeq(elements, pos)
            }
        }

    override fun visitMultisetOf(e: MultisetOf): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp =
            RuntimeTypeDomain.multisetInjection.toRef(toViperBuiltinType(ctx), pos = ctx.source.asPosition)

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp {
            val pos = ctx.source.asPosition
            val elements = e.elements.map { it.linearize().toViperBuiltinType(ctx) }
            return if (elements.isEmpty()) Exp.EmptyMultiset(Type.Int, pos) else Exp.ExplicitMultiset(elements, pos)
        }
    }

    override fun visitIntArraySet(e: IntArraySet): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            val arrayPath = ctx.ownedReceiverPath(e.array, e.receiverOwned, isWrite = true)
            val array = snapshotBefore(e.array.linearize().toViper(ctx), e.array.type, listOf(e.index, e.value), ctx)
            val index = atomicIndex(e.index, ctx, e.value)
            val value = e.value.linearize().toViperBuiltinType(ctx)
            ctx.assertInBounds(array, index, e.arraySymbol)
            if (arrayPath == null) return
            ctx.foldState?.openOwn(ctx, arrayPath)
            val contents = array.fieldAccess(contentsField, ctx.source.asPosition)
            ctx.addStatement {
                Stmt.FieldAssign(contents, Exp.SeqUpdate(contents, index, value, ctx.source.asPosition), ctx.source.asPosition)
            }
        }
    }

    /**
     * [index] as a Viper `Int`, first stored in a fresh variable unless it is a literal or linearizes to a variable.
     * Silicon relates the contents of an updated sequence, such as its `toMultiset`, to reads at the updated index only
     * when that index is atomic: with `s[j + 1 := v]` it does not re-establish the contents invariant of an in-place
     * insertion sort.
     */
    private fun atomicIndex(index: ExpEmbedding, ctx: LinearizationContext, later: ExpEmbedding): Exp {
        if (index.ignoringMetaNodes() is LiteralEmbedding) return index.linearize().toViperBuiltinType(ctx)
        val ref = snapshotBefore(index.linearize().toViper(ctx), index.type, listOf(later), ctx)
        if (ref is Exp.LocalVar) return RuntimeTypeDomain.intInjection.fromRef(ref)
        val variable = ctx.freshAnonVar { int() }
        ctx.addStatement { Stmt.assign(variable.toViperExp(this), ref, source.asPosition) }
        return RuntimeTypeDomain.intInjection.fromRef(variable.toViperExp(ctx))
    }

    /** Preserve an evaluated operand when a later operand assigns the local that supplied it. */
    private fun snapshotBefore(value: Exp, type: TypeEmbedding, later: List<ExpEmbedding>, ctx: LinearizationContext): Exp {
        if (value !is Exp.LocalVar || later.none { exp -> exp.subtree().any { it is Assign && it.lhs.name == value.name } }) {
            return value
        }
        val saved = ctx.freshAnonVar(type)
        ctx.addStatement { Stmt.assign(saved.toViperExp(this), value, source.asPosition) }
        return saved.toViperExp(ctx)
    }

    override fun visitStringBuilderLength(e: StringBuilderLength): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp =
            readStringBuilder(e, ctx) { contents -> Exp.SeqLength(contents, ctx.source.asPosition) }
    }

    override fun visitStringBuilderToString(e: StringBuilderToString): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp = readStringBuilder(e, ctx) { contents -> contents }
    }

    override fun visitStringBuilderAppend(e: StringBuilderAppend): Linearizable = object : StringBuilderUpdateLinearizable(e) {
        override fun toViper(ctx: LinearizationContext): Exp {
            val builder = e.builder.linearize().toViper(ctx)
            val value = e.value.linearize().toViperBuiltinType(ctx)
            val appended = if (e.value.type.pretype == CharTypeEmbedding) Exp.ExplicitSeq(listOf(value), ctx.source.asPosition) else value
            ctx.updateStringBuilder(e, builder, { e.exceptionalExit.linearize().toViperUnusedResult(this) }) { contents ->
                Exp.SeqAppend(contents, appended, ctx.source.asPosition)
            }
            return builder
        }
    }

    override fun visitStringBuilderClear(e: StringBuilderClear): Linearizable = object : StringBuilderUpdateLinearizable(e) {
        override fun toViper(ctx: LinearizationContext): Exp {
            val builder = e.builder.linearize().toViper(ctx)
            ctx.updateStringBuilder(e, builder) { Exp.EmptySeq(Type.Int, ctx.source.asPosition) }
            return builder
        }
    }

    /** An update returns its receiver, and runs whether or not its result is used. */
    private abstract inner class StringBuilderUpdateLinearizable(e: StringBuilderUpdate) : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            toViper(ctx)
        }
    }

    /**
     * Runs the `StringBuilder` or `copyInto` updates that [value] consists of when they act on a path, the initialization of an
     * [IntArrayInit], an [InlineCall] whose callee returns `@Unique`, or a conditional or block that yields a fresh
     * unique value on every path, and returns the path, the call's result or the temporary that each path moves its
     * value into, which then moves in place of [value]. Any other [value] is returned unchanged. A move folds its
     * source before the value is evaluated, which would close the path before the updates open it again, and the
     * result of an inline call is held only once the call has run.
     */
    private fun runUpdatesOfPath(value: ExpEmbedding, ctx: LinearizationContext): ExpEmbedding {
        var root = value.ignoringCastsAndMetaNodes()
        if ((root is If || root is Block) && root.yieldsFreshUnique()) {
            val temporary = ctx.freshAnonVar(value.type)
            root.movedInto(temporary).linearize().toViperUnusedResult(ctx)
            return temporary
        }
        if (root is GenericArrayBulkUpdate && root.kind == GenericArrayBulk.CopyInto) {
            value.linearize().toViperUnusedResult(ctx)
            val path = root.destination.ownedPath()!!
            return path.fields.fold<org.jetbrains.kotlin.formver.core.embeddings.properties.PathStep, ExpEmbedding>(path.root) { receiver, step ->
                step.valueOf(receiver)
            }
        }
        if (root is IntArrayInit) {
            value.linearize().toViperUnusedResult(ctx)
            return root.array
        }
        if (root is CollectionListAllocation) {
            value.linearize().toViperUnusedResult(ctx)
            return root.result
        }
        if (root is InlineCall && root.returnsUnique) {
            value.linearize().toViperUnusedResult(ctx)
            return root.result
        }
        if (root !is StringBuilderUpdate || root.ownedPath() == null) return value
        root.linearize().toViperUnusedResult(ctx)
        while (root is StringBuilderUpdate) root = root.builder.ignoringCastsAndMetaNodes()
        return root
    }

    /** The value [read] makes of the contents of [e]'s builder, under the builder's unique predicate. */
    private fun readStringBuilder(e: StringBuilderOperation, ctx: LinearizationContext, read: (contents: Exp) -> Exp): Exp {
        val builder = e.builder.linearize().toViper(ctx)
        val builderPath = ctx.ownedReceiverPath(e.builder, e.receiverOwned, isWrite = false)
        val pos = ctx.source.asPosition
        return ctx.addOwnedRead(
            StringBuilderEmbedding.uniquePredicateAccess(builder, pos),
            read(StringBuilderEmbedding.contents(builder, pos)),
            e.type,
            builderPath,
        )
    }

    // endregion

    // region Assignment / Declaration

    override fun visitAssign(e: Assign): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            val rhs = runUpdatesOfPath(e.rhs, ctx)
            val source = ctx.moveSource(rhs, e.targetOwned)
            rhs.linearize().toViperStoringIn(LinearizationVariableEmbedding(e.lhs.name, e.lhs.type), ctx)
            ctx.finishMove(rhs, source, e.targetOwned, OwnedPath(e.lhs))
        }
    }

    override fun visitDeclare(e: Declare): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            ctx.addDeclaration(e.variable.toLocalVarDecl(ctx.source.asPosition))
            val initializer = e.initializer?.let { runUpdatesOfPath(it, ctx) } ?: return
            val source = ctx.moveSource(initializer, e.targetOwned)
            initializer.linearize().toViperStoringIn(LinearizationVariableEmbedding(e.variable.name, e.variable.type), ctx)
            ctx.finishMove(initializer, source, e.targetOwned, OwnedPath(e.variable))
        }
    }

    // endregion

    // region ForAll / Acc

    override fun visitForAllEmbedding(e: ForAllEmbedding): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp =
            RuntimeTypeDomain.boolInjection.toRef(toViperBuiltinType(ctx), pos = ctx.source.asPosition)

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp {
            val (conjunction, viperTriggers) = getQuantifierParts(e.variable, e.conditions, e.triggerExpressions, ctx)
            return Exp.Forall(
                variables = listOf(e.variable.toLocalVarDecl()),
                triggers = viperTriggers,
                exp = quantifierDomain(e.variable, ctx)?.let { Exp.Implies(it, conjunction) } ?: conjunction,
                pos = ctx.source.asPosition,
                info = e.sourceRole.asInfo,
            )
        }
    }

    override fun visitExistsEmbedding(e: ExistsEmbedding): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp =
            RuntimeTypeDomain.boolInjection.toRef(toViperBuiltinType(ctx), pos = ctx.source.asPosition)

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp {
            val (conjunction, viperTriggers) = getQuantifierParts(e.variable, e.conditions, e.triggerExpressions, ctx)
            return Exp.Exists(
                variables = listOf(e.variable.toLocalVarDecl()),
                triggers = viperTriggers,
                exp = quantifierDomain(e.variable, ctx)?.let { Exp.And(it, conjunction) } ?: conjunction,
                pos = ctx.source.asPosition,
                info = e.sourceRole.asInfo,
            )
        }
    }

    private fun quantifierDomain(variable: VariableEmbedding, ctx: LinearizationContext): Exp? = when {
        variable.isOriginallyRef -> variable.toViperExp(ctx).isOf(variable.type.runtimeType)
        variable.type.pretype == StringTypeEmbedding -> IntegerBounds.string(variable.toLocalVarUse(ctx.source.asPosition))
        else -> IntegerKind.entries.firstOrNull { it.pretype == variable.type.pretype }?.let {
            // Membership provides a matchable injection term; range constrains the native variable.
            Exp.And(
                it.range(variable.toLocalVarUse(ctx.source.asPosition)),
                variable.toViperExp(ctx).isOf(variable.type.runtimeType),
            )
        }
    }

    override fun visitAccEmbedding(e: AccEmbedding): Linearizable = object : OnlyToBuiltinLinearizable(e, this@LinearizationVisitor) {
        override fun toViperBuiltinType(ctx: LinearizationContext): Exp {
            val field = Exp.FieldAccess(
                e.receiver.linearize().toViper(ctx),
                e.field.toViper(),
                ctx.source.asPosition,
            )
            return Exp.Acc(
                field = field,
                perm = e.perm,
                pos = ctx.source.asPosition,
                info = e.sourceRole.asInfo,
            )
        }
    }

    override fun visitPermissionLit(e: PermissionLit): Linearizable =
        error("PermissionLit should not be linearized; it is consumed directly by `acc` argument handling")

    // endregion

    // region Meta / Sharing

    override fun visitWithPosition(e: WithPosition): Linearizable {
        val innerLinearizable = attributingFailuresTo(e.source) { e.inner.accept(copy(source = e.source)) }
        fun <R> LinearizationContext.positioned(action: LinearizationContext.() -> R): R =
            attributingFailuresTo(e.source) { withPosition(e.source, action) }
        fun Exp.annotated(): Exp = e.sourceRole?.let { Exp.WithInfo(this, it.asInfo) } ?: this
        return object : Linearizable {
            override fun toViper(ctx: LinearizationContext): Exp =
                ctx.positioned { innerLinearizable.toViper(this).annotated() }

            override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) {
                ctx.positioned { innerLinearizable.toViperStoringIn(result, this) }
            }

            override fun toViperMaybeStoringIn(result: VariableEmbedding?, ctx: LinearizationContext) {
                ctx.positioned { innerLinearizable.toViperMaybeStoringIn(result, this) }
            }

            override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
                ctx.positioned { innerLinearizable.toViperBuiltinType(this).annotated() }

            override fun toViperBuiltinTypeAs(type: TypeEmbedding, ctx: LinearizationContext): Exp =
                ctx.positioned { innerLinearizable.toViperBuiltinTypeAs(type, this).annotated() }

            override fun toViperUnusedResult(ctx: LinearizationContext) {
                ctx.positioned { innerLinearizable.toViperUnusedResult(this) }
            }
        }
    }

    override fun visitSharingContext(e: SharingContext): Linearizable = object : Linearizable {
        override fun toViper(ctx: LinearizationContext): Exp =
            e.inner.linearize().toViper(ctx).also { e.sharedExp = null }

        override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) {
            e.inner.linearize().toViperStoringIn(result, ctx)
            e.sharedExp = null
        }

        override fun toViperMaybeStoringIn(result: VariableEmbedding?, ctx: LinearizationContext) {
            e.inner.linearize().toViperMaybeStoringIn(result, ctx)
            e.sharedExp = null
        }

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
            e.inner.linearize().toViperBuiltinType(ctx).also { e.sharedExp = null }

        override fun toViperUnusedResult(ctx: LinearizationContext) {
            e.inner.linearize().toViperUnusedResult(ctx)
            e.sharedExp = null
        }
    }

    override fun visitShared(e: Shared): Linearizable = object : StoredResultLinearizable(e) {
        override fun toViper(ctx: LinearizationContext): Exp =
            e.context.tryInitShared { e.inner.linearize().toViper(ctx) }

        override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) {
            e.context.tryInitShared { e.inner.linearize().toViperStoringIn(result, ctx); result.toViperExp(ctx) }
        }

        override fun toViperUnusedResult(ctx: LinearizationContext) {
            e.context.tryInitShared {
                e.inner.linearize().toViperUnusedResult(ctx); UnitLit.pureToViper(toBuiltin = false, ctx.typeResolver)
            }
        }
    }

    // endregion

    // region Lambda

    // A lambda that is invoked in place is inlined at its invocation and never linearized as a value.
    override fun visitLambdaExp(e: LambdaExp): Linearizable = object : StoredResultLinearizable(e) {
        override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) {
            throw UnsupportedFeatureException(e.function.source, "a lambda that is not invoked in place")
        }
    }

    // endregion
}

private fun <R> LinearizationContext.withFoldStateRestored(action: LinearizationContext.() -> R): R {
    val state = foldState ?: return action()
    val entry = state.snapshot()
    val result = action()
    state.restore(state.join(this, state.snapshot(), entry))
    return result
}

/**
 * Fails when one of [invariants] reads through a root that one of [shapes] holds open around a hole: such a read
 * needs the root's predicate folded.
 */
private fun LinearizationContext.requireFoldedReads(invariants: List<ExpEmbedding>, shapes: List<OwnedShape>) {
    val open = shapes.filter { it.holes.isNotEmpty() }.associateBy { it.root.name }
    if (open.isEmpty()) return
    fun ExpEmbedding.openRead(): OwnedPath? = when (this) {
        is FieldAccess -> receiver.ownedPath()?.takeIf { receiverOwned && it.root.name in open }
        is UniqueValAccess -> receiver.ownedPath()?.takeIf { receiverOwned && it.root.name in open }
        is FunctionCall -> args.zip(function.formalArgs).firstNotNullOfOrNull { (arg, formal) ->
            arg.ownedPath()?.takeIf { formal.isUnique && it.root.name in open }
        }
        else -> null
    } ?: children().firstNotNullOfOrNull { it.openRead() }
    val read = invariants.firstNotNullOfOrNull { it.openRead() } ?: return
    val shape = open.getValue(read.root.name)
    val hole = OwnedPath(shape.root, shape.holes.first())
    throw FoldStateException(
        source,
        FoldOwnershipCondition(FoldOwnershipRule.LOOP_INVARIANT_HOLE, read.toConversionPath(), hole.toConversionPath()),
    )
}

/**
 * The tracked path of an owned [receiver], or `null` when the access havocs or drops.
 *
 * An owned receiver the fold state cannot name, one that is not a variable followed by `@Unique` properties, is read
 * with a havoc: the value read is unconstrained, so this is sound. A write through it would leave the value its
 * predicate holds stale, so it is an error.
 */
private fun LinearizationContext.ownedReceiverPath(
    receiver: ExpEmbedding,
    receiverOwned: Boolean,
    isWrite: Boolean,
): OwnedPath? {
    if (!receiverOwned || foldState == null) return null
    if (with(typeResolver) { (receiver.type.pretype as? ClassTypeEmbedding)?.isManual } != false) return null
    val path = receiver.ownedPath()
    if (path == null && isWrite) {
        throw FoldStateException(source,
            FoldOwnershipCondition(FoldOwnershipRule.UNTRACKED_WRITE_RECEIVER, writeSite = source))
    }
    return path
}

/**
 * Sets the contents of [builder], which [e] updates, to [newContents] of the old contents. An update of a builder that
 * is not owned is dropped.
 */
private fun LinearizationContext.updateStringBuilder(
    e: StringBuilderUpdate,
    builder: Exp,
    exceptionalExit: (LinearizationContext.() -> Unit)? = null,
    newContents: (contents: Exp) -> Exp,
) {
    val builderPath = ownedReceiverPath(e.builder, e.receiverOwned, isWrite = true) ?: return
    foldState?.openOwn(this, builderPath)
    val contents = StringBuilderEmbedding.contents(builder, source.asPosition)
    val updated = newContents(contents)
    if (exceptionalExit != null) {
        val fits = Exp.LeCmp(Exp.SeqLength(updated), Exp.IntLit(Int.MAX_VALUE))
        val abnormal = asBlock(exceptionalExit)
        addStatement { Stmt.If(Exp.Not(fits), abnormal, null, source.asPosition) }
    }
    addStatement { Stmt.FieldAssign(contents, updated, source.asPosition) }
}

/** Assert that [index] is a valid index into [array], reporting a failure as an array bounds error. */
private fun LinearizationContext.assertInBounds(array: Exp, index: Exp, arraySymbol: FirBasedSymbol<*>?, generic: Boolean = false) {
    val pos = source.asPosition
    fun role(bound: SourceRole.ArrayElementAccessCheck.Bound) = SourceRole.ArrayElementAccessCheck(bound, arraySymbol).asInfo
    addStatement { Stmt.Assert(Exp.GeCmp(index, Exp.IntLit(0), pos, role(SourceRole.ArrayElementAccessCheck.Bound.NEGATIVE)), pos) }
    addStatement {
        Stmt.Assert(Exp.LtCmp(index, if (generic) GenericArrayEmbedding.arraySize(array, pos) else IntArrayEmbedding.arraySize(array, pos), pos, role(SourceRole.ArrayElementAccessCheck.Bound.NOT_BELOW_SIZE)), pos)
    }
}

/** The arguments that are held paths, with the formal parameter each is passed to. */
private fun LinearizationContext.heldPaths(
    args: List<ExpEmbedding>,
    formals: List<VariableEmbedding>,
): List<Pair<OwnedPath, VariableEmbedding>> {
    val state = foldState ?: return emptyList()
    return args.zip(formals).mapNotNull { (arg, formal) ->
        arg.ownedPath()?.takeIf { state.holds(it) }?.let { it to formal }
    }
}

/** Gives the body of an inline call what [binding] holds for it on entry. */
private fun LinearizationContext.enterBinding(binding: RootBinding, state: FoldState) {
    val path = OwnedPath(binding.variable)
    when (binding.mode) {
        BindingMode.Borrowed -> {
            val retained = binding.retainedPath
            if (binding.familyUpcast && retained != null) state.transfer(this, retained, path)
            else exposeFor(path, binding.formal)
        }
        BindingMode.Consumed -> exposeFor(path, binding.formal)
        BindingMode.Released -> state.release(this, path)
    }
}

/**
 * Takes back from the body of an inline call what [binding] held for it, on exit. A borrowed path with fields hands
 * its predicate back from the temporary that held it, which needs the path to be empty still.
 */
private fun LinearizationContext.exitBinding(binding: RootBinding, state: FoldState) {
    val path = OwnedPath(binding.variable)
    when (binding.mode) {
        BindingMode.Consumed -> state.release(this, path)
        BindingMode.Borrowed -> {
            state.close(this, path)
            val retained = binding.retainedPath ?: return
            if (state.holds(retained)) {
                throw FoldStateException(
                    source,
                    FoldOwnershipCondition(FoldOwnershipRule.BORROWED_ASSIGNMENT, retained.toConversionPath()),
                )
            }
            if (binding.familyUpcast) {
                val own = state.trackedClass(retained.fields.lastOrNull()?.type ?: retained.root.type)!!
                val family = state.trackedClass(binding.formal.type)!!
                state.restoreFamily(this, own, family, path)
                state.release(this, path)
                state.acquire(this, retained)
            } else state.transfer(this, path, retained)
        }
        BindingMode.Released -> {}
    }
}

/** Transfer fresh concrete arguments into the callee's ownership presentation, recording loans. */
private fun LinearizationContext.coerceFreshArguments(
    args: List<ExpEmbedding>,
    formals: List<VariableEmbedding>,
    values: List<Exp>,
): List<Triple<Int, ClassTypeEmbedding, ClassTypeEmbedding>> {
    if (foldState == null) return emptyList()
    val loans = mutableListOf<Triple<Int, ClassTypeEmbedding, ClassTypeEmbedding>>()
    args.zip(formals).forEachIndexed { index, (arg, formal) ->
        if (!formal.isUnique || !arg.isFreshUnique()) return@forEachIndexed
        val from = arg.ignoringCastsAndMetaNodes().type.pretype as? ClassTypeEmbedding ?: return@forEachIndexed
        val to = formal.type.pretype as? ClassTypeEmbedding ?: return@forEachIndexed
        if (from == to || !typeResolver.isFamilyView(to)) return@forEachIndexed
        if (!typeResolver.isSubtype(from, to)) throw FoldStateException(source, FoldOwnershipCondition(FoldOwnershipRule.DOWNCAST, typeName = to.name.sourceSpelling))
        val method = typeResolver.familyTransfer(from, to).method
        addFamilyCall(values[index], arg.type.isNullable, method)
        if (formal.isBorrowed) loans += Triple(index, from, to)
    }
    return loans
}

private fun LinearizationContext.addFamilyCall(value: Exp, nullable: Boolean, method: Method) {
    val call = method.toMethodCall(listOf(value), emptyList(), source.asPosition)
    addStatement {
        if (nullable) Stmt.If(
            Exp.NeCmp(value, RuntimeTypeDomain.nullValue(pos = source.asPosition), source.asPosition),
            Stmt.Seqn(listOf(call)), Stmt.Seqn(), source.asPosition,
        ) else call
    }
}

/** Make the folded predicate that the `@Unique` parameter [formal] takes held for the argument [path]. */
private fun LinearizationContext.exposeFor(path: OwnedPath, formal: VariableEmbedding) {
    val state = foldState ?: return
    val cls = state.trackedClass(formal.type) ?: return state.close(this, path)
    state.expose(this, path, cls)
}

/**
 * Fold the predicates of [cls] and its superclasses for [obj], which a constructor returned open, the topmost first.
 * Folding takes the predicates of the arguments the constructor stored in `@Unique` properties.
 */
private fun LinearizationContext.foldConstructed(obj: VariableEmbedding, cls: ClassTypeEmbedding) {
    val exp = obj.toViperExp(this)
    val info = SourceRole.Ownership("`${cls.name.sourceSpelling}`", SourceRole.Ownership.Site.Construction).asInfo
    for (chainClass in generateSequence(cls, typeResolver::superClass).toList().asReversed()) {
        addStatement { Stmt.Fold(hierarchyPredicateAccess(exp, chainClass, source, info), source.asPosition, info) }
    }
}

/** Whether [this] produces a value whose unique predicate the caller receives. */
private fun ExpEmbedding.isFreshUnique(): Boolean = when (val exp = ignoringCastsAndMetaNodes()) {
    is MethodCall -> exp.method.callableType.returnsUnique
    is FunctionCall -> exp.function.callableType.returnsUnique
    is NullLit, is CollectionListAllocation -> true
    else -> false
}

/**
 * Whether [this] yields a fresh unique value on every path: a fresh unique value itself, the result of an inline call
 * whose callee returns `@Unique`, or a block or conditional ending in such values.
 */
private fun ExpEmbedding.yieldsFreshUnique(): Boolean = when (val exp = ignoringCastsAndMetaNodes()) {
    is If -> exp.thenBranch.yieldsFreshUnique() && exp.elseBranch.yieldsFreshUnique()
    is Block -> exp.exps.lastOrNull()?.yieldsFreshUnique() ?: false
    is InlineCall -> exp.returnsUnique
    else -> exp.isFreshUnique()
}

/** [this], which [yieldsFreshUnique], with each value it yields moved into [target]. */
private fun ExpEmbedding.movedInto(target: VariableEmbedding): ExpEmbedding = when (val exp = ignoringCastsAndMetaNodes()) {
    is If -> If(exp.condition, exp.thenBranch.movedInto(target), exp.elseBranch.movedInto(target), buildType { unit() })
    is Block -> Block { addAll(exp.exps.dropLast(1)); add(exp.exps.last().movedInto(target)) }
    else -> Assign(target, this, targetOwned = true)
}

/**
 * Prepares a move out of [value] before it is linearized: when [value] is a held path, folds what can be folded under
 * it, keeping its holes, and returns it.
 * [targetOwned] is `null` when the store is not a move in the source.
 */
private fun LinearizationContext.moveSource(value: ExpEmbedding, targetOwned: Boolean?): OwnedPath? {
    val state = foldState ?: return null
    if (targetOwned == null) return null
    val path = value.ownedPath()?.takeIf { state.holds(it) } ?: return null
    if (targetOwned) state.tidy(this, path)
    return path
}

/**
 * Completes a move of [value] into [target] after the store. An owned target takes the predicate of [source] or of
 * a fresh unique value; otherwise the predicate of [source] is leaked.
 */
private fun LinearizationContext.finishMove(
    value: ExpEmbedding,
    source: OwnedPath?,
    targetOwned: Boolean?,
    target: OwnedPath?,
) {
    val state = foldState ?: return
    when {
        targetOwned != true || target == null -> source?.let { state.release(this, it) }
        source != null -> state.transfer(this, source, target)
        value.isFreshUnique() -> {
            val from = state.trackedClass(value.ignoringCastsAndMetaNodes().type)
            val to = state.trackedClass(target.fields.lastOrNull()?.type ?: target.root.type)
            val changesPresentation = from != to && to?.let(typeResolver::isFamilyView) == true
            if (from != null && to != null && changesPresentation) {
                state.transferFamily(this, from, to, target)
            }
            state.acquire(this, target)
        }
        else -> state.release(this, target)
    }
}
