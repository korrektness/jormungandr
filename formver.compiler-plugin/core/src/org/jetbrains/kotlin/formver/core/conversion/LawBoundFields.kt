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
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.core.annotationId

// isLawReadableFrom gates subclass-law reads against this declaring-class lookup; see "Class laws" in SPECIFICATIONS.md.
@OptIn(SymbolInternals::class)
fun FirPropertySymbol.isLawBound(session: FirSession): Boolean {
    val field = unwrapFakeOverrides()
    if (field.hasAnnotation(annotationId("LawBound"), session)) return true
    val owner = field.getContainingClassSymbol() as? FirRegularClassSymbol ?: return false
    val expanded = mutableSetOf<FirFunctionSymbol<*>>()
    var read = false
    val visitor = object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            if (element is FirQualifiedAccessExpression) {
                val callee = element.toResolvedCallableSymbol()
                if (callee is FirPropertySymbol && callee.unwrapFakeOverrides() == field) read = true
                if (callee != null && callee.getContainingClassSymbol() != null) {
                    val body = classLawObserverBody(callee)
                    if (body != null && expanded.add(body)) body.fir.body?.accept(this)
                }
            }
            element.acceptChildren(this)
        }
    }
    owner.ownClassLaws(session).forEach { it.fir.body?.accept(visitor) }
    return read
}

@OptIn(SymbolInternals::class)
fun FirPropertySymbol.isLawReadableFrom(lawOwner: FirClassSymbol<*>, session: FirSession): Boolean {
    val field = unwrapFakeOverrides()
    return !field.fir.isVar || field.getContainingClassSymbol() == lawOwner || field.isLawBound(session)
}
