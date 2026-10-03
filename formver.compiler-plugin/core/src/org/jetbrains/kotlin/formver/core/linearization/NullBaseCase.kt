/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.AccessPredicate
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Type

/**
 * `parameter == null ==> result == base`, where `base` is the pure function body [this] with every condition that
 * tests [parameter] against `null` decided; `null` when `base` reads the heap.
 *
 * Silicon unrolls a function's definition only at applications it meets directly, so the recursive application on a
 * `null` field of a folded value stays opaque; this postcondition states it for every application. It is an
 * optimisation: a body whose `null` case reads the heap gets no postcondition.
 */
fun Exp.nullBaseCase(parameter: SymbolicName): Exp? {
    val base = atNull(parameter)
    if (!base.isHeapFree()) return null
    val isNull = Exp.EqCmp(Exp.LocalVar(parameter, Type.Ref), RuntimeTypeDomain.nullValue())
    return Exp.Implies(isNull, Exp.EqCmp(Exp.Result(Type.Ref), base))
}

/** [this] where [parameter] is `null`: conditions on it decided, and `let`s whose variable is unused dropped. */
private fun Exp.atNull(parameter: SymbolicName): Exp = when (this) {
    is Exp.LetBinding -> {
        val inner = body.atNull(parameter)
        if (inner.mentions(variable.name)) copy(varExp = varExp.atNull(parameter), body = inner) else inner
    }

    is Exp.TernaryExp -> when (condExp.isNullTest(parameter)) {
        true -> thenExp.atNull(parameter)
        false -> elseExp.atNull(parameter)
        null -> copy(thenExp = thenExp.atNull(parameter), elseExp = elseExp.atNull(parameter))
    }

    else -> this
}

/** The value of [this] where [parameter] is `null`, when it is decided by comparisons of [parameter] with `null`. */
private fun Exp.isNullTest(parameter: SymbolicName): Boolean? = when (this) {
    is Exp.EqCmp -> comparesWithNull(parameter, left, right)
    is Exp.NeCmp -> comparesWithNull(parameter, left, right)?.not()
    is Exp.Not -> arg.isNullTest(parameter)?.not()
    is Exp.And -> {
        val l = left.isNullTest(parameter)
        val r = right.isNullTest(parameter)
        if (l == false || r == false) false else if (l == true && r == true) true else null
    }

    is Exp.Or -> {
        val l = left.isNullTest(parameter)
        val r = right.isNullTest(parameter)
        if (l == true || r == true) true else if (l == false && r == false) false else null
    }

    else -> null
}

private fun comparesWithNull(parameter: SymbolicName, left: Exp, right: Exp): Boolean? =
    if (listOf(left, right).let { pair -> pair.any { it.isParameter(parameter) } && pair.any { it.isNullValue() } }) true
    else null

private fun Exp.isParameter(parameter: SymbolicName) = this is Exp.LocalVar && name == parameter

private fun Exp.isNullValue() = this is Exp.DomainFuncApp && function.name == RuntimeTypeDomain.nullValue.name

private fun Exp.isHeapFree(): Boolean = when (this) {
    is Exp.FieldAccess, is Exp.FuncApp, is Exp.Unfolding, is Exp.PredicateAccess, is Exp.Acc,
    is AccessPredicate.FieldAccessPredicate, is Exp.Old -> false

    else -> subExps().all { it.isHeapFree() }
}
