/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.formver.core.conversion.ReturnTarget
import org.jetbrains.kotlin.formver.core.embeddings.ExpVisitor
import org.jetbrains.kotlin.formver.core.embeddings.LabelEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.LabelLink
import org.jetbrains.kotlin.formver.core.embeddings.callables.NamedFunctionSignatureWithContract
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineCallable
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.formver.core.linearization.OwnedShape
import org.jetbrains.kotlin.formver.viper.SymbolicName

private data class BlockImpl(override val exps: List<ExpEmbedding>) : Block

fun blockOf(vararg exps: ExpEmbedding): Block = BlockImpl(exps.toList())

fun List<ExpEmbedding>.toBlock(): Block = BlockImpl(this)

fun Block(actions: MutableList<ExpEmbedding>.() -> Unit): Block = BlockImpl(buildList {
    actions()
})

sealed interface Block : ExpEmbedding {
    val exps: List<ExpEmbedding>
    override val type: TypeEmbedding
        get() = exps.lastOrNull()?.type ?: buildType { unit() }

    override fun children(): Sequence<ExpEmbedding> = exps.asSequence()
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitBlock(this)
}

data class If(
    val condition: ExpEmbedding,
    val thenBranch: ExpEmbedding,
    val elseBranch: ExpEmbedding,
    override val type: TypeEmbedding
) :
    ExpEmbedding {

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(condition, thenBranch, elseBranch)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitIf(this)
}

/**
 * [headShapes] are the shapes of the variables in scope that the uniqueness checker finds `Unique` at the loop head,
 * and [exitShapes] those after the loop.
 */
data class While(
    val condition: ExpEmbedding,
    val body: ExpEmbedding,
    val breakLabelName: SymbolicName,
    val continueLabelName: SymbolicName,
    val invariants: List<ExpEmbedding>,
    val headShapes: List<OwnedShape> = emptyList(),
    val exitShapes: List<OwnedShape> = emptyList(),
) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { unit() }

    val continueLabel = LabelEmbedding(continueLabelName, invariants)
    val breakLabel = LabelEmbedding(breakLabelName)

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(condition, body)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitWhile(this)
}

data class Goto(val target: LabelLink) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { nothing() }

    override fun <R> accept(v: ExpVisitor<R>): R = v.visitGoto(this)
}

// Using this name to avoid clashes with all our other `Label` types.
data class LabelExp(val label: LabelEmbedding) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { unit() }

    override fun <R> accept(v: ExpVisitor<R>): R = v.visitLabelExp(this)
}

/**
 * An expression that optionally has a label and that uses a goto to exit.
 *
 * The result of the intermediate expression is stored.
 */
data class GotoChainNode(val label: LabelEmbedding?, val exp: ExpEmbedding, val next: LabelLink) :
    ExpEmbedding {
    override val type: TypeEmbedding = exp.type

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(exp)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitGotoChainNode(this)
}

data class NonDeterministically(val exp: ExpEmbedding) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { unit() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(exp)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitNonDeterministically(this)
}

// Note: this is always a *real* Viper method call.
data class MethodCall(val method: NonInlineCallable, val args: List<ExpEmbedding>) : ExpEmbedding {
    override val type: TypeEmbedding = method.callableType.returnType

    override fun children(): Sequence<ExpEmbedding> = args.asSequence()
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitMethodCall(this)
}

data class FunctionCall(val function: NonInlineCallable, val args: List<ExpEmbedding>) : ExpEmbedding {
    override val type: TypeEmbedding = function.callableType.returnType

    override fun <R> accept(v: ExpVisitor<R>): R =
        v.visitFunctionCall(this)

    override fun children(): Sequence<ExpEmbedding> = args.asSequence()
}

/**
 * We need to generate a fresh variable here since we want to havoc the result.
 *
 * TODO: do this with an explicit havoc in `toViperMaybeStoringIn`.
 */
data class InvokeFunctionObject(
    val receiver: ExpEmbedding,
    val args: List<ExpEmbedding>,
    override val type: TypeEmbedding
) :
    ExpEmbedding {

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(receiver) + args.asSequence()
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitInvokeFunctionObject(this)
}

data class FunctionExp(
    val signature: NamedFunctionSignatureWithContract?,
    val body: ExpEmbedding,
    val returnLabel: LabelEmbedding
) :
    ExpEmbedding {
    override val type: TypeEmbedding = body.type

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(body)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitFunctionExp(this)
}

/** How a parameter of an inlined body holds the predicate of its argument while the body runs. */
enum class BindingMode {
    /** A `@Unique` parameter: the body takes the predicate, and what it leaves of it is leaked at exit. */
    Consumed,

    /**
     * The body owns the argument and hands it back folded: a `@Unique @Borrowed` parameter, or a `@Borrowed` one
     * whose argument the caller owns.
     */
    Borrowed,

    /** The body owns nothing through the parameter, and the caller's predicate, if any, is leaked at entry. */
    Released,
}

/**
 * The parameter [root] of an inlined body, declared as [formal], bound to [variable], which stands for [argument] at
 * the call. [root] is `null` for a dispatch receiver.
 */
data class RootBinding(
    val root: FirBasedSymbol<*>?,
    val formal: VariableEmbedding,
    val variable: VariableEmbedding,
    val argument: ExpEmbedding,
    val mode: BindingMode,
)

/**
 * A body inlined at a call: [declarations] store the arguments that are not variables, [body] runs with its parameters
 * bound by [bindings], and the call's value is [result], which reads [returnVariable]. When [returnsUnique], the
 * caller receives the predicate of [returnVariable].
 */
data class InlineCall(
    val bindings: List<RootBinding>,
    val declarations: List<Declare>,
    val body: FunctionExp,
    val returnVariable: VariableEmbedding,
    val result: ExpEmbedding,
    val returnsUnique: Boolean,
) : ExpEmbedding {
    override val type: TypeEmbedding = result.type

    override fun children(): Sequence<ExpEmbedding> = declarations.asSequence() + sequenceOf(body, result)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitInlineCall(this)
}

data class Elvis(val left: ExpEmbedding, val right: ExpEmbedding, override val type: TypeEmbedding) :
    ExpEmbedding {

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(left, right)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitElvis(this)
}

data class Return(
    val returnExp: ExpEmbedding, val target: ReturnTarget
) : ExpEmbedding {
    override val type = buildType { nothing() }

    override fun <R> accept(v: ExpVisitor<R>): R = v.visitReturn(this)

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(returnExp)
}
