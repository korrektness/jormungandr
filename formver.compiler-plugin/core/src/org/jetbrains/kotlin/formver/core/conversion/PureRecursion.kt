/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.formver.core.linearization.subExps
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Predicate
import org.jetbrains.kotlin.formver.viper.ast.UserFunction

/**
 * The call graph of [functions]. A function calls the functions applied in its specification and body, and those
 * applied in the body of a predicate it unfolds, where class laws put them.
 */
internal class PureRecursion(functions: List<UserFunction>, predicates: List<Predicate>) {
    private val callees: Map<SymbolicName, Set<SymbolicName>>

    init {
        val predicateCalls = predicates.associate { it.name to it.body?.let { body -> applied(body, emptyMap()) }.orEmpty() }
        val names = functions.map { it.name }.toSet()
        callees = functions.associate { function ->
            val nodes = function.pres + function.posts + listOfNotNull(function.body)
            function.name to nodes.flatMap { applied(it, predicateCalls) }.filter(names::contains).toSet()
        }
    }

    private fun applied(exp: Exp, predicateCalls: Map<SymbolicName, Set<SymbolicName>>): Set<SymbolicName> = buildSet {
        fun visit(exp: Exp) {
            when (exp) {
                is Exp.FuncApp -> add(exp.functionName)
                is Exp.Unfolding -> addAll(predicateCalls[exp.predicateAccess.predicateName].orEmpty())
                else -> {}
            }
            exp.subExps().forEach(::visit)
        }
        visit(exp)
    }

    /** A shortest call path from [function] back to itself, starting and ending with it, or `null` if there is none. */
    fun cycle(function: SymbolicName): List<SymbolicName>? {
        val parent = mutableMapOf<SymbolicName, SymbolicName>()
        val queue = ArrayDeque(listOf(function))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (callee in callees[current].orEmpty()) {
                if (callee == function) {
                    return generateSequence(current) { if (it == function) null else parent[it] }.toList().reversed() + function
                }
                if (callee !in parent) {
                    parent[callee] = current
                    queue.addLast(callee)
                }
            }
        }
        return null
    }
}
