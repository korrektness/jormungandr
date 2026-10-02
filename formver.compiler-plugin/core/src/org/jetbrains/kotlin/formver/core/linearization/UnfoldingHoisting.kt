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
 * An `unfolding` of a predicate whose arguments are paths (a variable followed by field reads) rises through
 * arithmetic, comparisons, `||`, `==>`, `!`, conditionals, quantifiers, function applications and sequence and field
 * reads. Equal `unfolding`s that meet are merged. It stops:
 * - at a conjunction, so a conjunct never reads a predicate that an earlier conjunct provides;
 * - at a quantifier whose variables its arguments mention;
 * - below the guard of `==>`, `||` or a conditional when the guard mentions the root of one of its arguments,
 *   except as the argument of `arraySize`, which needs no permission, and unless an equal `unfolding` rises from
 *   the guard itself;
 * - at `old` and `let`, inside which `unfolding`s are placed independently;
 * - at any other expression.
 */
fun Exp.hoistUnfoldings(): Exp = hoist().wrapped()

/** [body] under the `unfolding`s of [pending], outermost first, which are free to rise further. */
private class Hoisted(val body: Exp, val pending: List<Exp.Unfolding>) {
    fun wrapped(): Exp = wrap(body, pending)
}

/** The arguments of an `unfolding`'s predicate as paths: the root variable, then the field names. */
private typealias UnfoldingKey = Pair<SymbolicName, List<List<Any>>>

private fun Exp.Unfolding.key(): UnfoldingKey? {
    val args = predicateAccess.formalArgs.map { it.pathKey() ?: return null }
    return predicateAccess.predicateName to args
}

private fun Exp.pathKey(): List<Any>? = when (this) {
    is Exp.LocalVar -> listOf(name)
    is Exp.FieldAccess -> rcv.pathKey()?.plus(field.name)
    else -> null
}

private fun Exp.Unfolding.roots(): Set<SymbolicName> =
    predicateAccess.formalArgs.mapNotNull { it.pathKey()?.first() as? SymbolicName }.toSet()

private fun wrap(body: Exp, unfoldings: List<Exp.Unfolding>): Exp =
    unfoldings.foldRight(body) { unfolding, acc -> unfolding.copy(body = acc) }

private fun merge(lists: List<List<Exp.Unfolding>>): List<Exp.Unfolding> =
    lists.flatten().distinctBy { it.key() }

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

    is Exp.Old -> Hoisted(copy(exp = exp.hoistUnfoldings()), emptyList())
    is Exp.LetBinding -> Hoisted(copy(varExp = varExp.hoistUnfoldings(), body = body.hoistUnfoldings()), emptyList())

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
    is Exp.SeqAppend -> transparent(listOf(left, right)) { (l, r) -> copy(left = l, right = r) }
    is Exp.FuncApp -> transparent(args) { copy(args = it) }
    is Exp.DomainFuncApp -> transparent(args) { copy(args = it) }
    is Exp.FieldAccess -> transparent(listOf(rcv)) { (rcv) -> copy(rcv = rcv) }
    is Exp.SeqLength -> transparent(listOf(seq)) { (seq) -> copy(seq = seq) }
    is Exp.SeqIndex -> transparent(listOf(seq, idx)) { (seq, idx) -> copy(seq = seq, idx = idx) }
    is Exp.SeqTake -> transparent(listOf(seq, idx)) { (seq, idx) -> copy(seq = seq, idx = idx) }
    is Exp.SeqUpdate -> transparent(listOf(seq, idx, elem)) { (seq, idx, elem) -> copy(seq = seq, idx = idx, elem = elem) }
    is Exp.ExplicitSeq -> transparent(args) { copy(args = it) }

    else -> Hoisted(this, emptyList())
}

private fun transparent(children: List<Exp>, rebuild: (List<Exp>) -> Exp): Hoisted {
    val hoisted = children.map { it.hoist() }
    return Hoisted(rebuild(hoisted.map { it.body }), merge(hoisted.map { it.pending }))
}

/**
 * [guard] decides whether [branches] are evaluated. An `unfolding` from a branch stays in that branch when the guard
 * mentions the root of one of its arguments, unless an equal `unfolding` rises from the guard: the branch is then
 * evaluated under it, and keeping the branch's copy would nest an `unfolding` inside an equal one, which has no
 * predicate left to unfold.
 */
private fun guarded(guard: Exp, branches: List<Exp>, rebuild: (Exp, List<Exp>) -> Exp): Hoisted {
    val hoistedGuard = guard.hoist()
    val mentioned = guard.mentionedVariables()
    val guardKeys = hoistedGuard.pending.map { it.key() }.toSet()
    val hoistedBranches = branches.map { branch ->
        val hoisted = branch.hoist()
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
 * An `unfolding` from [body] that mentions none of [bound] rises above the quantifier, and is removed from the
 * triggers too, since a trigger matches terms of the body.
 */
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

private fun Exp.withoutUnfoldings(keys: Set<UnfoldingKey?>): Exp {
    val hoisted = hoist()
    return wrap(hoisted.body, hoisted.pending.filter { it.key() !in keys })
}

/**
 * The variables [this] mentions, except as the argument of `arraySize`; `null` when it contains an expression whose
 * variables are not tracked here.
 */
private fun Exp.mentionedVariables(): Set<SymbolicName>? {
    val children = when (this) {
        is Exp.LocalVar -> return setOf(name)
        is Exp.FuncApp -> if (functionName == IntArrayEmbedding.arraySizeFunction.name) return emptySet() else args
        is Exp.IntLit, is Exp.BoolLit, is Exp.NullLit, is Exp.Result -> emptyList()
        is BinaryExp -> listOf(left, right)
        is UnaryExp -> listOf(arg)
        is Exp.DomainFuncApp -> args
        is Exp.FieldAccess -> listOf(rcv)
        is Exp.SeqLength -> listOf(seq)
        is Exp.SeqIndex -> listOf(seq, idx)
        is Exp.TernaryExp -> listOf(condExp, thenExp, elseExp)
        is Exp.Unfolding -> predicateAccess.formalArgs + body
        is Exp.Old -> listOf(exp)
        else -> return null
    }
    return children.fold(emptySet<SymbolicName>() as Set<SymbolicName>?) { acc, child ->
        val mentioned = child.mentionedVariables()
        if (acc == null || mentioned == null) null else acc + mentioned
    }
}
