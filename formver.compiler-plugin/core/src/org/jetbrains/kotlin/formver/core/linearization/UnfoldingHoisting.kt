/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.formver.core.embeddings.types.IntArrayEmbedding
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.BinaryExp
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.UnaryExp

/**
 * [this] specification with each `unfolding` placed as high as it can go.
 *
 * An `unfolding` of a predicate whose arguments are paths (a variable followed by field reads and applications of
 * single-argument functions, such as the getter of a `@Unique` `val`) rises through arithmetic, comparisons, `||`,
 * `==>`, `!`, conditionals, quantifiers, `let`, function applications and sequence and field reads. Equal
 * `unfolding`s that meet are merged. It stops:
 * - at a conjunction, so a conjunct never reads a predicate that an earlier conjunct provides;
 * - at a quantifier whose variables its arguments mention;
 * - below the guard of `==>`, `||` or a conditional when the guard mentions the root of one of its arguments,
 *   except within the argument of `arraySize` outside any field read or `unfolding`, which needs no permission, and
 *   unless an equal `unfolding` rises from the guard itself;
 * - at a `let` whose variable is the root of one of its arguments, or whose value mentions such a root, unless an
 *   equal `unfolding` rises from the value;
 * - at `old`, inside which `unfolding`s are placed independently;
 * - below an operand when another operand of the same expression applies a function to the predicate's argument
 *   at one of [predicateParameters]: inside the `unfolding` that predicate is not available folded;
 * - at any other expression.
 */
fun Exp.hoistUnfoldings(predicateParameters: Set<PredicateParameter>): Exp =
    with(predicateParameters) { hoist().wrapped() }

/** A parameter of [function] whose argument's predicate the function's preconditions take. */
data class PredicateParameter(val function: SymbolicName, val index: Int)

/** [body] under the `unfolding`s of [pending], outermost first, which are free to rise further. */
private class Hoisted(val body: Exp, val pending: List<Exp.Unfolding>) {
    fun wrapped(): Exp = wrap(body, pending)
}

/** The arguments of an `unfolding`'s predicate as paths: the root variable, then the field and function names. */
private typealias UnfoldingKey = Pair<SymbolicName, List<List<Any>>>

private fun Exp.Unfolding.key(): UnfoldingKey? {
    val args = predicateAccess.formalArgs.map { it.pathKey() ?: return null }
    return predicateAccess.predicateName to args
}

/** [this] as a path; with [throughUnfoldings], `unfolding`s around its parts are skipped. */
private fun Exp.pathKey(throughUnfoldings: Boolean = false): List<Any>? = when (this) {
    is Exp.LocalVar -> listOf(name)
    is Exp.FieldAccess -> rcv.pathKey(throughUnfoldings)?.plus(field.name)
    is Exp.FuncApp -> args.singleOrNull()?.pathKey(throughUnfoldings)?.plus(functionName)
    is Exp.Unfolding -> if (throughUnfoldings) body.pathKey(throughUnfoldings) else null
    else -> null
}

private fun Exp.Unfolding.roots(): Set<SymbolicName> =
    predicateAccess.formalArgs.mapNotNull { it.pathKey()?.first() as? SymbolicName }.toSet()

private fun wrap(body: Exp, unfoldings: List<Exp.Unfolding>): Exp =
    unfoldings.foldRight(body) { unfolding, acc -> unfolding.copy(body = acc) }

private fun merge(lists: List<List<Exp.Unfolding>>): List<Exp.Unfolding> =
    lists.flatten().distinctBy { it.key() }

context(predicateParameters: Set<PredicateParameter>)
private fun Exp.hoist(): Hoisted = when (this) {
    is Exp.Unfolding -> {
        val args = predicateAccess.formalArgs.map { it.hoist() }
        val unfolding = copy(predicateAccess = predicateAccess.copy(formalArgs = args.map { it.body }))
        val inner = body.hoist()
        if (unfolding.key() == null) {
            Hoisted(unfolding.copy(body = inner.wrapped()), merge(args.map { it.pending }))
        } else {
            Hoisted(inner.body, merge(args.map { it.pending } + listOf(listOf(unfolding), inner.pending)))
        }
    }

    is Exp.And -> Hoisted(copy(left = left.hoist().wrapped(), right = right.hoist().wrapped()), emptyList())
    is Exp.Implies -> guarded(left, listOf(right)) { guard, (body) -> copy(left = guard, right = body) }
    is Exp.Or -> guarded(left, listOf(right)) { guard, (body) -> copy(left = guard, right = body) }
    is Exp.TernaryExp -> guarded(condExp, listOf(thenExp, elseExp)) { guard, (then, otherwise) ->
        copy(condExp = guard, thenExp = then, elseExp = otherwise)
    }

    is Exp.Forall -> quantified(variables.map { it.name }.toSet(), triggers, exp) { triggers, body ->
        copy(triggers = triggers, exp = body)
    }
    is Exp.Exists -> quantified(variables.map { it.name }.toSet(), triggers, exp) { triggers, body ->
        copy(triggers = triggers, exp = body)
    }

    is Exp.Old -> Hoisted(copy(exp = exp.hoistUnfoldings(predicateParameters)), emptyList())
    is Exp.LetBinding -> bound()

    is Exp.Not -> transparent(listOf(arg)) { (arg) -> copy(arg = arg) }
    is Exp.Minus -> transparent(listOf(arg)) { (arg) -> copy(arg = arg) }
    is Exp.Add -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.Sub -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.Mul -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.Div -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.Mod -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.LtCmp -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.LeCmp -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.GtCmp -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.GeCmp -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.EqCmp -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.NeCmp -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.FuncApp -> transparent(args) { copy(args = it) }
    is Exp.DomainFuncApp -> transparent(args) { copy(args = it) }
    is Exp.FieldAccess -> transparent(listOf(rcv)) { (rcv) -> copy(rcv = rcv) }
    else -> hoistCollection() ?: Hoisted(this, emptyList())
}

/** [hoist] for sequence and multiset operations, which are all transparent; `null` for any other expression. */
context(predicateParameters: Set<PredicateParameter>)
private fun Exp.hoistCollection(): Hoisted? = when (this) {
    is Exp.SeqAppend -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.SeqLength -> transparent(listOf(seq)) { (seq) -> copy(seq = seq) }
    is Exp.SeqIndex -> transparent(listOf(seq, idx)) { (seq, idx) -> copy(seq = seq, idx = idx) }
    is Exp.SeqTake -> transparent(listOf(seq, idx)) { (seq, idx) -> copy(seq = seq, idx = idx) }
    is Exp.SeqUpdate -> transparent(listOf(seq, idx, elem)) { (seq, idx, elem) -> copy(seq = seq, idx = idx, elem = elem) }
    is Exp.ExplicitSeq -> transparent(args) { copy(args = it) }
    is Exp.ExplicitMultiset -> transparent(args) { copy(args = it) }
    is Exp.MultisetUnion -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.MultisetMinus -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.MultisetCount -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.MultisetSize -> transparent(listOf(arg)) { (arg) -> copy(arg = arg) }
    else -> null
}

context(predicateParameters: Set<PredicateParameter>)
private fun transparent(children: List<Exp>, rebuild: (List<Exp>) -> Exp): Hoisted {
    val hoisted = settle(children.map { it.hoist() })
    return Hoisted(rebuild(hoisted.map { it.body }), merge(hoisted.map { it.pending }))
}

/**
 * [operands] of one expression, each keeping below it the `unfolding`s that another operand needs folded, together
 * with the `unfolding`s after them, whose predicates they may provide.
 */
context(predicateParameters: Set<PredicateParameter>)
private fun settle(operands: List<Hoisted>): List<Hoisted> = operands.mapIndexed { index, operand ->
    val others = operands.filterIndexed { other, _ -> other != index }.map { it.body }
    val firstStaying = operand.pending.indexOfFirst { unfolding -> others.any { it.takesPredicateOf(unfolding) } }
    if (firstStaying < 0) operand
    else Hoisted(wrap(operand.body, operand.pending.drop(firstStaying)), operand.pending.take(firstStaying))
}

/** Whether [this] applies a function to an argument of [unfolding] at a parameter that takes its predicate. */
context(predicateParameters: Set<PredicateParameter>)
private fun Exp.takesPredicateOf(unfolding: Exp.Unfolding): Boolean =
    takesPredicateAt(unfolding.predicateAccess.formalArgs.map { it.pathKey() }.toSet())

context(predicateParameters: Set<PredicateParameter>)
private fun Exp.takesPredicateAt(paths: Set<List<Any>?>): Boolean =
    this is Exp.FuncApp && args.withIndex().any { (index, arg) ->
        PredicateParameter(functionName, index) in predicateParameters && arg.pathKey(throughUnfoldings = true) in paths
    } || subExps().any { it.takesPredicateAt(paths) }

/**
 * [guard] decides whether [branches] are evaluated. An `unfolding` from a branch stays in that branch when the guard
 * mentions the root of one of its arguments, unless an equal `unfolding` rises from the guard: the branch is then
 * evaluated under it, and keeping the branch's copy would nest an `unfolding` inside an equal one, which has no
 * predicate left to unfold.
 */
context(predicateParameters: Set<PredicateParameter>)
private fun guarded(guard: Exp, branches: List<Exp>, rebuild: (Exp, List<Exp>) -> Exp): Hoisted {
    val settled = settle((listOf(guard) + branches).map { it.hoist() })
    val hoistedGuard = settled.first()
    val mentioned = guard.mentionedVariables()
    val guardKeys = hoistedGuard.pending.map { it.key() }.toSet()
    val hoistedBranches = settled.drop(1).map { hoisted ->
        val (rising, staying) = hoisted.pending.partition { unfolding ->
            unfolding.key() in guardKeys || mentioned != null && unfolding.roots().none { it in mentioned }
        }
        Hoisted(wrap(hoisted.body, staying), rising)
    }
    return Hoisted(
        rebuild(hoistedGuard.body, hoistedBranches.map { it.body }),
        merge(listOf(hoistedGuard.pending) + hoistedBranches.map { it.pending }),
    )
}

/**
 * An `unfolding` from the body rises unless the bound variable is the root of one of its arguments, or the value
 * mentions such a root and no equal `unfolding` rises from the value.
 */
context(predicateParameters: Set<PredicateParameter>)
private fun Exp.LetBinding.bound(): Hoisted {
    val (value, inner) = settle(listOf(varExp.hoist(), body.hoist()))
    val mentioned = varExp.mentionedVariables()
    val valueKeys = value.pending.map { it.key() }.toSet()
    val (rising, staying) = inner.pending.partition { unfolding ->
        val roots = unfolding.roots()
        variable.name !in roots &&
            (unfolding.key() in valueKeys || mentioned != null && roots.none { it in mentioned })
    }
    return Hoisted(copy(varExp = value.body, body = wrap(inner.body, staying)), merge(listOf(value.pending, rising)))
}

/**
 * An `unfolding` from [body] that mentions none of [bound] rises above the quantifier, and is removed from the
 * triggers too, since a trigger matches terms of the body.
 */
context(predicateParameters: Set<PredicateParameter>)
private fun quantified(
    bound: Set<SymbolicName>,
    triggers: List<Exp.Trigger>,
    body: Exp,
    rebuild: (List<Exp.Trigger>, Exp) -> Exp,
): Hoisted {
    val hoisted = body.hoist()
    val (rising, staying) = hoisted.pending.partition { unfolding -> unfolding.roots().none { it in bound } }
    val risingKeys = rising.map { it.key() }.toSet()
    val newTriggers = triggers.map { trigger ->
        Exp.Trigger(trigger.exps.map { it.withoutUnfoldings(risingKeys) }, trigger.pos, trigger.info)
    }
    return Hoisted(rebuild(newTriggers, wrap(hoisted.body, staying)), rising)
}

context(predicateParameters: Set<PredicateParameter>)
private fun Exp.withoutUnfoldings(keys: Set<UnfoldingKey?>): Exp {
    val hoisted = hoist()
    return wrap(hoisted.body, hoisted.pending.filter { it.key() !in keys })
}

/**
 * The variables [this] mentions, except as the argument of `arraySize` outside any field read or `unfolding`, which
 * needs no permission; `null` when it contains an expression whose variables are not tracked here. Inside such an
 * argument [permissionFree] is set.
 */
private fun Exp.mentionedVariables(permissionFree: Boolean = false): Set<SymbolicName>? {
    var childrenPermissionFree = permissionFree
    val children = when (this) {
        is Exp.LocalVar -> return if (permissionFree) emptySet() else setOf(name)
        is Exp.FuncApp -> {
            if (functionName == IntArrayEmbedding.arraySizeFunction.name) childrenPermissionFree = true
            args
        }
        is Exp.IntLit, is Exp.BoolLit, is Exp.NullLit, is Exp.Result -> emptyList()
        is BinaryExp -> listOf(left, right)
        is UnaryExp -> listOf(arg)
        is Exp.DomainFuncApp -> args
        is Exp.FieldAccess -> {
            childrenPermissionFree = false
            listOf(rcv)
        }
        is Exp.SeqLength -> listOf(seq)
        is Exp.SeqIndex -> listOf(seq, idx)
        is Exp.TernaryExp -> listOf(condExp, thenExp, elseExp)
        is Exp.Unfolding -> {
            childrenPermissionFree = false
            predicateAccess.formalArgs + body
        }
        is Exp.Old -> listOf(exp)
        else -> return null
    }
    return children.fold(emptySet<SymbolicName>() as Set<SymbolicName>?) { acc, child ->
        val mentioned = child.mentionedVariables(childrenPermissionFree)
        if (acc == null || mentioned == null) null else acc + mentioned
    }
}
