/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyAccessor
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.formver.common.TrustedAssumption
import org.jetbrains.kotlin.formver.common.trustName
import org.jetbrains.kotlin.formver.core.embeddings.callables.NamedFunctionSignature
import org.jetbrains.kotlin.formver.common.externalSpecifications
import org.jetbrains.kotlin.formver.locality.plugin.neverConvert
import org.jetbrains.kotlin.formver.locality.plugin.neverVerify

/**
 * The trusted assumptions that a program relies on, beyond the declarations it verifies. [verifiedHere] tells whether
 * the program verifies a callee itself.
 */
class TrustedAssumptionCollector(
    private val session: FirSession,
    private val typeResolver: TypeResolver,
    private val verifiedHere: (FirCallableSymbol<*>) -> Boolean,
) {
    private val trusted = sortedSetOf<TrustedAssumption>()

    val assumptions: Set<TrustedAssumption> get() = trusted

    /** Records the external binding of [target]. */
    fun recordBinding(target: FirBasedSymbol<*>) {
        trusted += session.externalSpecifications!!.bindingAssumption(target)
    }

    /** Records [symbol], a callee or accessor that the program calls, when SnaKt does not verify it. */
    fun recordOptOut(symbol: FirCallableSymbol<*>, name: FirCallableSymbol<*> = symbol) {
        if (verifiedHere(symbol) || !symbol.neverVerify(session)) return
        val kind = if (symbol.neverConvert(session)) TrustedAssumption.Kind.NEVER_CONVERT else TrustedAssumption.Kind.NEVER_VERIFY
        trusted += TrustedAssumption(kind, name.trustName)
    }

    /** Records the custom accessors of [property] that SnaKt does not verify, under the property's name. */
    fun recordAccessorOptOuts(property: FirPropertySymbol) {
        for (accessor in listOfNotNull(property.getterSymbol, property.setterSymbol)) {
            @OptIn(SymbolInternals::class)
            if (accessor.fir !is FirDefaultPropertyAccessor) recordOptOut(accessor, property)
        }
    }

    /**
     * Records the contract that [StdLibConverter.kt] gives [signature], the signature of [symbol], when [symbol] is a
     * library callable. A source declaration verifies its own body against that contract. An external spec model is a
     * source declaration too: its contract comes from the spec, and [recordBinding] records it.
     */
    fun recordLibraryContract(symbol: FirCallableSymbol<*>, signature: NamedFunctionSignature) {
        if (symbol.origin == FirDeclarationOrigin.Source) return
        if (signature.stdLibPreconditions(typeResolver).isEmpty() &&
            signature.stdLibPostconditions(signature.returns, typeResolver).isEmpty()) return
        trusted += TrustedAssumption(TrustedAssumption.Kind.LIBRARY_CONTRACT, symbol.trustName)
    }
}
