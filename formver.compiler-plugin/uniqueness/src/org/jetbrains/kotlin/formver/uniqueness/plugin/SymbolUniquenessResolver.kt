/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.formver.common.externalSpecifications
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.formver.intrinsics.plugin.isGenericArrayFactory
import org.jetbrains.kotlin.formver.intrinsics.plugin.primitiveArrayClassIds

import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.common.externalSpecificationSymbol
import org.jetbrains.kotlin.formver.common.isLibraryDeclaration
import org.jetbrains.kotlin.fir.references.toResolvedFunctionSymbol
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirConstructor
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
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.fir.types.ConeErrorType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.locality.plugin.borrowsDispatchReceiver
import org.jetbrains.kotlin.formver.locality.plugin.ownsDispatchReceiver
import org.jetbrains.kotlin.formver.locality.plugin.receiverOwner
import org.jetbrains.kotlin.formver.readonly.plugin.postconditionsId
import org.jetbrains.kotlin.formver.type.plugin.SymbolTypeFactResolver
import org.jetbrains.kotlin.formver.type.plugin.collectTails
import org.jetbrains.kotlin.formver.type.plugin.removeCast

fun FirReceiverParameterSymbol.resolveUniqueness(): Uniqueness =
    resolvedType.scopeUniqueness

/**
 * Resolves the uniqueness of the result of [this] function as its declaration states it. A constructor's result is
 * a fresh object, unique unless the construction lets it escape or the constructor is a library constructor that no
 * model binds of a class that has a model.
 */
context(context: CheckerContext)
fun FirFunctionSymbol<*>.resolveResultUniqueness(): Uniqueness =
    if (this is FirConstructorSymbol) resolveConstructorResultUniqueness()
    else if (isGenericArrayFactory()) Uniqueness.Unique
    else externalSpecificationSymbol(context.session).resolvedReturnType.scopeUniqueness

/**
 * Resolves the uniqueness of [this] constructor's result: as its `@ExternalConstructor` model declares it; `Shared`
 * for another constructor of a class without source that is externally modelled or has such a supertype, since nothing
 * establishes that model's laws and observers for the new object, unless the class is a primitive array, which the
 * converter models itself; otherwise unique unless the construction lets the object escape.
 */
context(context: CheckerContext)
fun FirConstructorSymbol.resolveConstructorResultUniqueness(): Uniqueness {
    val model = externalSpecificationSymbol(context.session)
    return if (model != this) model.resolvedReturnType.scopeUniqueness
    else if (isLibraryDeclaration() && constructsModelledType(context.session)) Uniqueness.Shared
    else if (resolveConstructionEscape(context.session) == null) Uniqueness.Unique else Uniqueness.Shared
}

private fun FirConstructorSymbol.constructsModelledType(session: FirSession): Boolean {
    val classSymbol = resolvedReturnType.toRegularClassSymbol(session) ?: return false
    if (classSymbol.classId in primitiveArrayClassIds) return false
    return lookupSuperTypes(classSymbol, lookupInterfaces = true, deep = true, useSiteSession = session)
        .mapNotNull { it.toRegularClassSymbol(session) }
        .plus(classSymbol)
        .any { session.externalSpecifications?.modelClass(it) != null }
}

/**
 * Resolves the uniqueness of [this] variable: as its written type states it, or, for a variable declared without a
 * type, from its initializer. A property of the top level or of an object, declared without a type, is shared, since
 * any function can reach it.
 */
context(context: CheckerContext)
fun FirVariableSymbol<*>.resolveUniqueness(): Uniqueness {
    if (resolvedReturnType is ConeErrorType) return Uniqueness.Shared

    if (this is FirValueParameterSymbol) {
        resolvePostconditionResultUniqueness()?.let { return it }
    }

    if (resolvedReturnTypeRef.source?.kind !is KtFakeSourceElementKind.ImplicitTypeRef) {
        return resolvedReturnType.scopeUniqueness
    }

    if (this is FirPropertySymbol && isGloballyReachable()) return Uniqueness.Shared

    return resolvedInitializer?.resolveInitializerUniqueness() ?: Uniqueness.Shared
}

context(context: CheckerContext)
private fun FirPropertySymbol.isGloballyReachable(): Boolean {
    if (isLocal) return false
    val owner = callableId?.classId ?: return true
    if (owner.isLocal) return false
    return (context.session.symbolProvider.getClassLikeSymbolByClassId(owner) as? FirRegularClassSymbol)?.classKind == ClassKind.OBJECT
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
    val callUniquenesses = collectCallTails().map { it.resolveCallResultUniqueness() }
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
        symbol.externalSpecificationSymbol(context.session).resolvedReturnType.parameterUniqueness
}

object VariableUniquenessResolver : SymbolTypeFactResolver<Uniqueness, FirVariableSymbol<*>> {
    context(context: CheckerContext)
    override fun resolveTypeFactOf(symbol: FirVariableSymbol<*>): Uniqueness =
        symbol.resolveUniqueness()
}

/**
 * Resolves the uniqueness [this] member function declares for its dispatch receiver: unique when it owns it, unknown
 * when it only borrows it, as for a parameter.
 */
context(context: CheckerContext)
fun FirCallableSymbol<*>.resolveDispatchReceiverUniqueness(): Uniqueness =
    when {
        ownsDispatchReceiver(context.session) -> Uniqueness.Unique
        borrowsDispatchReceiver(context.session) -> Uniqueness.Unknown
        else -> Uniqueness.Shared
    }

/**
 * Resolves the uniqueness of `this` of [this] class from the member function that binds it. `this` is shared outside
 * member functions, as under construction.
 */
context(context: CheckerContext)
fun FirClassSymbol<*>.resolveReceiverUniqueness(): Uniqueness {
    val owner = receiverOwner() as? FirFunction ?: return Uniqueness.Shared
    return if (owner is FirConstructor) Uniqueness.Shared else owner.symbol.resolveDispatchReceiverUniqueness()
}

context(context: CheckerContext)
fun FirBasedSymbol<*>.resolveDeclaredUniqueness(): Uniqueness =
    when (this) {
        is FirVariableSymbol<*> -> externalSpecificationSymbol(context.session).resolveUniqueness()
        is FirReceiverParameterSymbol -> externalSpecificationSymbol(context.session).resolveUniqueness()
        is FirClassSymbol<*> -> resolveReceiverUniqueness()
        // A callable reference captures a function as a value, which nothing owns.
        is FirFunctionSymbol<*> -> Uniqueness.Shared
        else -> throw SnaktInternalException(null, "No declared uniqueness for $this.")
    }

/**
 * Resolves the uniqueness of [this] call's result: as the bound model declares it for a dependency function, else
 * from the call's type.
 */
context(context: CheckerContext)
fun FirFunctionCall.resolveCallResultUniqueness(): Uniqueness {
    // An unresolved callee is already a compiler error.
    val target = calleeReference.toResolvedFunctionSymbol() ?: return resolvedType.scopeUniqueness
    if (target.isGenericArrayFactory()) return Uniqueness.Unique
    val model = target.externalSpecificationSymbol(context.session)
    return if (model != target) model.resolvedReturnType.scopeUniqueness else resolvedType.scopeUniqueness
}
