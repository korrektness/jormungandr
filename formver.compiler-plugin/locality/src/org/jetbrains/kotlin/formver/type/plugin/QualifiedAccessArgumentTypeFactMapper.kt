package org.jetbrains.kotlin.formver.type.plugin

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol

/**
 * Resolves the type fact that [access] to [callee] requires of its dispatch receiver, or null when it requires none.
 */
fun interface DispatchReceiverTypeFactResolver<TypeFact> {
    context(context: CheckerContext)
    fun resolveTypeFactOf(access: FirQualifiedAccessExpression, callee: FirCallableSymbol<*>): TypeFact?
}

class QualifiedAccessArgumentTypeFactMapper<TypeFact>(
    private val receiverSymbolTypeFactResolver: SymbolTypeFactResolver<TypeFact, FirReceiverParameterSymbol>,
    private val contextSymbolTypeFactResolver: SymbolTypeFactResolver<TypeFact, FirVariableSymbol<*>>,
    private val dispatchReceiverTypeFactResolver: DispatchReceiverTypeFactResolver<TypeFact>? = null,
) {
    context(_: CheckerContext)
    fun mapArgumentTypeFactsOf(expression: FirQualifiedAccessExpression): List<Pair<FirExpression, TypeFact>> {
        val callableSymbol = expression.toResolvedCallableSymbol() ?: return emptyList()
        val result = mutableListOf<Pair<FirExpression, TypeFact>>()

        val dispatchReceiver = expression.dispatchReceiver
        if (dispatchReceiver != null && dispatchReceiverTypeFactResolver != null) {
            dispatchReceiverTypeFactResolver.resolveTypeFactOf(expression, callableSymbol)?.let { result += dispatchReceiver to it }
        }

        val receiver = expression.extensionReceiver
        val receiverSymbol = callableSymbol.receiverParameterSymbol

        if (receiver != null && receiverSymbol != null) {
            result += receiver to receiverSymbolTypeFactResolver.resolveTypeFactOf(receiverSymbol)
        }

        for ((argument, argumentSymbol) in expression.contextArguments.zip(callableSymbol.contextParameterSymbols)) {
            result += argument to contextSymbolTypeFactResolver.resolveTypeFactOf(argumentSymbol)
        }

        return result
    }
}
