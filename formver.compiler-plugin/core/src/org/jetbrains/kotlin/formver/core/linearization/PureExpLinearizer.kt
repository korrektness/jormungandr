/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.formver.core.embeddings.properties.UniqueValStep
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.asPosition
import org.jetbrains.kotlin.formver.core.conversion.ReturnTarget
import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.embeddings.expression.AnonymousVariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.VariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.FieldEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.injection
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Declaration
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Label
import org.jetbrains.kotlin.formver.viper.ast.Position
import org.jetbrains.kotlin.formver.viper.ast.Stmt

/**
 * Linearization context linearizing a pure ExpEmbedding into a Viper expression.
 *
 * There are cases in Viper where we expect our result to be an expression by itself, for example when
 * processing preconditions, postconditions, and invariants. Compared to the [PureFunBodyLinearizer],
 * this linearizer is highly restrictive on what embeddings are supported and is used to translate
 * specifications made in Kotlin into Viper expressions. A construct that needs a statement is reported as
 * unsupported.
 *
 * The locals of a block, such as the parameters and result of an inlined call, are bound with let-expressions
 * around the block's value: see [LetScope].
 */
data class PureExpLinearizer(
    override val source: KtSourceElement?,
    override val typeResolver: TypeResolver,
    /** The scope of the innermost enclosing block; `null` outside every block. */
    private val scope: LetScope? = null,
) : LinearizationContext {

    override val logicOperatorPolicy: LogicOperatorPolicy
        get() = LogicOperatorPolicy.CONVERT_TO_EXPRESSION

    /** A specification has no code after it to rely on inhaled facts. */
    override val inhalesInvariants: Boolean
        get() = false

    private fun unsupported(msg: String): Nothing = throw UnsupportedFeatureException(source, msg)

    override fun <R> withPosition(newSource: KtSourceElement, action: LinearizationContext.() -> R): R =
        copy(source = newSource).action()

    /** The value [action] produces, with the locals declared while producing it bound around it. */
    private fun inNewScope(action: LinearizationContext.() -> Exp): Exp {
        val inner = LetScope()
        return inner.bindAround(copy(scope = inner).action(), source.asPosition)
    }

    override fun freshAnonVar(type: TypeEmbedding): AnonymousVariableEmbedding =
        unsupported("This construct needs a temporary variable, which a specification cannot introduce.")

    override fun asBlock(action: LinearizationContext.() -> Unit): Stmt.Seqn =
        unsupported("This construct needs a statement block, which a specification cannot contain.")

    override fun addStatement(buildStmt: LinearizationContext.() -> Stmt) =
        unsupported("This construct needs a statement, which a specification cannot contain.")

    override fun addDeclaration(decl: Declaration) {
        if (decl !is Declaration.LocalVarDecl) unsupported("This construct needs a declaration, which a specification cannot contain.")
        if (scope == null) unsupported("A specification can declare locals only inside a lambda or block.")
        scope.declare(decl)
    }

    /** Only a return can jump to a label in a specification, and [addReturn] takes no jump. */
    override fun addLabel(label: Label) {}

    private fun bind(variable: VariableEmbedding, value: Exp) {
        if (scope?.bind(variable.name, value) != true) unsupported("A specification can only initialize a local it declares, and only once.")
    }

    override fun store(lhs: VariableEmbedding, rhs: Linearizable) = bind(lhs, rhs.toViper(this))

    override fun addReturn(returnExp: Linearizable, target: ReturnTarget) {
        val linearize = { builtinType: TypeEmbedding? ->
            if (builtinType == null) returnExp.toViper(this)
            else returnExp.toViperInBuiltinForm(target.variable.type, builtinType, this)
        }
        if (scope?.bindResult(target.variable.name, linearize) != true) {
            unsupported("A return in a specification must be the last expression of its lambda.")
        }
    }

    override fun addBranch(
        condition: Linearizable,
        thenBranch: Linearizable,
        elseBranch: Linearizable,
        result: VariableEmbedding?
    ) {
        if (result == null) unsupported("A specification cannot use if or when as a statement.")
        bind(result, addConditional(condition, thenBranch, elseBranch, result.type))
    }

    override fun addBlock(
        statements: List<Linearizable>,
        last: Linearizable,
        type: TypeEmbedding,
        builtinType: TypeEmbedding?,
    ): Exp {
        val inner = LetScope()
        val ctx = copy(scope = inner)
        statements.forEach { it.toViperUnusedResult(ctx) }
        val lastExp = last.toViper(ctx)
        val result = inner.takeResult(lastExp, builtinType)
        val value = inner.bindAround(result ?: lastExp, source.asPosition)
        return if (builtinType != null && result == null) defaultToViperBuiltinType({ value }, builtinType, null, this)
        else value
    }

    override fun addConditional(
        condition: Linearizable,
        thenBranch: Linearizable,
        elseBranch: Linearizable,
        type: TypeEmbedding,
    ): Exp = Exp.TernaryExp(
        condition.toViperBuiltinType(this),
        inNewScope { thenBranch.toViper(this) },
        inNewScope { elseBranch.toViper(this) },
        source.asPosition,
    )

    override fun addFieldAccessStoringIn(
        receiver: Linearizable,
        receiverType: TypeEmbedding,
        field: FieldEmbedding,
        result: VariableEmbedding,
        receiverPath: OwnedPath?,
    ) {
        bind(result, addFieldAccess(receiver, receiverType, field, receiverPath))
    }

    override fun addFieldAccess(
        receiver: Linearizable,
        receiverType: TypeEmbedding,
        field: FieldEmbedding,
        receiverPath: OwnedPath?,
    ): Exp {
        val receiverViper = receiver.toViper(this)
        val primitiveAccess: Exp = Exp.FieldAccess(receiverViper, field.toViper(), source.asPosition)
        return hierarchyPredicateAccesses(receiverViper, receiverType, field).toList()
            .foldRight(primitiveAccess) { predicateAccess, acc -> Exp.Unfolding(predicateAccess, acc) }
    }

    override fun addUniqueValAccess(receiver: Linearizable, receiverType: TypeEmbedding, step: UniqueValStep, owned: Boolean): Exp {
        val receiverViper = receiver.toViper(this)
        val value = step.valueOf(receiverViper, source.asPosition)
        if (!owned) return value
        return hierarchyPredicateAccesses(receiverViper, receiverType, step).toList()
            .foldRight(value) { predicateAccess, acc -> Exp.Unfolding(predicateAccess, acc) }
    }

    override fun addOwnedRead(predicate: Exp.PredicateAccess, value: Exp, type: TypeEmbedding, ownerPath: OwnedPath?): Exp =
        type.injection.toRef(Exp.Unfolding(predicate, value, source.asPosition), pos = source.asPosition)

    override fun addModifier(mod: StmtModifier) =
        unsupported("This construct needs a statement, which a specification cannot contain.")

    override fun resolveVariableName(name: SymbolicName): SymbolicName =
        name
}

fun ExpEmbedding.pureToViper(toBuiltin: Boolean, typeResolver: TypeResolver, source: KtSourceElement? = null): Exp {
    val linearizer = PureExpLinearizer(source, typeResolver)
    val lin = toLinearizable(source)
    val exp = if (toBuiltin) lin.toViperBuiltinType(linearizer) else lin.toViper(linearizer)
    return exp.hoistUnfoldings()
}

fun List<ExpEmbedding>.pureToViper(
    toBuiltin: Boolean,
    typeResolver: TypeResolver,
    source: KtSourceElement? = null
): List<Exp> =
    map { it.pureToViper(toBuiltin, typeResolver, source) }

/**
 * The locals a block in a specification declares, and the values bound to them, in the order they are bound. Each
 * local is bound once: by its initializer, or by the return that gives an inlined call its result.
 *
 * A returned value is linearized when the block's value is, so that a block whose value is the returned value
 * produces it in the form the block's use asks for.
 */
class LetScope {
    private data class Binding(
        val decl: Declaration.LocalVarDecl,
        val isResult: Boolean,
        val linearize: (builtinType: TypeEmbedding?) -> Exp,
    )

    private val declared = mutableMapOf<SymbolicName, Declaration.LocalVarDecl>()
    private val bindings = mutableListOf<Binding>()

    fun declare(decl: Declaration.LocalVarDecl) {
        declared[decl.name] = decl
    }

    /** Binds the local [name] to [value]; false when this scope declares no unbound local [name]. */
    fun bind(name: SymbolicName, value: Exp): Boolean = add(name, isResult = false) { value }

    /**
     * Binds the local [name] to a returned value, which [linearize] produces in reference form, or in the builtin form
     * of the type it is given.
     */
    fun bindResult(name: SymbolicName, linearize: (builtinType: TypeEmbedding?) -> Exp): Boolean =
        add(name, isResult = true, linearize)

    private fun add(name: SymbolicName, isResult: Boolean, linearize: (builtinType: TypeEmbedding?) -> Exp): Boolean {
        val decl = declared.remove(name) ?: return false
        bindings.add(Binding(decl, isResult, linearize))
        return true
    }

    /**
     * The returned value, in the builtin form of [builtinType] or in reference form without one, when [body] reads the
     * local bound last and that local holds a returned value. The binding is then consumed.
     */
    fun takeResult(body: Exp, builtinType: TypeEmbedding?): Exp? {
        val last = bindings.lastOrNull()?.takeIf { it.isResult } ?: return null
        if (body !is Exp.LocalVar || body.name != last.decl.name) return null
        bindings.removeLast()
        return last.linearize(builtinType)
    }

    /**
     * [body] under the bindings of this scope. A binding whose body is its local, or a domain function applied to its
     * local alone, has its value substituted for the local.
     */
    fun bindAround(body: Exp, pos: Position): Exp =
        bindings.foldRight(body) { binding, acc ->
            val value = binding.linearize(null)
            acc.substitutingLocal(binding.decl.name, value) ?: Exp.LetBinding(binding.decl, value, acc, pos)
        }

    private fun Exp.substitutingLocal(name: SymbolicName, value: Exp): Exp? {
        fun Exp.isLocal() = this is Exp.LocalVar && this.name == name
        return when {
            isLocal() -> value
            this is Exp.DomainFuncApp && args.singleOrNull()?.isLocal() == true -> copy(args = listOf(value))
            else -> null
        }
    }
}
