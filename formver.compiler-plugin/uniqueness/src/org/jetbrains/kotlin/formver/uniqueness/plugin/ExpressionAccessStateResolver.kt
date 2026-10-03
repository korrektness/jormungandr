/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.formver.locality.plugin.receiverTemporaryInitializer
import org.jetbrains.kotlin.formver.intrinsics.plugin.aliasedStringBuilderReceiver
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirSafeCallExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.formver.type.plugin.ExpressionTypeFactResolver
import org.jetbrains.kotlin.formver.type.plugin.UnifyingExpressionTypeFactResolver

/**
 * Resolves the access-state contributed by [this] when it is the terminal expression of a larger expression. A
 * `StringBuilder` intrinsic that returns its receiver contributes the access-state of its receiver.
 */
context(context: CheckerContext)
fun FirExpression.resolveTerminalAccessState(): AccessState {
    aliasedStringBuilderReceiver(context.session)?.let { return it.resolveAccessState() }
    return when (this) {
        is FirQualifiedAccessExpression -> {
            when (val symbol = calleeReference.symbol) {
                is FirReceiverParameterSymbol -> {
                    EmptyAccessState.putChild(symbol, AccessState(Access.Terminal))
                }
                // `this` is a path only where it is not shared, so that a shared `this` leaves the paths through it
                // as they are outside member functions.
                is FirClassSymbol<*> ->
                    if (symbol.resolveReceiverUniqueness() == Uniqueness.Shared) EmptyAccessState
                    else EmptyAccessState.putChild(symbol, AccessState(Access.Terminal))
                is FirVariableSymbol<*> -> {
                    symbol.receiverTemporaryInitializer?.let { return it.resolveAccessState() }
                    val receiverState = pathReceiver
                        ?.resolveAccessState()
                        ?: EmptyAccessState

                    receiverState.append(EmptyAccessState.putChild(symbol, AccessState(Access.Terminal)))
                }
                else -> EmptyAccessState
            }
        }
        is FirVarargArgumentsExpression ->
            arguments.fold(EmptyAccessState) { state, argument -> state.join(argument.resolveAccessState()) }
        is FirSafeCallExpression -> {
            val selector = selector

            return if (selector is FirExpression) {
                selector.resolveAccessState()
            } else {
                EmptyAccessState
            }
        }
        else -> EmptyAccessState
    }
}

/**
 * Resolves the access-state of an expression by joining the access-states of its tail subexpressions.
 */
class ExpressionAccessStateResolver(session: FirSession) :
    FirExtensionSessionComponent(session),
    ExpressionTypeFactResolver<AccessState> by UnifyingExpressionTypeFactResolver(
        session.firCachesFactory,
        AccessState::join,
        { expression -> expression.resolveTerminalAccessState() }
    ) {
    companion object {
        fun getFactory(): Factory {
            return Factory { session -> ExpressionAccessStateResolver(session) }
        }
    }
}

private val FirSession.expressionAccessStateResolver: ExpressionAccessStateResolver
        by FirSession.sessionComponentAccessor()

/**
 * Resolves the paths in the current expression that refer to mutable uniqueness state.
 */
context(context: CheckerContext)
fun FirExpression.resolveAccessState(): AccessState {
    return context.session.expressionAccessStateResolver.resolveTypeFactOf(this)
}
