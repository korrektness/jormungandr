/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.directOverriddenFunctionsSafe
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.resolve.FirSamResolver
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.utils.isInterface
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid

context(context: CheckerContext)
private fun FirNamedFunctionSymbol.overridesGhostObserver(): Boolean =
    directOverriddenFunctionsSafe().any {
        val original = it.unwrapFakeOverrides()
        original.isGhostObserver(context.session) || original.overridesGhostObserver()
    }

/** The literal lambda converted by this expression to a fun interface with ghost observers. */
context(context: CheckerContext)
@OptIn(SymbolInternals::class)
fun FirElement.specifiedLambda(): FirAnonymousFunction? {
    val argument = when (this) {
        is FirSamConversionExpression -> expression
        is FirFunctionCall -> if (toResolvedCallableSymbol()?.origin == FirDeclarationOrigin.SamConstructor)
            arguments.singleOrNull() else null
        else -> null
    }
    val lambda = (argument?.unwrapArgument() as? FirAnonymousFunctionExpression)?.anonymousFunction ?: return null
    if (!lambda.isLambda) return null
    val spec = (this as FirExpression).resolvedType.toRegularClassSymbol(context.session) ?: return null
    if (!spec.fir.isInterface || !spec.fir.status.isFun) return null
    val samResolver = FirSamResolver(context.session, context.scopeSession)
    if (samResolver.getSamInfoForPossibleSamType(resolvedType) == null) return null
    val scope = spec.unsubstitutedScope(context.session, context.scopeSession, false, FirResolvePhase.BODY_RESOLVE)
    var hasObservers = false
    var invalidObserver = false
    for (name in scope.getCallableNames()) scope.processFunctionsByName(name) {
        val original = it.unwrapFakeOverrides()
        if (original.overridesGhostObserver()) {
            invalidObserver = true
        }
        if (original.isGhostObserver(context.session)) {
            hasObservers = true
            if (original.fir.ghostObserverViolation() != null) invalidObserver = true
        }
    }
    return lambda.takeIf { hasObservers && !invalidObserver }
}

/** The specified lambdas nested in this element. */
context(context: CheckerContext)
fun FirElement.specifiedLambdas(): Set<FirAnonymousFunction> {
    val result = mutableSetOf<FirAnonymousFunction>()
    accept(object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            element.specifiedLambda()?.let(result::add)
            element.acceptChildren(this)
        }
    })
    return result
}
