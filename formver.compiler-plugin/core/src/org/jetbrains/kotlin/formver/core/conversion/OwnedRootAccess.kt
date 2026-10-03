/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.expressions.FirDesugaredAssignmentValueReferenceExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirSmartCastExpression
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.core.isBorrowed
import org.jetbrains.kotlin.formver.core.isUnique
import org.jetbrains.kotlin.formver.uniqueness.plugin.indexedArrayInitializer
import org.jetbrains.kotlin.formver.uniqueness.plugin.intArraySetId

/*
 * Code converted without the uniqueness checker's state, such as a default argument or the body of an inline function,
 * treats every path as shared: it havocs a read and drops a write. Through a `@Unique` or `@Borrowed` parameter or
 * receiver of the function that code belongs to, the caller may own the path, so the functions below find such accesses
 * for the call to be rejected.
 */

/** The `@Unique` value parameters and extension receiver of [function]. */
fun StmtConversionContext.uniqueRootsOf(function: FirFunctionSymbol<*>): List<FirBasedSymbol<*>> =
    context(checkerContext) { function.rootsOf().filter { it.isUnique() } }

/**
 * Reports unsupported ownership at [call] when the body of the inline function [function] assigns a property or an
 * `IntArray` element through one of its `@Unique` or `@Borrowed` parameters or its receiver annotated so.
 */
fun StmtConversionContext.rejectInlineWriteThroughOwnedRoot(call: FirFunctionCall, function: FirFunctionSymbol<*>, body: FirElement) {
    val roots = context(checkerContext) { function.rootsOf().filter { it.isUnique() || it.isBorrowed() } }
    if (body.writesThroughRoot(function, roots)) {
        reportUnsupportedOwnership(
            call.source, "An inline function may not write through its @Unique or @Borrowed parameter or receiver."
        )
    }
}

private fun FirFunctionSymbol<*>.rootsOf(): List<FirBasedSymbol<*>> = valueParameterSymbols + listOfNotNull(receiverParameterSymbol)

/** Whether this expression reads a property through one of [roots], parameters or the receiver of [function]. */
fun FirExpression.readsThroughRoot(function: FirFunctionSymbol<*>, roots: List<FirBasedSymbol<*>>): Boolean =
    containsAccess(roots) { element ->
        element is FirPropertyAccessExpression && element.receiverRoot(function) in roots
    }

/**
 * Whether this element assigns a property or an `IntArray` element through one of [roots], parameters or the receiver
 * of [function].
 */
private fun FirElement.writesThroughRoot(function: FirFunctionSymbol<*>, roots: List<FirBasedSymbol<*>>): Boolean =
    containsAccess(roots) { element ->
        when (element) {
            is FirVariableAssignment -> {
                val lValue = element.lValue
                val access = (lValue as? FirDesugaredAssignmentValueReferenceExpression)?.expressionRef?.value ?: lValue
                access is FirPropertyAccessExpression && access.receiverRoot(function) in roots
            }
            is FirFunctionCall -> element.toResolvedCallableSymbol()?.callableId == intArraySetId &&
                    element.dispatchReceiver?.rootSymbol().forFunction(function) in roots
            else -> false
        }
    }

private fun FirElement.containsAccess(roots: List<FirBasedSymbol<*>>, isAccess: (FirElement) -> Boolean): Boolean {
    if (roots.isEmpty()) return false
    var found = false
    accept(object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            if (found) return
            if (isAccess(element)) {
                found = true
                return
            }
            element.acceptChildren(this)
        }
    })
    return found
}

/**
 * The root of the path of this access's receiver, with `this` of [function] standing for its receiver parameter. Null
 * for an access without a receiver.
 */
private fun FirPropertyAccessExpression.receiverRoot(function: FirFunctionSymbol<*>): FirBasedSymbol<*>? =
    (dispatchReceiver ?: extensionReceiver)?.rootSymbol().forFunction(function)

private fun FirBasedSymbol<*>?.forFunction(function: FirFunctionSymbol<*>): FirBasedSymbol<*>? =
    if (this == function) function.receiverParameterSymbol else this

/**
 * The parameter, receiver or local at the root of the property path this expression denotes. The `<array>` temporary
 * of a compound index assignment stands for its initializer.
 */
private fun FirExpression.rootSymbol(): FirBasedSymbol<*>? = when (this) {
    is FirSmartCastExpression -> originalExpression.rootSymbol()
    is FirThisReceiverExpression -> calleeReference.boundSymbol as FirBasedSymbol<*>?
    is FirPropertyAccessExpression -> (dispatchReceiver ?: extensionReceiver)?.rootSymbol()
        ?: calleeReference.symbol?.let { symbol -> symbol.indexedArrayInitializer?.rootSymbol() ?: symbol }
    else -> null
}
