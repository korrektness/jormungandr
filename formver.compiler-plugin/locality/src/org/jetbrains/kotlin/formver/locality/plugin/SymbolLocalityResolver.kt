/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.locality.plugin

import org.jetbrains.kotlin.formver.intrinsics.plugin.isStringBuilderClearReceiver
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.resolve.dfa.controlFlowGraph
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.fir.types.ConeErrorType
import org.jetbrains.kotlin.formver.readonly.plugin.isInSpecification
import org.jetbrains.kotlin.formver.readonly.plugin.isReadOnlyBorrowed
import org.jetbrains.kotlin.formver.type.plugin.SymbolTypeFactResolver

/**
 * Whether [function] is an inline function called from a specification. The call is inlined into an expression,
 * which reads its arguments and stores none of them.
 */
context(context: CheckerContext)
private fun isInlinedIntoSpecification(function: FirBasedSymbol<*>?): Boolean =
    function is FirFunctionSymbol<*> && function.isInline && context.isInSpecification()

context(context: CheckerContext)
fun FirReceiverParameterSymbol.resolveLocality(): Locality =
    if (isReadOnlyBorrowed(context.session) || isStringBuilderClearReceiver ||
        isInlinedIntoSpecification(containingDeclarationSymbol)
    ) Locality.Local else resolvedType.locality

object ReceiverLocalityResolver :
    SymbolTypeFactResolver<Locality, FirReceiverParameterSymbol> {
    context(context: CheckerContext)
    override fun resolveTypeFactOf(symbol: FirReceiverParameterSymbol): Locality =
        symbol.resolveLocality()
}

context(context: CheckerContext)
fun FirFunctionSymbol<*>.resolveScopeLocality(): Locality {
    val graph = resolvedControlFlowGraphReference?.controlFlowGraph
        ?: return Locality.Global

    return graph.resolveScopeLocality()
}

context(context: CheckerContext)
fun FirVariableSymbol<*>.resolveLocality(): Locality {
    if (resolvedReturnType is ConeErrorType) return Locality.Global

    if (this is FirValueParameterSymbol &&
        (isReadOnlyBorrowed(context.session) || isInlinedIntoSpecification(containingDeclarationSymbol))
    ) return Locality.Local

    if (resolvedReturnTypeRef.source?.kind !is KtFakeSourceElementKind.ImplicitTypeRef) {
        return resolvedReturnType.locality
    }

    return resolvedInitializer?.resolveLocality() ?: resolvedReturnType.locality
}

context(context: CheckerContext)
fun FirCallableSymbol<*>.resolveLocality(): Locality =
    when (this) {
        is FirVariableSymbol<*> -> resolveLocality()
        is FirFunctionSymbol<*> -> resolveScopeLocality()
        else -> Locality.Global
    }

object VariableLocalityResolver :
    SymbolTypeFactResolver<Locality, FirVariableSymbol<*>> {
    context(context: CheckerContext)
    override fun resolveTypeFactOf(symbol: FirVariableSymbol<*>): Locality =
        symbol.resolveLocality()
}
