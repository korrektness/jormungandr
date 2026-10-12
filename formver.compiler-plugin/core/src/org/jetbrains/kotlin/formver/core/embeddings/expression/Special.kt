/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.formver.common.PurityCondition
import org.jetbrains.kotlin.formver.common.PurityRule
import org.jetbrains.kotlin.formver.core.embeddings.ExpVisitor
import org.jetbrains.kotlin.formver.core.embeddings.properties.FieldEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.PathStep
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.formver.core.purity.PurityContext
import org.jetbrains.kotlin.formver.core.purity.isPure
import org.jetbrains.kotlin.formver.viper.ast.Exp

/**
 * Especially when working with type information, there are a number of expressions that do not have a corresponding `ExpEmbedding`.
 * We will eventually want to solve this somehow, but there are still open design questions there, so for now this wrapper will
 * do the job.
 */
class ExpWrapper private constructor(private val expression: () -> Exp, override val type: TypeEmbedding) : ExpEmbedding {
    constructor(value: Exp, type: TypeEmbedding) : this({ value }, type)

    val value: Exp get() = expression()

    companion object {
        /** Resolve a specification expression after all type declarations have been registered. */
        fun deferred(type: TypeEmbedding, expression: () -> Exp) = ExpWrapper(expression, type)
    }

    override fun <R> accept(v: ExpVisitor<R>): R = v.visitExpWrapper(this)
}

class RepresentationObserverCall(
    val args: List<ExpEmbedding>,
    override val type: TypeEmbedding,
    val expand: (List<Exp>) -> Exp,
) : ExpEmbedding {
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitRepresentationObserverCall(this)
}

/**
 * Assumes that control does not reach this point (`inhale false`).
 */
data object Unreachable : ExpEmbedding {
    override val type: TypeEmbedding = buildType { nothing() }

    override fun <R> accept(v: ExpVisitor<R>): R = v.visitUnreachable(this)
}

/**
 * Stands in for a statement that could not be converted because it is unsupported.
 * The unsupported construct is reported, which stops the function before linearization.
 */
data object UnsupportedPlaceholder : ExpEmbedding {
    override val type: TypeEmbedding = buildType { nothing() }

    override fun <R> accept(v: ExpVisitor<R>): R = v.visitUnsupportedPlaceholder(this)
}

/** Allocates field permissions and fixes the runtime identity, without assuming a representation invariant. */
data class AllocateObject(
    val result: VariableEmbedding,
    val constructed: ClassTypeEmbedding,
    val fields: List<FieldEmbedding>,
) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { unit() }
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitAllocateObject(this)
}

/** Establishes the declaring class's laws before exposing the receiver or completing construction. */
data class EstablishConstruction(
    val receiver: VariableEmbedding,
    val declaring: ClassTypeEmbedding,
    val result: ExpEmbedding = UnitLit,
) : ExpEmbedding {
    override val type: TypeEmbedding = result.type
    override fun ignoringCastsAndMetaNodes(): ExpEmbedding = result.ignoringCastsAndMetaNodes()
    override fun children(): Sequence<ExpEmbedding> = sequenceOf(result)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitEstablishConstruction(this)
}

/** Moves an evaluated unique initializer into immutable storage whose getter equality is established. */
data class BindOwnedField(
    val receiver: VariableEmbedding,
    val step: PathStep,
    val value: VariableEmbedding,
) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { unit() }
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitBindOwnedField(this)
}

data class Assert(val exp: ExpEmbedding) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { unit() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(exp)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitAssert(this)

    override fun isValid(ctx: PurityContext): Boolean = exp.isPure(admitsInlinedCalls = true).also {
        if (!it) ctx.addPurityError(exp, PurityCondition(PurityRule.ASSERT_CONDITION))
    }
}

data class Refute(val exp: ExpEmbedding) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { unit() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(exp)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitRefute(this)

    override fun isValid(ctx: PurityContext): Boolean = exp.isPure(admitsInlinedCalls = true).also {
        if (!it) ctx.addPurityError(exp, PurityCondition(PurityRule.REFUTE_CONDITION))
    }
}

/**
 * Immediately performs an unconditional inhale of the statement.
 *
 * This can cause all kinds of issues with statement ordering, so it's more of a solution for porting legacy stuff than something
 * we should be adding more of going forward.
 */
data class InhaleDirect(val exp: ExpEmbedding) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { unit() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(exp)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitInhaleDirect(this)
}


data class Unfold(val pred: PredicateAccessPermissions) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { unit() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(pred)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitUnfold(this)
}

data class Fold(val pred: PredicateAccessPermissions) : ExpEmbedding {
    override val type: TypeEmbedding = buildType { unit() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(pred)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitFold(this)
}
