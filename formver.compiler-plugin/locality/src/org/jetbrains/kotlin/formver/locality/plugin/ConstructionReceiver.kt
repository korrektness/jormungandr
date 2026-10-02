/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.locality.plugin

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirQualifiedAccessExpressionChecker
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertySetter
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousInitializerSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol

/**
 * The declarations nested inside the construction scope of [this] class: its constructor body, `init` block or
 * property initializer that encloses the checked element, innermost last. Null when no such scope encloses it.
 *
 * A constructor result is unique, so the receiver of a construction scope is local there.
 */
context(context: CheckerContext)
private fun FirBasedSymbol<*>.constructionScopeNesting(): List<FirBasedSymbol<*>>? {
    if (this !is FirRegularClassSymbol || classKind != ClassKind.CLASS) return null

    val declarations = context.containingDeclarations
    val classIndex = declarations.indexOf(this)
    if (classIndex < 0) return null

    val nested = declarations.subList(classIndex + 1, declarations.size)
    val isConstructionScope = when (nested.firstOrNull()) {
        // Declaration checkers of a member property run directly in its class.
        null, is FirConstructorSymbol, is FirAnonymousInitializerSymbol -> true
        is FirPropertySymbol -> nested.getOrNull(1) !is FirPropertyAccessorSymbol
        else -> false
    }

    return if (isConstructionScope) nested.drop(1) else null
}

context(context: CheckerContext)
fun FirBasedSymbol<*>.isUnderConstruction(): Boolean =
    constructionScopeNesting() != null

internal val FirExpression.thisReceiverSymbol: FirBasedSymbol<*>?
    get() = (this as? FirThisReceiverExpression)?.calleeReference?.symbol

@OptIn(SymbolInternals::class)
private val FirPropertySymbol.isField: Boolean
    get() = isFinal && getterSymbol?.fir.let { it == null || it is FirDefaultPropertyGetter } &&
            setterSymbol?.fir.let { it == null || it is FirDefaultPropertySetter }

/**
 * Rejects the uses of a receiver under construction that the locality of `this` does not cover: member calls, which
 * may store their dispatch receiver, and references from a nested class, whose instances may outlive the scope.
 */
object ConstructionReceiverChecker : FirQualifiedAccessExpressionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirQualifiedAccessExpression) {
        val callee = expression.toResolvedCallableSymbol()
        val dispatchReceiverSymbol = expression.dispatchReceiver?.thisReceiverSymbol

        if (dispatchReceiverSymbol != null && dispatchReceiverSymbol.isUnderConstruction()) {
            val isMemberCall = callee is FirFunctionSymbol<*> || (callee is FirPropertySymbol && !callee.isField)
            if (isMemberCall) {
                reporter.reportOn(expression.source, LocalityErrors.INVALID_CONSTRUCTION_RECEIVER, callee)
            }
        }

        val symbol = expression.thisReceiverSymbol ?: return
        val nestedClass = symbol.constructionScopeNesting()?.firstOrNull { it is FirClassSymbol<*> } ?: return
        reporter.reportOn(expression.source, LocalityErrors.INVALID_LOCALITY_CAPTURE, symbol, nestedClass)
    }
}
