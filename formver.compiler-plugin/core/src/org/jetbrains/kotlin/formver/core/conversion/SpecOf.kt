/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.formver.common.externalFunctionId
import org.jetbrains.kotlin.formver.common.externalConstructorId
import org.jetbrains.kotlin.formver.common.externalPropertyId
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.impl.ConeTypeParameterTypeImpl
import org.jetbrains.kotlin.formver.core.annotationId
import org.jetbrains.kotlin.name.Name

val specOfId = annotationId("SpecOf")

/**
 * A specification together with the function it is written in: that function's value parameters are the ones the
 * specification names, and its uniqueness analysis is the one its reads are checked against.
 */
data class UserSpecification(val holder: FirSimpleFunction, val spec: FirSpecification)

/** The name a `@SpecOf` annotation on this function gives, or `null` when it has none. */
fun FirNamedFunctionSymbol.specOfTargetName(session: FirSession): String? =
    getAnnotationByClassId(specOfId, session)?.getStringArgument(Name.identifier("name"), session)

/** The [functionsDeclaredBeside] this one that have the name its `@SpecOf` gives and [hasParameterTypesOf] it. */
fun FirNamedFunctionSymbol.specOfTargets(session: FirSession): List<FirNamedFunctionSymbol> {
    val name = specOfTargetName(session) ?: return emptyList()
    return functionsDeclaredBeside(session).filter { it.name.asString() == name && hasParameterTypesOf(it, session) }
}

/**
 * The top-level `@ExternalProperty` models in this function's package that have the name its `@SpecOf` gives and that
 * this function [hasParameterTypesOf]. A function with value parameters names no property.
 */
fun FirNamedFunctionSymbol.specOfPropertyTargets(session: FirSession): List<FirPropertySymbol> {
    val name = specOfTargetName(session) ?: return emptyList()
    if (getContainingClassSymbol() != null || valueParameterSymbols.isNotEmpty()) return emptyList()
    return session.symbolProvider.getTopLevelPropertySymbols(callableId.packageName, Name.identifier(name))
        .filter { it.getAnnotationByClassId(externalPropertyId, session) != null && hasParameterTypesOf(it, session) }
}

/** The `@SpecOf` functions declared in the file of this `@ExternalProperty` model that name it. */
@OptIn(DirectDeclarationsAccess::class)
fun FirPropertySymbol.specOfSiblings(session: FirSession): List<FirNamedFunctionSymbol> {
    if (getAnnotationByClassId(externalPropertyId, session) == null) return emptyList()
    val file = session.firProvider.getFirCallableContainerFile(this) ?: return emptyList()
    return file.declarations.filterIsInstance<FirSimpleFunction>().map { it.symbol }
        .filter { it.specOfTargetName(session) == name.asString() && this in it.specOfPropertyTargets(session) }
}

/** The `@SpecOf` functions declared next to this function that name it. */
fun FirNamedFunctionSymbol.specOfSiblings(session: FirSession): List<FirNamedFunctionSymbol> {
    return functionsDeclaredBeside(session)
        .filter { it.specOfTargetName(session) == name.asString() && this in it.specOfTargets(session) }
}

/** Sibling members, or top-level declarations beside an external function model. */
@OptIn(DirectDeclarationsAccess::class)
fun FirNamedFunctionSymbol.functionsDeclaredBeside(session: FirSession): List<FirNamedFunctionSymbol> {
    val owner = getContainingClassSymbol() as? FirClassSymbol<*>
    if (owner != null) return owner.declarationSymbols.filterIsInstance<FirNamedFunctionSymbol>().filter { it != this }
    val targetName = specOfTargetName(session)
    if (targetName != null) return session.symbolProvider
        .getTopLevelFunctionSymbols(callableId.packageName, Name.identifier(targetName))
        .filter { it != this && it.isExternalFunctionModel(session) }
    if (!isExternalFunctionModel(session)) return emptyList()
    // In a multiplatform compilation the model can sit in a fragment this session depends on.
    val file = checkNotNull(moduleData.session.firProvider.getFirCallableContainerFile(this)) {
        "A source external function model must have a containing file."
    }
    return file.declarations.filterIsInstance<FirSimpleFunction>().map { it.symbol }.filter { it != this }
}

private fun FirNamedFunctionSymbol.isExternalFunctionModel(session: FirSession): Boolean =
    getAnnotationByClassId(externalFunctionId, session) != null || getAnnotationByClassId(externalConstructorId, session) != null

/**
 * Whether this function has the type parameters of [target] and, with its type parameters read as those of
 * [target], the same extension receiver and value parameter types. Ownership annotations are not compared.
 */
private fun FirNamedFunctionSymbol.hasParameterTypesOf(target: FirCallableSymbol<*>, session: FirSession): Boolean {
    val targetParameters = (target as? FirFunctionSymbol<*>)?.valueParameterSymbols.orEmpty()
    if (typeParameterSymbols.size != target.typeParameterSymbols.size) return false
    if (valueParameterSymbols.size != targetParameters.size) return false
    val substitutor = substitutorByMap(
        typeParameterSymbols.zip(target.typeParameterSymbols) { own, theirs ->
            own to ConeTypeParameterTypeImpl(theirs.toLookupTag(), isMarkedNullable = false)
        }.toMap(),
        session,
    )
    val receiver = resolvedReceiverTypeRef?.coneType
    val targetReceiver = target.resolvedReceiverTypeRef?.coneType
    if ((receiver == null) != (targetReceiver == null)) return false
    if (receiver != null && !substitutor.substituteOrSelf(receiver).sameUpToAttributes(targetReceiver!!)) return false
    return valueParameterSymbols.zip(targetParameters).all { (own, theirs) ->
        substitutor.substituteOrSelf(own.resolvedReturnType).sameUpToAttributes(theirs.resolvedReturnType)
    }
}

/** Compares a holder's type with the target's return type under positional type-parameter substitution. */
fun FirNamedFunctionSymbol.specificationResultMatches(type: ConeKotlinType, target: FirCallableSymbol<*>, session: FirSession): Boolean {
    val substitutor = substitutorByMap(
        typeParameterSymbols.zip(target.typeParameterSymbols) { own, theirs ->
            own to ConeTypeParameterTypeImpl(theirs.toLookupTag(), isMarkedNullable = false)
        }.toMap(), session,
    )
    return substitutor.substituteOrSelf(type).sameUpToAttributes(target.resolvedReturnType)
}

/** The specification written as the leading statements of this function's body, or `null` when it has none. */
fun FirSimpleFunction.ownSpecification(returnType: ConeKotlinType = symbol.resolvedReturnType): FirSpecification? {
    val body = body ?: return null
    return extractFirSpecification(body, returnType).takeIf { it.precond != null || it.postcond != null }
}

/**
 * The specification of this function: the one in its own body, else the one of the `@SpecOf` function declared
 * next to it, else `null`. A `@SpecOf` function is looked for only next to a source declaration.
 */
@OptIn(SymbolInternals::class)
fun FirSimpleFunction.userSpecification(session: FirSession): UserSpecification? {
    ownSpecification()?.let { return UserSpecification(this, it) }
    if (origin != FirDeclarationOrigin.Source) return null
    return symbol.specOfSiblings(session).singleOrNull()?.fir?.specificationOf(symbol, session)
}

/** The specification of this `@ExternalProperty` model's getter: the one of the `@SpecOf` function that names it. */
@OptIn(SymbolInternals::class)
fun FirPropertySymbol.userSpecification(session: FirSession): UserSpecification? =
    specOfSiblings(session).singleOrNull()?.fir?.specificationOf(this, session)

/** The specification this `@SpecOf` function gives [target]. */
private fun FirSimpleFunction.specificationOf(target: FirCallableSymbol<*>, session: FirSession): UserSpecification? {
    // A holder whose postconditions name another result type is reported by the `@SpecOf` checker.
    val resultType = body?.postconditionsResultType()
    if (resultType != null && !symbol.specificationResultMatches(resultType, target, session)) return null
    return ownSpecification(resultType ?: target.resolvedReturnType)?.let { UserSpecification(this, it) }
}
