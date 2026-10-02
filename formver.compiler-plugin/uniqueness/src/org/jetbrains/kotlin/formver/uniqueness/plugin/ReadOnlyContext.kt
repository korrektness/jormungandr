/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ControlFlowGraph
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val formverPluginPackage = FqName("org.jetbrains.kotlin.formver.plugin")

private val pureAnnotationId = ClassId(formverPluginPackage, Name.identifier("Pure"))

private fun builtinId(name: String): CallableId =
    CallableId(formverPluginPackage, Name.identifier(name))

val postconditionsId = builtinId("postconditions")

/**
 * The builtins whose arguments are specifications.
 */
private val specificationFunctionIds: Set<CallableId> =
    setOf(builtinId("preconditions"), postconditionsId, builtinId("loopInvariants"), builtinId("verify"),
        builtinId("forAll"), builtinId("exists"))

fun FirBasedSymbol<*>.isPure(session: FirSession): Boolean =
    hasAnnotation(pureAnnotationId, session)

fun FirFunctionCall.isPureCall(session: FirSession): Boolean =
    toResolvedCallableSymbol()?.isPure(session) == true

private fun FirElement.opensReadOnlyContext(session: FirSession): Boolean =
    when (this) {
        is FirFunction -> symbol.isPure(session)
        is FirFunctionCall -> toResolvedCallableSymbol()?.callableId in specificationFunctionIds
        else -> false
    }

/**
 * The elements of a graph's declaration that lie in a read-only context: the body of a `@Pure` function, or the
 * arguments of a specification builtin, including the lambdas passed to it.
 */
class ReadOnlyContext private constructor(
    private val coversDeclaration: Boolean,
    private val elements: Set<FirElement>,
) {
    operator fun contains(element: FirElement): Boolean =
        coversDeclaration || element in elements

    private class Collector(private val session: FirSession) : FirVisitorVoid() {
        val elements = mutableSetOf<FirElement>()
        private var depth = 0

        override fun visitElement(element: FirElement) {
            val opens = element.opensReadOnlyContext(session)

            if (opens) depth++
            if (depth > 0) elements.add(element)
            element.acceptChildren(this)
            if (opens) depth--
        }
    }

    companion object {
        /**
         * Resolves the read-only context of [graph], which is checked within [context].
         */
        fun of(graph: ControlFlowGraph, context: CheckerContext): ReadOnlyContext {
            val session = context.session
            val declaration = graph.declaration
            val enclosedByReadOnlyContext = context.containingElements.any { it.opensReadOnlyContext(session) }

            if (enclosedByReadOnlyContext || declaration?.opensReadOnlyContext(session) == true) {
                return ReadOnlyContext(coversDeclaration = true, elements = emptySet())
            }

            val collector = Collector(session)
            declaration?.accept(collector)

            return ReadOnlyContext(coversDeclaration = false, elements = collector.elements)
        }
    }
}
