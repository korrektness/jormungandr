/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.core.annotationId
import org.jetbrains.kotlin.formver.uniqueness.plugin.isGuaranteedDefault

/** Visits each access in the bodies of [laws], expanding the bodies of the class members they reach. */
@OptIn(SymbolInternals::class)
private fun forEachLawAccess(laws: List<FirNamedFunctionSymbol>, action: (FirCallableSymbol<*>) -> Unit) {
    val expanded = mutableSetOf<FirFunctionSymbol<*>>()
    val visitor = object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            if (element is FirQualifiedAccessExpression) {
                val callee = element.toResolvedCallableSymbol()
                if (callee != null) action(callee)
                if (callee != null && callee.getContainingClassSymbol() != null) {
                    val body = classLawObserverBody(callee)
                    if (body != null && expanded.add(body)) body.fir.body?.accept(this)
                }
            }
            element.acceptChildren(this)
        }
    }
    laws.forEach { it.fir.body?.accept(visitor) }
}

// isLawReadableFrom gates subclass-law reads against this declaring-class lookup; see "Class laws" in SPECIFICATIONS.md.
fun FirPropertySymbol.isLawBound(session: FirSession): Boolean {
    val field = unwrapFakeOverrides()
    if (field.hasAnnotation(annotationId("LawBound"), session)) return true
    val owner = field.getContainingClassSymbol() as? FirRegularClassSymbol ?: return false
    var read = false
    forEachLawAccess(owner.ownClassLaws(session)) { callee ->
        if (callee is FirPropertySymbol && callee.unwrapFakeOverrides() == field) read = true
    }
    return read
}

/**
 * Whether [this] stored property is read and written through accessor methods rather than as a field: its getter is
 * the default one, but a custom setter or an override can replace its accessors.
 */
@OptIn(SymbolInternals::class)
fun FirPropertySymbol.hasAccessorMethods(session: FirSession): Boolean =
    fir.backingField != null && fir.getter?.body == null && !isGuaranteedDefault(session)

/** Whether [this] law reads a property with accessor methods, directly or through the observers it expands. */
fun FirNamedFunctionSymbol.readsAccessorProperty(session: FirSession): Boolean {
    var read = false
    forEachLawAccess(listOf(this)) { callee ->
        if (callee is FirPropertySymbol && callee.hasAccessorMethods(session)) read = true
    }
    return read
}

@OptIn(SymbolInternals::class)
fun FirPropertySymbol.isLawReadableFrom(lawOwner: FirClassSymbol<*>, session: FirSession): Boolean {
    val field = unwrapFakeOverrides()
    return !field.fir.isVar || field.getContainingClassSymbol() == lawOwner || field.isLawBound(session)
}
