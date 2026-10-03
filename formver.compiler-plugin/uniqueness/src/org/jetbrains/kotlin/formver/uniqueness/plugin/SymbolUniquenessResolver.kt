/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.expressions.unwrapExpression
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.fir.types.ConeErrorType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.readonly.plugin.postconditionsId
import org.jetbrains.kotlin.formver.type.plugin.SymbolTypeFactResolver
import org.jetbrains.kotlin.formver.type.plugin.collectTails
import org.jetbrains.kotlin.formver.type.plugin.removeCast

fun FirReceiverParameterSymbol.resolveUniqueness(): Uniqueness =
    resolvedType.scopeUniqueness

/**
 * Resolves the uniqueness of the result of [this] function as its declaration states it. A constructor's result is
 * a fresh object, unique unless the construction lets it escape.
 */
context(context: CheckerContext)
fun FirFunctionSymbol<*>.resolveResultUniqueness(): Uniqueness =
    if (this is FirConstructorSymbol) resolveConstructorResultUniqueness() else resolvedReturnType.scopeUniqueness

context(context: CheckerContext)
fun FirConstructorSymbol.resolveConstructorResultUniqueness(): Uniqueness =
    if (resolveConstructionEscape(context.session) == null) Uniqueness.Unique else Uniqueness.Shared

context(context: CheckerContext)
fun FirVariableSymbol<*>.resolveUniqueness(): Uniqueness {
    if (resolvedReturnType is ConeErrorType) return Uniqueness.Shared

    if (this is FirValueParameterSymbol) {
        resolvePostconditionResultUniqueness()?.let { return it }
    }

    if (resolvedReturnTypeRef.source?.kind !is KtFakeSourceElementKind.ImplicitTypeRef) {
        return resolvedReturnType.scopeUniqueness
    }

    return resolvedInitializer?.resolveInitializerUniqueness() ?: Uniqueness.Shared
}

/**
 * Resolves the uniqueness that a local of inferred type takes from its initializer [this]: the join of the access
 * uniqueness of the paths it references and the result uniqueness of each function call among its tails. A
 * constructor call contributes nothing, so a local initialized from a constructor alone is Shared.
 */
context(context: CheckerContext)
private fun FirExpression.resolveInitializerUniqueness(): Uniqueness {
    val pathUniqueness = sequenceOf(this)
        .filter { it.resolveAccessState() != EmptyAccessState }
        .map { it.resolveAccessUniqueness() }
    val callUniquenesses = collectCallTails().map { it.resolvedType.scopeUniqueness }
    return (pathUniqueness + callUniquenesses).reduceOrNull(Uniqueness::join) ?: Uniqueness.Shared
}

private fun FirExpression.collectCallTails(): Sequence<FirFunctionCall> {
    val expression = unwrapExpression().removeCast()
    val tails = expression.collectTails()
    if (tails.any()) return tails.flatMap { it.collectCallTails() }
    val call = expression as? FirFunctionCall ?: return emptySequence()
    return if (call.calleeReference.symbol is FirConstructorSymbol) emptySequence() else sequenceOf(call)
}

/**
 * Resolves the uniqueness of [this] as the result parameter of a `postconditions` lambda, which is the result
 * uniqueness of the function the postconditions belong to. Returns null when [this] is not such a parameter.
 */
context(context: CheckerContext)
private fun FirValueParameterSymbol.resolvePostconditionResultUniqueness(): Uniqueness? {
    val lambda = containingDeclarationSymbol as? FirAnonymousFunctionSymbol ?: return null
    val function = context.containingElements
        .lastOrNull { it is FirFunction && it !is FirAnonymousFunction } as? FirFunction ?: return null
    if (lambda !in function.collectPostconditionLambdas()) return null

    return function.returnTypeRef.coneType.scopeUniqueness
}

private fun FirFunction.collectPostconditionLambdas(): Set<FirAnonymousFunctionSymbol> {
    val function = this
    val lambdas = mutableSetOf<FirAnonymousFunctionSymbol>()

    function.body?.accept(object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            if (element is FirFunction && element !is FirAnonymousFunction) return
            if (element is FirFunctionCall && element.toResolvedCallableSymbol()?.callableId == postconditionsId) {
                element.arguments.mapNotNullTo(lambdas) { argument ->
                    (argument.unwrapArgument() as? FirAnonymousFunctionExpression)?.anonymousFunction?.symbol
                }
            }
            element.acceptChildren(this)
        }
    })

    return lambdas
}

object ParameterUniquenessResolver: SymbolTypeFactResolver<Uniqueness, FirValueParameterSymbol> {
    context(context: CheckerContext)
    override fun resolveTypeFactOf(symbol: FirValueParameterSymbol): Uniqueness =
        symbol.resolvedReturnType.parameterUniqueness
}

object VariableUniquenessResolver : SymbolTypeFactResolver<Uniqueness, FirVariableSymbol<*>> {
    context(context: CheckerContext)
    override fun resolveTypeFactOf(symbol: FirVariableSymbol<*>): Uniqueness =
        symbol.resolveUniqueness()
}

context(context: CheckerContext)
fun FirBasedSymbol<*>.resolveDeclaredUniqueness(): Uniqueness =
    when (this) {
        is FirVariableSymbol<*> -> resolveUniqueness()
        is FirReceiverParameterSymbol -> resolveUniqueness()
        else -> Uniqueness.Shared
    }
