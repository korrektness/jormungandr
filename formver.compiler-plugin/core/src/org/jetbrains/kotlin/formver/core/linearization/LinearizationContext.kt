/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.formver.core.embeddings.properties.UniqueValStep
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.core.conversion.ReturnTarget
import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.embeddings.expression.AnonymousVariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.VariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.FieldEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.PretypeBuilder
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeBuilder
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Declaration
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Label
import org.jetbrains.kotlin.formver.viper.ast.Stmt

enum class LogicOperatorPolicy {
    CONVERT_TO_IF, CONVERT_TO_EXPRESSION;
}

/**
 * Context in which an `ExpEmbedding` can be flattened to an `Exp` and a sequence of `Stmt`s.
 *
 * We do not distinguish between expressions and statements on the Kotlin side, but we do on the Viper side.
 * As such, an `ExpEmbedding` can represent a nested structure that has to be flattened into sequences
 * of statements. We call this process linearization.
 */
interface LinearizationContext {
    // TODO: Move position tracking out of LinearizationContext and into LinearizationVisitor,
    //  passing Position explicitly to ctx methods that need it.
    val source: KtSourceElement?
    val logicOperatorPolicy: LogicOperatorPolicy

    /** Whether the linearized code inhales the type invariants the conversion records for a value. */
    val inhalesInvariants: Boolean
        get() = true

    val typeResolver: TypeResolver

    /** The unique predicates the linearized code holds; `null` where no permissions are tracked. */
    val foldState: FoldState?
        get() = null

    fun freshAnonVar(type: TypeEmbedding): AnonymousVariableEmbedding

    fun asBlock(action: LinearizationContext.() -> Unit): Stmt.Seqn
    fun <R> withPosition(newSource: KtSourceElement, action: LinearizationContext.() -> R): R

    fun addStatement(buildStmt: LinearizationContext.() -> Stmt)
    fun addDeclaration(decl: Declaration)
    fun store(lhs: VariableEmbedding, rhs: Linearizable)
    fun addReturn(returnExp: Linearizable, target: ReturnTarget)
    fun addBranch(
        condition: Linearizable,
        thenBranch: Linearizable,
        elseBranch: Linearizable,
        result: VariableEmbedding?
    )

    /**
     * The value of a block that evaluates [statements] and then [last], which has [type]. With a [builtinType], the
     * value is in the builtin form of that type.
     */
    fun addBlock(statements: List<Linearizable>, last: Linearizable, type: TypeEmbedding, builtinType: TypeEmbedding?): Exp {
        val result = freshAnonVar(type)
        statements.forEach { it.toViperUnusedResult(this) }
        last.toViperStoringIn(result, this)
        val value = result.toViperExp(this)
        return if (builtinType != null) defaultToViperBuiltinType({ value }, builtinType, null, this) else value
    }

    /** The value of `if ([condition]) [thenBranch] else [elseBranch]`, which has [type]. */
    fun addConditional(
        condition: Linearizable,
        thenBranch: Linearizable,
        elseBranch: Linearizable,
        type: TypeEmbedding,
    ): Exp {
        val result = freshAnonVar(type)
        addBranch(condition, thenBranch, elseBranch, result)
        return result.toViperExp(this)
    }

    /**
     * [receiverPath] is the receiver's path when it is owned: the access then unfolds the receiver's predicates
     * instead of havocking the result.
     */
    fun addFieldAccess(
        receiver: Linearizable,
        receiverType: TypeEmbedding,
        field: FieldEmbedding,
        receiverPath: OwnedPath? = null,
    ): Exp

    fun addFieldAccessStoringIn(
        receiver: Linearizable,
        receiverType: TypeEmbedding,
        field: FieldEmbedding,
        result: VariableEmbedding,
        receiverPath: OwnedPath? = null,
    )

    /**
     * The value of the `@Unique` `val` [step] on [receiver]. The value needs no permission, but in a specification an
     * [owned] receiver's predicates are unfolded down to the class declaring [step], so that reads below it can unfold
     * the value's predicate.
     */
    fun addUniqueValAccess(receiver: Linearizable, receiverType: TypeEmbedding, step: UniqueValStep, owned: Boolean): Exp

    /**
     * [value], a read of the contents of a built-in owned object under its unique [predicate], as a `Ref` of [type],
     * whose injection [value] is in. In a body, [ownerPath] is the object's path when it is owned: the read then
     * unfolds the predicate, and otherwise havocks the value.
     */
    fun addOwnedRead(predicate: Exp.PredicateAccess, value: Exp, type: TypeEmbedding, ownerPath: OwnedPath?): Exp

    fun addModifier(mod: StmtModifier)

    /**
     * Records [parameter] of a function applied in the linearized code. A context that places `unfolding`s inside
     * expressions keeps them clear of the application; one that unfolds in statements needs nothing.
     */
    fun addPredicateParameter(parameter: PredicateParameter) {}

    fun addLabel(label: Label) {
        foldState?.arriveAt(this, label.name)
        addDeclaration(label.toDecl())
        addStatement { label.toStmt() }
    }

    fun resolveVariableName(name: SymbolicName): SymbolicName
}

fun LinearizationContext.freshAnonVar(init: TypeBuilder.() -> PretypeBuilder): AnonymousVariableEmbedding =
    freshAnonVar(buildType(init))
