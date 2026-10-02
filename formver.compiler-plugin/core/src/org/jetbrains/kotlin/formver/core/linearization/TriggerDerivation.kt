/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.names.FromRefFuncName
import org.jetbrains.kotlin.formver.core.names.QualifiedDomainFuncName
import org.jetbrains.kotlin.formver.core.names.ToRefFuncName
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.AccessPredicate
import org.jetbrains.kotlin.formver.viper.ast.BinaryExp
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.UnaryExp

/**
 * Trigger sets for a user quantifier over [bound] with body [body] and no user triggers: one set for each maximal
 * subterm of [body] outside `old` that has a trigger shape and mentions [bound]. Empty when [body] contains no `old`,
 * so that Silicon infers the triggers.
 *
 * Silicon's inference treats a current-state term and the same term under `old` as one candidate and keeps the `old`
 * one. The quantifier then fires only where the old-state term is already known, and a caller that proves a fact about
 * the current state at a fresh index never instantiates it. With `field contents: Seq[Int]`, this callee postcondition
 *
 *     ensures forall k: Int :: 0 <= k && k < |arr.contents| && k != i && k != j ==>
 *       arr.contents[k] == old(arr.contents[k])
 *
 * is inferred as `{ old(arr.contents[k]) }`, and a caller of `swapSimple(arr, i, j)` cannot carry
 * `forall k :: 0 <= k && k < |arr.contents| ==> arr.contents[k] <= v` across the call. With the trigger
 * `{ arr.contents[k] }` it can.
 *
 * A trigger shape is a field read, a sequence index or length, or a function application, whose arguments are again
 * trigger shapes, variables or literals. The `unfolding`s around field reads are left out of the trigger, and a boxing
 * into or out of `Ref` is not a candidate itself. Terms mentioning a variable bound inside [body] and the runtime-type
 * guard `isSubtype(typeOf(x), T)` are not candidates.
 */
fun derivedTriggers(bound: SymbolicName, body: Exp): List<Exp.Trigger> {
    if (!body.containsOld()) return emptyList()
    val candidates = mutableListOf<Exp>()
    body.collectCandidates(bound, emptySet(), candidates)
    return candidates.distinct().map { Exp.Trigger(listOf(it)) }
}

private fun Exp.collectCandidates(bound: SymbolicName, nested: Set<SymbolicName>, into: MutableList<Exp>) {
    when (this) {
        is Exp.Old -> return
        is Exp.Forall -> exp.collectCandidates(bound, nested + variables.map { it.name }, into)
        is Exp.Exists -> exp.collectCandidates(bound, nested + variables.map { it.name }, into)
        is Exp.LetBinding -> {
            varExp.collectCandidates(bound, nested, into)
            body.collectCandidates(bound, nested + variable.name, into)
        }
        else -> {
            val term = if (isInjection()) null else triggerTerm(nested)
            when {
                term == null -> subExps().forEach { it.collectCandidates(bound, nested, into) }
                term.mentions(bound) -> into.add(term)
            }
        }
    }
}

/**
 * A boxing such as `intToRef(arr.contents[k])`, or an unboxing such as `intFromRef(x.f)`, only matches where that
 * converted term is known, which is narrower than the term inside it, so the term inside is the candidate.
 */
private fun Exp.isInjection(): Boolean {
    val name = (this as? Exp.DomainFuncApp)?.function?.name as? QualifiedDomainFuncName ?: return false
    return name.funcName is ToRefFuncName || name.funcName is FromRefFuncName
}

private val runtimeTypeGuards = setOf(RuntimeTypeDomain.isSubtype, RuntimeTypeDomain.typeOf)

/** [this] as a trigger term without `unfolding`s, or `null` when it does not have a trigger shape. */
private fun Exp.triggerTerm(nested: Set<SymbolicName>): Exp? = when (this) {
    is Exp.FieldAccess -> rcv.triggerArg(nested)?.let { copy(rcv = it) }
    is Exp.SeqLength -> seq.triggerArg(nested)?.let { copy(seq = it) }
    is Exp.SeqIndex -> {
        val seq = seq.triggerArg(nested)
        val idx = idx.triggerArg(nested)
        if (seq == null || idx == null) null else copy(seq = seq, idx = idx)
    }
    is Exp.FuncApp -> args.triggerArgs(nested)?.let { copy(args = it) }
    is Exp.DomainFuncApp ->
        if (function in runtimeTypeGuards) null else args.triggerArgs(nested)?.let { copy(args = it) }
    else -> null
}

private fun Exp.triggerArg(nested: Set<SymbolicName>): Exp? = when (this) {
    is Exp.LocalVar -> takeIf { name !in nested }
    is Exp.IntLit, is Exp.BoolLit, is Exp.NullLit -> this
    is Exp.Unfolding -> body.triggerArg(nested)
    else -> triggerTerm(nested)
}

private fun List<Exp>.triggerArgs(nested: Set<SymbolicName>): List<Exp>? = map { it.triggerArg(nested) ?: return null }

private fun Exp.mentions(variable: SymbolicName): Boolean =
    this is Exp.LocalVar && name == variable || subExps().any { it.mentions(variable) }

private fun Exp.containsOld(): Boolean = this is Exp.Old || subExps().any { it.containsOld() }

private fun Exp.subExps(): List<Exp> = when (this) {
    is BinaryExp -> listOf(left, right)
    is UnaryExp -> listOf(arg)
    is Exp.Forall -> listOf(exp)
    is Exp.Exists -> listOf(exp)
    is Exp.FieldAccess -> listOf(rcv)
    is Exp.FuncApp -> args
    is Exp.DomainFuncApp -> args
    is Exp.AdtConstructorApp -> args
    is Exp.ExplicitSeq -> args
    is Exp.ExplicitMultiset -> args
    is Exp.SeqLength -> listOf(seq)
    is Exp.SeqTake -> listOf(seq, idx)
    is Exp.SeqIndex -> listOf(seq, idx)
    is Exp.SeqUpdate -> listOf(seq, idx, elem)
    is Exp.Old -> listOf(exp)
    is Exp.PredicateAccess -> formalArgs
    is Exp.Unfolding -> listOf(predicateAccess, body)
    is Exp.Acc -> listOf(field)
    is AccessPredicate.FieldAccessPredicate -> listOf(access)
    is Exp.LetBinding -> listOf(varExp, body)
    is Exp.TernaryExp -> listOf(condExp, thenExp, elseExp)
    is Exp.IntLit, is Exp.NullLit, is Exp.BoolLit, is Exp.LocalVar, is Exp.Result, is Exp.EmptySeq,
    is Exp.EmptyMultiset -> emptyList()
}
