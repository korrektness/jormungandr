/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isArrayType
import org.jetbrains.kotlin.fir.types.isNothingOrNullableNothing
import org.jetbrains.kotlin.fir.types.isNullableString
import org.jetbrains.kotlin.fir.types.isPrimitiveOrNullablePrimitive
import org.jetbrains.kotlin.fir.types.isSomeFunctionType
import org.jetbrains.kotlin.fir.types.isString
import org.jetbrains.kotlin.fir.types.isUnitOrNullableUnit
import org.jetbrains.kotlin.fir.unwrapFakeOverrides

/** Origins of declarations read from compiled dependencies, as opposed to sources and members generated for them. */
val libraryOrigins = setOf(
    FirDeclarationOrigin.Library, FirDeclarationOrigin.Java.Library, FirDeclarationOrigin.BuiltIns,
    FirDeclarationOrigin.BuiltInsFallback, FirDeclarationOrigin.Precompiled, FirDeclarationOrigin.Enhancement,
)

/** Whether [this] callable, unwrapped from fake overrides, is read from a compiled dependency. */
fun FirCallableSymbol<*>.isLibraryDeclaration(): Boolean = unwrapFakeOverrides().origin in libraryOrigins

/**
 * Whether [this] callable, or the property [this] accessor belongs to, is a library member of `kotlin` or a
 * `kotlin.*` package without an external specification that no override replaces, and that cannot keep a reference
 * to its receiver through its result or parameters. It is final, a property is a `val`, it has no context
 * parameters, the result and every value parameter are a primitive, `String`, `Unit` or `Nothing` (nullable or not),
 * no receiver has a function type, and some receiver can be owned and is not modelled by SnaKt itself: it is none of
 * those types, an array or an object. SnaKt assumes such a member keeps no reference to its receiver or arguments,
 * so each of them is borrowed (SPECIFICATIONS.md, "Verification Control").
 */
fun FirBasedSymbol<*>.isNonRetainingStdlibMember(session: FirSession): Boolean {
    val callable = when (this) {
        is FirPropertyAccessorSymbol -> propertySymbol
        is FirCallableSymbol<*> -> this
        else -> return false
    }.unwrapFakeOverrides()
    if (!callable.isUnmodelledFinalStdlibMember(session)) return false
    val parameters = (callable as? FirFunctionSymbol<*>)?.valueParameterSymbols.orEmpty().map { it.resolvedReturnType }
    if (!(parameters + callable.resolvedReturnType).all { it.isNonAliasing }) return false
    val receivers = listOfNotNull(callable.resolvedReceiverTypeRef?.coneType, callable.dispatchReceiverType)
    return receivers.none { it.isSomeFunctionType(session) } && receivers.any { it.isOwnableIn(session) }
}

private fun FirCallableSymbol<*>.isUnmodelledFinalStdlibMember(session: FirSession): Boolean {
    val packageName = callableId?.packageName?.asString() ?: return false
    return isLibraryDeclaration() && (packageName == "kotlin" || packageName.startsWith("kotlin.")) &&
        externalSpecificationSymbol(session) == this && resolvedStatus.modality == Modality.FINAL &&
        (this !is FirPropertySymbol || isVal) && contextParameterSymbols.isEmpty()
}

private fun ConeKotlinType.isOwnableIn(session: FirSession): Boolean =
    !isNonAliasing && !isArrayType && toRegularClassSymbol(session)?.classKind != ClassKind.OBJECT

private val ConeKotlinType.isNonAliasing: Boolean
    get() = isPrimitiveOrNullablePrimitive || isString || isNullableString || isUnitOrNullableUnit || isNothingOrNullableNothing
