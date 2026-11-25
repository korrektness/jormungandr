/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.formver.common.externalSpecificationSymbol
import org.jetbrains.kotlin.formver.locality.plugin.Locality
import org.jetbrains.kotlin.formver.locality.plugin.resolveLocality
import org.jetbrains.kotlin.formver.locality.plugin.specifiesOwnedReceiver
import org.jetbrains.kotlin.util.OperatorNameConventions

/** The receiver whose ownership gates use of the external model for the resolved [callee], if any. */
context(context: CheckerContext)
fun FirQualifiedAccessExpression.receiverRequiringModelOwnership(callee: FirCallableSymbol<*>): FirExpression? {
    val dispatch = dispatchReceiver
    if (dispatch != null && callee.specifiesOwnedReceiver(context.session)) return dispatch

    val extension = extensionReceiver ?: return null
    val model = callee.externalSpecificationSymbol(context.session)
    return extension.takeIf {
        model != callee && model.receiverParameterSymbol?.resolveDeclaredUniqueness() == Uniqueness.Unique
    }
}

/**
 * Ownership supplied by the unchecked path of a receiver-gated access. Only the synthetic iterator for a `for`
 * loop and an extension whose model returns unique without consuming any input supply an owned result.
 */
context(context: CheckerContext)
fun FirQualifiedAccessExpression.uncheckedModelResultUniqueness(callee: FirCallableSymbol<*>): Uniqueness =
    if (isForLoopIterator(callee) || callee.specifiesFreshExtensionResult()) Uniqueness.Unique else Uniqueness.Shared

private fun FirQualifiedAccessExpression.isForLoopIterator(callee: FirCallableSymbol<*>): Boolean =
    source?.kind == KtFakeSourceElementKind.DesugaredForLoop && callee.name == OperatorNameConventions.ITERATOR

context(context: CheckerContext)
private fun FirCallableSymbol<*>.specifiesFreshExtensionResult(): Boolean {
    val model = externalSpecificationSymbol(context.session) as? FirFunctionSymbol<*> ?: return false
    if (model == this || dispatchReceiverType != null) return false
    return model.resolveResultUniqueness() == Uniqueness.Unique &&
        (listOfNotNull(model.receiverParameterSymbol) + model.contextParameterSymbols + model.valueParameterSymbols)
            .none { it.resolveDeclaredUniqueness() == Uniqueness.Unique && !it.isBorrowedParameter() }
}

context(context: CheckerContext)
private fun FirBasedSymbol<*>.isBorrowedParameter(): Boolean = when (this) {
    is FirReceiverParameterSymbol -> resolveLocality() == Locality.Local
    is FirVariableSymbol<*> -> resolveLocality() == Locality.Local
    else -> false
}
