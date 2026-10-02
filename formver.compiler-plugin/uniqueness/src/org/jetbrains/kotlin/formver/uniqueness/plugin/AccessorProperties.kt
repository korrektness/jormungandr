/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertySetter
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol

/**
 * Whether [this] property has a custom getter, or is a `var` with a custom setter.
 */
@OptIn(SymbolInternals::class)
val FirPropertySymbol.isCustom: Boolean
    get() {
        val getter = getterSymbol?.fir
        val setter = setterSymbol?.fir
        return if (isVal) getter !is FirDefaultPropertyGetter
        else getter !is FirDefaultPropertyGetter || setter !is FirDefaultPropertySetter
    }

/**
 * Whether every access to [this] property runs its default accessors: the property is final or declared in a final
 * class, and has no custom accessor. Any other access is an accessor call, which runtime dispatch may route to
 * arbitrary code.
 */
fun FirPropertySymbol.isGuaranteedDefault(session: FirSession): Boolean {
    val classSymbolFinal = dispatchReceiverType?.toClassSymbol(session)?.isFinal ?: false
    return (isFinal || classSymbolFinal) && !isCustom
}

/**
 * The property [this] expression reads or writes through an accessor call, or `null` when it accesses a local,
 * a field, or something other than a property.
 */
fun FirQualifiedAccessExpression.accessorCallProperty(session: FirSession): FirPropertySymbol? {
    val symbol = calleeReference.symbol as? FirPropertySymbol ?: return null
    return symbol.takeUnless { it.isLocal || it.isGuaranteedDefault(session) }
}
