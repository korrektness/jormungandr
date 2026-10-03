/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
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
    return functionsDeclaredBeside().filter { it.name.asString() == name && hasParameterTypesOf(it, session) }
}

/** The `@SpecOf` functions declared next to this function that name it. */
fun FirNamedFunctionSymbol.specOfSiblings(session: FirSession): List<FirNamedFunctionSymbol> {
    return functionsDeclaredBeside()
        .filter { it.specOfTargetName(session) == name.asString() && this in it.specOfTargets(session) }
}

/** The other member functions declared in the class that declares this function. */
@OptIn(DirectDeclarationsAccess::class)
fun FirNamedFunctionSymbol.functionsDeclaredBeside(): List<FirNamedFunctionSymbol> {
    val owner = getContainingClassSymbol() as? FirClassSymbol<*> ?: return emptyList()
    return owner.declarationSymbols.filterIsInstance<FirNamedFunctionSymbol>().filter { it != this }
}

/**
 * Whether this function has the type parameters of [target] and, with its type parameters read as those of
 * [target], the same value parameter types. Ownership annotations are not compared.
 */
private fun FirNamedFunctionSymbol.hasParameterTypesOf(target: FirNamedFunctionSymbol, session: FirSession): Boolean {
    if (typeParameterSymbols.size != target.typeParameterSymbols.size) return false
    if (valueParameterSymbols.size != target.valueParameterSymbols.size) return false
    val substitutor = substitutorByMap(
        typeParameterSymbols.zip(target.typeParameterSymbols) { own, theirs ->
            own to ConeTypeParameterTypeImpl(theirs.toLookupTag(), isMarkedNullable = false)
        }.toMap(),
        session,
    )
    return valueParameterSymbols.zip(target.valueParameterSymbols).all { (own, theirs) ->
        substitutor.substituteOrSelf(own.resolvedReturnType).sameUpToAttributes(theirs.resolvedReturnType)
    }
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
    val sibling = symbol.specOfSiblings(session).singleOrNull()?.fir ?: return null
    // A sibling whose postconditions name another result type is reported by the `@SpecOf` checker.
    val resultType = sibling.body?.postconditionsResultType()
    if (resultType != null && !resultType.sameUpToAttributes(symbol.resolvedReturnType)) return null
    return sibling.ownSpecification(symbol.resolvedReturnType)?.let { UserSpecification(sibling, it) }
}
