/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.formver.core.embeddings.types.StringBuilderEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.CharTypeEmbedding
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.common.attributingFailuresTo
import org.jetbrains.kotlin.formver.core.asPosition
import org.jetbrains.kotlin.formver.core.conversion.constructedOpen
import org.jetbrains.kotlin.formver.core.conversion.AccessPolicy
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.domains.SeqMultisetDomain
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain.Companion.isOf
import org.jetbrains.kotlin.formver.core.embeddings.*
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.core.embeddings.asInfo
import org.jetbrains.kotlin.formver.core.embeddings.callables.toFuncApp
import org.jetbrains.kotlin.formver.core.embeddings.callables.toMethodCall
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.IntArrayEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.contentsField
import org.jetbrains.kotlin.formver.core.embeddings.types.fillHoles
import org.jetbrains.kotlin.formver.core.embeddings.types.injection
import org.jetbrains.kotlin.formver.core.names.sourceSpelling
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Exp.Companion.toConjunction
import org.jetbrains.kotlin.formver.viper.ast.Stmt
import org.jetbrains.kotlin.formver.viper.ast.Type
import org.jetbrains.kotlin.formver.viper.ast.viperLiteral

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
        val conjunction = conditions.pureToViper(true, ctx.typeResolver, ctx.source).toConjunction()
        if (triggerExpressions.isEmpty()) return conjunction to derivedTriggers(variable.name, conjunction)
        val viperTriggers = triggerExpressions.map { triggerExpr ->
            Exp.Trigger(listOf(triggerExpr.linearize().toViperBuiltinType(ctx)))
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
                ctx, e.continueLabel.name, e.headShapes, e.breakLabel.name, e.exitShapes,
            ).orEmpty()
            ctx.requireFoldedReads(e.invariants, headShapes + e.exitShapes)
            // The permissions come first, since the user's invariants may read through them.
            val headPermissions = headShapes.mapNotNull { foldState?.permission(it) }
            ctx.addLabel(e.continueLabel.copy(invariants = headPermissions + e.continueLabel.invariants).toViper(ctx))
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
            ctx.addLabel(e.breakLabel.toViper(ctx))

            e.invariants.forEach {
                ctx.addStatement {
                    Stmt.Assert(it.pureToViper(toBuiltin = true, ctx.typeResolver))
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
            val argsViper = e.args.map { it.linearize().toViper(ctx) }
            val heldArgs = ctx.heldPaths(e.args, e.method.formalArgs)
            for ((path, formal) in heldArgs) {
                when {
                    formal.isUnique -> ctx.exposeFor(path, formal)
                    formal.isBorrowed -> ctx.foldState?.close(ctx, path)
                }
            }
            ctx.addStatement {
                e.method.toMethodCall(argsViper, result.toLocalVarUse(ctx.source.asPosition), ctx.source.asPosition)
            }
            e.method.constructedOpen?.let { ctx.foldConstructed(result, it) }
            for ((path, formal) in heldArgs) {
                when {
                    !formal.isBorrowed -> ctx.foldState?.release(ctx, path)
                    // The callee may write through its shared view of the argument, so the caller's values are stale.
                    !formal.isUnique -> ctx.foldState?.refresh(ctx, path)
                    else -> ctx.foldState?.refreshRetained(ctx, path)
                }
            }
        }
    }

    override fun visitFunctionCall(e: FunctionCall): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp {
            val argsViper = e.args.map { it.linearize().toViper(ctx) }
            for ((path, formal) in ctx.heldPaths(e.args, e.function.formalArgs)) {
                if (formal.isUnique) ctx.exposeFor(path, formal)
            }
            return e.function.toFuncApp(argsViper, ctx.source.asPosition)
        }
    }

    override fun visitInvokeFunctionObject(e: InvokeFunctionObject): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp {
            val variable = ctx.freshAnonVar(e.type)
            e.receiver.linearize().toViperUnusedResult(ctx)
            for (arg in e.args) arg.linearize().toViperUnusedResult(ctx)
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
            ctx.addLabel(e.returnLabel.toViper(ctx))
            if (foldState != null && signature != null) {
                signature.formalArgs.filter { it.isUnique && it.isBorrowed }.forEach { foldState.close(ctx, OwnedPath(it)) }
                if (signature.callableType.returnsUnique) foldState.close(ctx, OwnedPath(signature.returns))
            }
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
            ctx.finishMove(returnExp, source, targetOwned = true, OwnedPath(e.target.variable))
            ctx.addReturn(returnExp.linearize(), e.target)
        }
    }

    // endregion

    // region Literals

    override fun visitLiteralEmbedding(e: LiteralEmbedding): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp =
            if (e is NullLit) RuntimeTypeDomain.nullValue(pos = ctx.source.asPosition)
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
        override fun toViper(ctx: LinearizationContext): Exp = e.toViperExp(ctx)
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
            ctx.addStatement { Stmt.Assert(e.exp.linearize().toViperBuiltinType(ctx), ctx.source.asPosition) }
        }
    }

    override fun visitInhaleDirect(e: InhaleDirect): Linearizable = object : UnitResultLinearizable(e) {
        override fun toViperUnusedResult(ctx: LinearizationContext) {
            ctx.addStatement { Stmt.Inhale(e.exp.linearize().toViperBuiltinType(ctx), ctx.source.asPosition) }
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

    override fun visitBinaryOperatorExpEmbedding(e: BinaryOperatorExpEmbedding): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp =
            e.refsOperation(
                e.left.linearize().toViper(ctx),
                e.right.linearize().toViper(ctx),
                pos = ctx.source.asPosition,
                info = e.sourceRole.asInfo
            )

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
            e.builtinsOperation(
                e.left.linearize().toViperBuiltinType(ctx),
                e.right.linearize().toViperBuiltinType(ctx),
                pos = ctx.source.asPosition,
                info = e.sourceRole.asInfo
            )
    }

    override fun visitUnaryOperatorExpEmbedding(e: UnaryOperatorExpEmbedding): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp =
            e.refsOperation(e.inner.linearize().toViper(ctx), pos = ctx.source.asPosition, info = e.sourceRole.asInfo)

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
            e.builtinsOperation(e.inner.linearize().toViperBuiltinType(ctx), pos = ctx.source.asPosition, info = e.sourceRole.asInfo)
    }

    override fun visitInjectionBasedExpEmbedding(e: InjectionBasedExpEmbedding): Linearizable =
        error("visitInjectionBasedExpEmbedding should not be called directly")

    override fun visitSequentialAnd(e: SequentialAnd): Linearizable = sequentialLogicOperator(e)
    override fun visitSequentialOr(e: SequentialOr): Linearizable = sequentialLogicOperator(e)

    private fun sequentialLogicOperator(e: SequentialLogicOperatorEmbedding): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        private fun replacement(ctx: LinearizationContext) = e.operatorReplacement(ctx)

        override fun toViper(ctx: LinearizationContext): Exp =
            replacement(ctx).linearize().toViper(ctx)

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
            replacement(ctx).linearize().toViperBuiltinType(ctx)

        override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) {
            replacement(ctx).linearize().toViperStoringIn(result, ctx)
        }
    }

    // endregion

    // region Comparisons

    override fun visitEqCmp(e: EqCmp): Linearizable = comparisonLinearizable(e)
    override fun visitNeCmp(e: NeCmp): Linearizable = comparisonLinearizable(e)

    override fun visitIdentityCmp(e: IdentityCmp): Linearizable = comparisonLinearizable(e)

    private fun comparisonLinearizable(e: AnyComparisonExpression): Linearizable = object : DirectResultLinearizable(e, this@LinearizationVisitor) {
        override fun toViper(ctx: LinearizationContext): Exp =
            RuntimeTypeDomain.boolInjection.toRef(
                toViperBuiltinType(ctx),
                pos = ctx.source.asPosition,
                info = e.sourceRole.asInfo
            )

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp {
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
            Exp.Old(e.inner.linearize().toViper(ctx), ctx.source.asPosition)

        override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
            Exp.Old(e.inner.linearize().toViperBuiltinType(ctx), ctx.source.asPosition)
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
            receiverPath?.let { ctx.foldState?.open(ctx, it, e.field) }
            val targetOwned = receiverPath != null && e.field.isUnique
            val newValue = runUpdatesOfPath(e.newValue, ctx)
            val source = ctx.moveSource(newValue, targetOwned)
            val newValueViper = newValue.linearize().toViper(ctx)
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
        override fun toViper(ctx: LinearizationContext): Exp {
            val array = e.array.linearize().toViper(ctx)
            val index = e.index.linearize().toViperBuiltinType(ctx)
            val arrayPath = ctx.ownedReceiverPath(e.array, e.receiverOwned, isWrite = false)
            if (arrayPath != null) ctx.assertInBounds(array, index, e.arraySymbol)
            val pos = ctx.source.asPosition
            return ctx.addOwnedRead(IntArrayEmbedding.uniquePredicateAccess(array, pos), IntArrayEmbedding.element(array, index, pos), e.type, arrayPath)
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
            if (arrayPath == null) {
                e.children().forEach { it.linearize().toViperUnusedResult(ctx) }
                return
            }
            val array = e.array.linearize().toViper(ctx)
            val index = atomicIndex(e.index, ctx)
            val value = e.value.linearize().toViperBuiltinType(ctx)
            ctx.assertInBounds(array, index, e.arraySymbol)
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
    private fun atomicIndex(index: ExpEmbedding, ctx: LinearizationContext): Exp {
        if (index.ignoringMetaNodes() is LiteralEmbedding) return index.linearize().toViperBuiltinType(ctx)
        val ref = index.linearize().toViper(ctx)
        if (ref is Exp.LocalVar) return RuntimeTypeDomain.intInjection.fromRef(ref)
        val variable = ctx.freshAnonVar { int() }
        ctx.addStatement { Stmt.assign(variable.toViperExp(this), ref, source.asPosition) }
        return RuntimeTypeDomain.intInjection.fromRef(variable.toViperExp(ctx))
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
            ctx.updateStringBuilder(e, builder) { contents -> Exp.SeqAppend(contents, appended, ctx.source.asPosition) }
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
     * Runs the `StringBuilder` updates that [value] consists of when they act on a path, or the initialization of an
     * [IntArrayInit], and returns that path, which then moves in place of [value]. Any other [value] is returned
     * unchanged. A move folds its source before the value is evaluated, which would close the path before the updates
     * open it again.
     */
    private fun runUpdatesOfPath(value: ExpEmbedding, ctx: LinearizationContext): ExpEmbedding {
        var root = value.ignoringCastsAndMetaNodes()
        if (root is IntArrayInit) {
            value.linearize().toViperUnusedResult(ctx)
            return root.array
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

    override fun visitForAllEmbedding(e: ForAllEmbedding): Linearizable = object : OnlyToBuiltinLinearizable(e, this@LinearizationVisitor) {
        override fun toViperBuiltinType(ctx: LinearizationContext): Exp {
            val (conjunction, viperTriggers) = getQuantifierParts(e.variable, e.conditions, e.triggerExpressions, ctx)
            return Exp.Forall(
                variables = listOf(e.variable.toLocalVarDecl()),
                triggers = viperTriggers,
                exp = if (e.variable.isOriginallyRef) Exp.Implies(
                    e.variable.toViperExp(ctx).isOf(e.variable.type.runtimeType),
                    conjunction
                )
                else conjunction,
                pos = ctx.source.asPosition,
                info = e.sourceRole.asInfo,
            )
        }
    }

    override fun visitExistsEmbedding(e: ExistsEmbedding): Linearizable = object : OnlyToBuiltinLinearizable(e, this@LinearizationVisitor) {
        override fun toViperBuiltinType(ctx: LinearizationContext): Exp {
            val (conjunction, viperTriggers) = getQuantifierParts(e.variable, e.conditions, e.triggerExpressions, ctx)
            return Exp.Exists(
                variables = listOf(e.variable.toLocalVarDecl()),
                triggers = viperTriggers,
                exp = if (e.variable.isOriginallyRef) Exp.And(
                    e.variable.toViperExp(ctx).isOf(e.variable.type.runtimeType),
                    conjunction
                )
                else conjunction,
                pos = ctx.source.asPosition,
                info = e.sourceRole.asInfo,
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
        return object : Linearizable {
            override fun toViper(ctx: LinearizationContext): Exp =
                ctx.positioned { innerLinearizable.toViper(this) }

            override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) {
                ctx.positioned { innerLinearizable.toViperStoringIn(result, this) }
            }

            override fun toViperMaybeStoringIn(result: VariableEmbedding?, ctx: LinearizationContext) {
                ctx.positioned { innerLinearizable.toViperMaybeStoringIn(result, this) }
            }

            override fun toViperBuiltinType(ctx: LinearizationContext): Exp =
                ctx.positioned { innerLinearizable.toViperBuiltinType(this) }

            override fun toViperBuiltinTypeAs(type: TypeEmbedding, ctx: LinearizationContext): Exp =
                ctx.positioned { innerLinearizable.toViperBuiltinTypeAs(type, this) }

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

    override fun visitLambdaExp(e: LambdaExp): Linearizable = object : StoredResultLinearizable(e) {
        override fun toViperStoringIn(result: VariableEmbedding, ctx: LinearizationContext) {
            TODO("create new function object with counter, duplicable (requires toViper restructuring)")
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
        "Ownership of all of ${read.render()} is needed by a loop invariant, but ${hole.render()} is not held at the loop head.",
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
        val written = renderSite(source)?.let { " by $it" } ?: ""
        throw FoldStateException(
            source,
            "Ownership of the receiver written$written is needed here, but the receiver is not a variable followed " +
                "by `@Unique` properties, so its permissions are not tracked.",
        )
    }
    return path
}

/**
 * Sets the contents of [builder], which [e] updates, to [newContents] of the old contents. An update of a builder that
 * is not owned is dropped.
 */
private fun LinearizationContext.updateStringBuilder(e: StringBuilderUpdate, builder: Exp, newContents: (contents: Exp) -> Exp) {
    val builderPath = ownedReceiverPath(e.builder, e.receiverOwned, isWrite = true) ?: return
    foldState?.openOwn(this, builderPath)
    val contents = StringBuilderEmbedding.contents(builder, source.asPosition)
    addStatement { Stmt.FieldAssign(contents, newContents(contents), source.asPosition) }
}

/** Assert that [index] is a valid index into [array], reporting a failure as an array bounds error. */
private fun LinearizationContext.assertInBounds(array: Exp, index: Exp, arraySymbol: FirBasedSymbol<*>?) {
    val pos = source.asPosition
    fun role(bound: SourceRole.ArrayElementAccessCheck.Bound) = SourceRole.ArrayElementAccessCheck(bound, arraySymbol).asInfo
    addStatement { Stmt.Assert(Exp.GeCmp(index, Exp.IntLit(0), pos, role(SourceRole.ArrayElementAccessCheck.Bound.NEGATIVE)), pos) }
    addStatement {
        Stmt.Assert(Exp.LtCmp(index, IntArrayEmbedding.arraySize(array, pos), pos, role(SourceRole.ArrayElementAccessCheck.Bound.NOT_BELOW_SIZE)), pos)
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
    is NullLit -> true
    else -> false
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
        value.isFreshUnique() -> state.acquire(this, target)
        else -> state.release(this, target)
    }
}
