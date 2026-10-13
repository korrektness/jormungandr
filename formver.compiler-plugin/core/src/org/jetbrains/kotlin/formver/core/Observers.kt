/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core

import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.formver.common.externalSpecifications
import org.jetbrains.kotlin.formver.common.externalSpecificationSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.fir.unwrapUseSiteSubstitutionOverrides
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.fir.isIntersectionOverride
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.directOverriddenSymbolsSafe
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol

/** Immutable entry observations are values and require no receiver footprint. */
fun FirCallableSymbol<*>.isMapEntryValueObserver(session: FirSession): Boolean {
    val property = if (this is FirPropertyAccessorSymbol) propertySymbol else this as? FirPropertySymbol ?: return false
    if (!property.isVal || property.name.asString() !in setOf("key", "value")) return false
    val target = session.symbolProvider.getClassLikeSymbolByClassId(
        ClassId(FqName("kotlin.collections"), FqName("Map.Entry"), false),
    ) as? FirRegularClassSymbol ?: return false
    return property.externalSpecificationSymbol(session).getContainingClassSymbol() == session.externalSpecifications?.modelClass(target)
}

/** Whether this member is a pure abstract member or ghost observer declared in an interface. */
context(context: CheckerContext)
fun FirCallableSymbol<*>.isObserver(): Boolean =
    if (this is FirPropertyAccessorSymbol) propertySymbol.isObserver() else
    !isMapEntryValueObserver(context.session) &&
    (isAbstract || isGhostObserver(context.session)) && isPure(context.session) &&
            (this is FirNamedFunctionSymbol || (this is FirPropertySymbol && !isVar)) &&
            (getContainingClassSymbol() as? FirClassSymbol<*>)?.classKind?.isInterface == true

/** Whether this member is an observer or overrides one, directly or through other overrides. */
context(context: CheckerContext)
fun FirCallableSymbol<*>.isObserverOrOverride(): Boolean =
    if (this is FirPropertyAccessorSymbol) propertySymbol.isObserverOrOverride() else
    isObserver() || directOverriddenSymbolsSafe().any { it.isObserverOrOverride() }

/** Observer snapshots refer to declarations independently of call-site type arguments. */
context(context: CheckerContext)
fun FirFunctionSymbol<*>.observerDeclaration(): FirFunctionSymbol<*>? {
    val declaration = unwrapUseSiteSubstitutionOverrides()
    if (!declaration.isObserverOrOverride()) return null
    val inherited = declaration.unwrapFakeOverrides()
    val implementation = if (inherited.isIntersectionOverride) {
        inherited.directOverriddenSymbolsSafe().filterIsInstance<FirFunctionSymbol<*>>()
            .map { it.unwrapFakeOverrides() }.firstOrNull { !it.isAbstract } ?: inherited
    } else inherited
    return implementation.takeIf { it != this }
}
