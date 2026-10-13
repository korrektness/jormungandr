/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.formver.core.linearization.subExps
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.AccessPredicate
import org.jetbrains.kotlin.formver.viper.ast.BinaryExp
import org.jetbrains.kotlin.formver.viper.ast.UnaryExp
import org.jetbrains.kotlin.formver.viper.ast.Exp

/** A value path, independent of source positions and the unfoldings needed to read it. */
internal fun Exp.capturePath(): List<SymbolicName>? = when (this) {
    is Exp.WithInfo -> inner.capturePath()
    is Exp.LocalVar -> listOf(name)
    is Exp.FieldAccess -> rcv.capturePath()?.plus(field.name)
    is Exp.Unfolding -> body.capturePath()
    else -> null
}

internal fun Exp.freeVariables(bound: Set<SymbolicName> = emptySet()): Set<SymbolicName> = when (this) {
    is Exp.LocalVar -> setOf(name) - bound
    is Exp.Forall -> (listOf(exp) + triggers.flatMap { it.exps }).flatMapTo(mutableSetOf()) {
        it.freeVariables(bound + variables.map { variable -> variable.name })
    }
    is Exp.Exists -> (listOf(exp) + triggers.flatMap { it.exps }).flatMapTo(mutableSetOf()) {
        it.freeVariables(bound + variables.map { variable -> variable.name })
    }
    is Exp.LetBinding -> varExp.freeVariables(bound) + body.freeVariables(bound + variable.name)
    else -> subExps().flatMapTo(mutableSetOf()) { it.freeVariables(bound) }
}

/** A value's expression structure, independent of source metadata and read permissions. */
internal fun Exp.captureValueKey(): Any = capturePath() ?: when (this) {
    is Exp.WithInfo -> inner.captureValueKey()
    is Exp.Unfolding -> body.captureValueKey()
    is Exp.IntLit -> listOf(javaClass, value)
    is Exp.BoolLit -> listOf(javaClass, value)
    is Exp.NullLit -> javaClass
    is Exp.FieldAccess -> listOf(javaClass, field.name, rcv.captureValueKey())
    is Exp.FuncApp -> listOf(javaClass, functionName, args.map { it.captureValueKey() })
    is Exp.DomainFuncApp -> listOf(javaClass, function.name, typeVarMap, args.map { it.captureValueKey() })
    is Exp.AdtConstructorApp -> listOf(javaClass, constructor.name, typeVarMap, args.map { it.captureValueKey() })
    is Exp.Forall -> listOf(javaClass, variables.map { it.name to it.type }, exp.captureValueKey())
    is Exp.Exists -> listOf(javaClass, variables.map { it.name to it.type }, exp.captureValueKey())
    is Exp.LetBinding -> listOf(javaClass, variable.name, varExp.captureValueKey(), body.captureValueKey())
    is Exp.PredicateAccess -> listOf(javaClass, predicateName, perm, formalArgs.map { it.captureValueKey() })
    is Exp.PredicateInstance -> listOf(javaClass, predicateName, args.map { it.captureValueKey() })
    is Exp.Acc -> listOf(javaClass, perm, field.captureValueKey())
    is AccessPredicate.FieldAccessPredicate -> listOf(javaClass, perm, access.captureValueKey())
    is Exp.EmptySeq -> listOf(javaClass, elementType)
    is Exp.EmptyMultiset -> listOf(javaClass, elementType)
    is Exp.Result -> listOf(javaClass, type)
    is Exp.DecreasesWildcard -> javaClass
    is BinaryExp, is UnaryExp, is Exp.ExplicitSeq, is Exp.ExplicitMultiset, is Exp.SeqLength,
    is Exp.SeqTake, is Exp.SeqIndex, is Exp.SeqUpdate, is Exp.Old, is Exp.PermissionCheck,
    is Exp.TernaryExp, is Exp.DecreasesTuple -> listOf(javaClass, subExps().map { it.captureValueKey() })
    is Exp.LocalVar -> error("A local variable has a capture path")
}

private fun BinaryExp.rewriteOperands(rewrite: (Exp) -> Exp): Exp = when (this) {
    is Exp.Add -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.Sub -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.Mul -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.Div -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.Mod -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.LtCmp -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.LeCmp -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.GtCmp -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.GeCmp -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.EqCmp -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.NeCmp -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.And -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.Or -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.Implies -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.SeqAppend -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.MultisetUnion -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.MultisetMinus -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.MultisetCount -> copy(left = rewrite(left), right = rewrite(right))
    is Exp.SeqContains -> copy(left = rewrite(left), right = rewrite(right))
}

/** Rewrite free captured values, including substituted inline arguments and enclosing closure reads. */
internal fun Exp.rewriteCaptureValues(
    replacements: Map<out Any, Exp>,
    bound: Set<SymbolicName> = emptySet(),
): Exp {
    if (freeVariables().none(bound::contains)) replacements[captureValueKey()]?.let { return it }
    fun Exp.rewrite() = rewriteCaptureValues(replacements, bound)
    fun List<Exp>.rewrite() = map { it.rewrite() }
    fun List<Exp.Trigger>.rewrite(variables: List<org.jetbrains.kotlin.formver.viper.ast.Declaration.LocalVarDecl>) = map {
        Exp.Trigger(it.exps.map { exp -> exp.rewriteCaptureValues(replacements, bound + variables.map { v -> v.name }) }, it.pos, it.info)
    }
    return when (this) {
        is Exp.WithInfo -> copy(inner = inner.rewrite())
        is BinaryExp -> rewriteOperands { it.rewrite() }
        is Exp.Minus -> copy(arg = arg.rewrite())
        is Exp.Not -> copy(arg = arg.rewrite())
        is Exp.Forall -> copy(exp = exp.rewriteCaptureValues(replacements, bound + variables.map { it.name }),
            triggers = triggers.rewrite(variables))
        is Exp.Exists -> copy(exp = exp.rewriteCaptureValues(replacements, bound + variables.map { it.name }),
            triggers = triggers.rewrite(variables))
        is Exp.FieldAccess -> copy(rcv = rcv.rewrite())
        is Exp.FuncApp -> copy(args = args.rewrite())
        is Exp.DomainFuncApp -> copy(args = args.rewrite())
        is Exp.AdtConstructorApp -> copy(args = args.rewrite())
        is Exp.ExplicitSeq -> copy(args = args.rewrite())
        is Exp.ExplicitMultiset -> copy(args = args.rewrite())
        is Exp.SeqLength -> copy(seq = seq.rewrite())
        is Exp.SeqTake -> copy(seq = seq.rewrite(), idx = idx.rewrite())
        is Exp.SeqIndex -> copy(seq = seq.rewrite(), idx = idx.rewrite())
        is Exp.SeqUpdate -> copy(seq = seq.rewrite(), idx = idx.rewrite(), elem = elem.rewrite())
        is Exp.MultisetSize -> copy(arg = arg.rewrite())
        is Exp.Old -> copy(exp = exp.rewrite())
        is Exp.PredicateAccess -> copy(formalArgs = formalArgs.rewrite())
        is Exp.PermissionCheck -> copy(access = access.rewrite() as Exp.PredicateAccess)
        is Exp.Unfolding -> copy(predicateAccess = predicateAccess.rewrite() as Exp.PredicateAccess, body = body.rewrite())
        is Exp.Acc -> copy(field = field.rewrite() as Exp.FieldAccess)
        is AccessPredicate.FieldAccessPredicate -> copy(access = access.rewrite() as Exp.FieldAccess)
        is Exp.LetBinding -> copy(varExp = varExp.rewrite(),
            body = body.rewriteCaptureValues(replacements, bound + variable.name))
        is Exp.TernaryExp -> copy(condExp = condExp.rewrite(), thenExp = thenExp.rewrite(), elseExp = elseExp.rewrite())
        is Exp.DecreasesTuple -> copy(measures = measures.rewrite())
        is Exp.PredicateInstance -> copy(args = args.rewrite())
        is Exp.IntLit, is Exp.NullLit, is Exp.BoolLit, is Exp.LocalVar, is Exp.Result, is Exp.EmptySeq,
        is Exp.EmptyMultiset, is Exp.DecreasesWildcard -> this
    }
}

internal fun Exp.conjuncts(): List<Exp> = when (this) {
    is Exp.WithInfo -> inner.conjuncts()
    is Exp.And -> left.conjuncts() + right.conjuncts()
    is Exp.Not -> when (val negated = arg) {
        is Exp.Or -> Exp.Not(negated.left, pos, info).conjuncts() + Exp.Not(negated.right, pos, info).conjuncts()
        is Exp.Not -> negated.arg.conjuncts()
        else -> listOf(this)
    }
    else -> listOf(this)
}

/** Ownership is supplied by the capture representation rather than copied as a creation fact. */
internal fun Exp.hasOwnershipAssertion(): Boolean = when (this) {
    is Exp.PredicateAccess, is Exp.Acc, is AccessPredicate.FieldAccessPredicate, is Exp.PermissionCheck -> true
    is Exp.Unfolding -> body.hasOwnershipAssertion()
    else -> subExps().any { it.hasOwnershipAssertion() }
}
