/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.readonly.plugin

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ControlFlowGraph
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.uniqueness.attribute.uniquenessAttribute
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
 * The builtins whose arguments are never run: specifications, and the ghost operations on permissions.
 */
private val specificationFunctionIds: Set<CallableId> =
    setOf(builtinId("preconditions"), postconditionsId, builtinId("loopInvariants"), builtinId("verify"),
        builtinId("forAll"), builtinId("exists"), builtinId("old"), builtinId("acc"), builtinId("fold"),
        builtinId("unfold"))

private val uniquePredClassId = ClassId(formverPluginPackage, Name.identifier("UniquePred"))

private val FirFunctionSymbol<*>.isUniquePredConstructor: Boolean
    get() = this is FirConstructorSymbol && resolvedReturnType.classId == uniquePredClassId

/**
 * Whether [this] call constructs a `UniquePred`. See `UniquePredPlacementChecker`.
 */
fun FirFunctionCall.isUniquePredConstruction(): Boolean =
    (toResolvedCallableSymbol() as? FirFunctionSymbol<*>)?.isUniquePredConstructor == true

fun FirBasedSymbol<*>.isPure(session: FirSession): Boolean =
    hasAnnotation(pureAnnotationId, session)

fun FirFunctionCall.isPureCall(session: FirSession): Boolean =
    toResolvedCallableSymbol()?.isPure(session) == true

private fun FirFunctionSymbol<*>.borrowsParameterOfType(type: ConeKotlinType, session: FirSession): Boolean =
    callableId in specificationFunctionIds || isUniquePredConstructor ||
            isPure(session) && type.attributes.uniquenessAttribute != null

/**
 * Whether [this] parameter is borrowed because its function only reads it: every parameter of a specification
 * builtin or of the `UniquePred` constructor, and every `@Unique` parameter of a `@Pure` function.
 */
fun FirValueParameterSymbol.isReadOnlyBorrowed(session: FirSession): Boolean {
    val function = containingDeclarationSymbol as? FirFunctionSymbol<*> ?: return false
    return function.borrowsParameterOfType(resolvedReturnType, session)
}

/**
 * Whether [this] receiver is borrowed because its function only reads it, as for [FirValueParameterSymbol]s.
 */
fun FirReceiverParameterSymbol.isReadOnlyBorrowed(session: FirSession): Boolean {
    val function = containingDeclarationSymbol as? FirFunctionSymbol<*> ?: return false
    return function.borrowsParameterOfType(resolvedType, session)
}

private fun FirElement.isSpecificationCall(): Boolean =
    this is FirFunctionCall && toResolvedCallableSymbol()?.callableId in specificationFunctionIds

/**
 * Whether the element checked in [this] context lies in the arguments of a specification builtin.
 */
fun CheckerContext.isInSpecification(): Boolean =
    containingElements.any { it.isSpecificationCall() }

private fun FirElement.opensReadOnlyContext(session: FirSession): Boolean =
    when (this) {
        is FirFunction -> symbol.isPure(session)
        else -> isSpecificationCall()
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

    private class Collector(private val opensContext: (FirElement) -> Boolean) : FirVisitorVoid() {
        val elements = mutableSetOf<FirElement>()
        private var depth = 0

        override fun visitElement(element: FirElement) {
            val opens = opensContext(element)

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
            return collect(graph, context) { it.opensReadOnlyContext(session) }
        }

        /**
         * Resolves the part of the read-only context of [graph] that lies in specifications: the arguments of
         * specification builtins, which are never run.
         */
        fun specificationsOf(graph: ControlFlowGraph, context: CheckerContext): ReadOnlyContext =
            collect(graph, context) { it.isSpecificationCall() }

        private fun collect(
            graph: ControlFlowGraph,
            context: CheckerContext,
            opensContext: (FirElement) -> Boolean,
        ): ReadOnlyContext {
            val declaration = graph.declaration
            val enclosedByContext = context.containingElements.any(opensContext)

            if (enclosedByContext || declaration?.let(opensContext) == true) {
                return ReadOnlyContext(coversDeclaration = true, elements = emptySet())
            }

            val collector = Collector(opensContext)
            declaration?.accept(collector)

            return ReadOnlyContext(coversDeclaration = false, elements = collector.elements)
        }
    }
}
