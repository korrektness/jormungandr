/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.*

/** The JVM's representative bound of a cast to this type; generic arguments supply no runtime evidence. */
fun ConeKotlinType.erasedCastTarget(session: FirSession): ConeKotlinType {
    fun erase(t: ConeKotlinType): ConeKotlinType = when (val expanded = t.fullyExpandedType(session).lowerBoundIfFlexible()) {
        is ConeDefinitelyNotNullType -> erase(expanded.original)
        is ConeTypeParameterType -> {
            val bounds = expanded.lookupTag.typeParameterSymbol.resolvedBounds.map { it.coneType }
            val erased = bounds.map(::erase)
            erased.firstOrNull {
                (it.toClassSymbol(session) as? FirRegularClassSymbol)?.classKind != ClassKind.INTERFACE
            } ?: erased.first()
        }
        else -> expanded
    }
    return erase(this).withNullability(canBeNull(session), session.typeContext)
}
