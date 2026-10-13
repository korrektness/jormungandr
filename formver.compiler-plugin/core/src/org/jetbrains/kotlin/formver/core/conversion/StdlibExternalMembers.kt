/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.formver.common.ExternalSpecificationRegistry
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.common.externalSpecifications

/** Unbound operations in a specified stdlib family cannot use an opaque dependency signature. */
internal fun rejectUnsupportedStdlibMember(symbol: FirCallableSymbol<*>, session: FirSession) {
    if (symbol.origin == FirDeclarationOrigin.Source) return
    val registry = session.externalSpecifications ?: return
    if (!isSpecifiedStdlibFamily(symbol, session, registry)) return
    if (symbol is FirFunctionSymbol<*> && hasStdlibHofTemplate(symbol)) return
    throw UnsupportedFeatureException(
        symbol.source,
        "standard-library member ${symbol.callableId} without a supported embedding or external specification",
    )
}

private fun isSpecifiedStdlibFamily(
    symbol: FirCallableSymbol<*>,
    session: FirSession,
    registry: ExternalSpecificationRegistry,
): Boolean {
    val id = symbol.callableId ?: return false
    val receiver = symbol.resolvedReceiverTypeRef?.coneType ?: symbol.dispatchReceiverType
    return when (id.packageName.asString()) {
        "kotlin.collections" ->
            receiver.hasClassModel(session, registry) || symbol.resolvedReturnType.hasClassModel(session, registry)
        "kotlin.text" -> receiver?.let(registry::hasExtensionModel) == true
        "kotlin" -> id.className?.asString() == "String" && receiver?.let(registry::hasExtensionModel) == true
        else -> false
    }
}

private fun ConeKotlinType?.hasClassModel(session: FirSession, registry: ExternalSpecificationRegistry): Boolean =
    this?.toRegularClassSymbol(session)?.let(registry::modelClass) != null
