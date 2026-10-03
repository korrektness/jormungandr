/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.locality.contract.plugin

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.locality.plugin.Locality
import org.jetbrains.kotlin.formver.locality.plugin.locality
import org.jetbrains.kotlin.formver.type.contract.plugin.FunctionTypeFact
import org.jetbrains.kotlin.formver.type.plugin.CallArgumentTypeFactsMapper
import org.jetbrains.kotlin.formver.type.plugin.ExpressionTypeFactResolver
import org.jetbrains.kotlin.formver.type.plugin.InvokeParameterTypeFactsResolver
import org.jetbrains.kotlin.formver.type.plugin.QualifiedAccessArgumentTypeFactMapper
import org.jetbrains.kotlin.formver.type.plugin.ReturnResultTypeFactResolver
import org.jetbrains.kotlin.formver.type.plugin.UnifyingExpressionTypeFactResolver

private object TerminalLocalityContractResolver : ExpressionTypeFactResolver<LocalityContract?> {
    context(context: CheckerContext)
    override fun resolveTypeFactOf(expression: FirExpression): LocalityContract? =
        when (expression) {
            is FirCallableReferenceAccess ->
                when (val symbol = expression.calleeReference.symbol) {
                    is FirVariableSymbol<*> -> symbol.resolveReferenceLocalityContract(expression.resolvedType)
                    else -> expression.resolvedType.resolveLocalityContract(context.session)
                }
            is FirQualifiedAccessExpression ->
                when (val symbol = expression.calleeReference.symbol) {
                    is FirFunctionSymbol<*> -> expression.resolvedType.resolveLocalityContract(context.session)
                    is FirCallableSymbol -> symbol.resolveLocalityContract()
                    is FirReceiverParameterSymbol -> symbol.resolveLocalityContract()
                    else -> null
                }
            is FirAnonymousFunctionExpression ->
                expression.resolvedType.resolveLocalityContract(context.session)
            else -> null
        }
}

/**
 * The contract of a reference to a variable is that of its getter: the receivers the reference leaves unbound, followed
 * by the variable's own contract as the result. A dispatch receiver is global, since an accessor may store it.
 */
context(context: CheckerContext)
private fun FirVariableSymbol<*>.resolveReferenceLocalityContract(referenceType: ConeKotlinType): LocalityContract {
    val receivers = listOfNotNull(
        dispatchReceiverType?.let { FunctionTypeFact.ParameterTypeFact(Locality.Global, null) },
        resolvedReceiverType?.let { type ->
            FunctionTypeFact.ParameterTypeFact(type.locality, type.resolveLocalityContract(context.session))
        },
    )
    // The reference type is `KPropertyN<receivers..., value>`; a bound reference has dropped its leading receiver.
    val unboundReceiverCount = referenceType.typeArguments.size - 1

    return FunctionTypeFact(
        parameterTypeFacts = receivers.takeLast(unboundReceiverCount),
        resultFunctionTypeFact = resolvedReturnType.resolveLocalityContract(context.session),
    )
}

class ExpressionLocalityContractResolver(session: FirSession) :
    ExpressionTypeFactResolver<LocalityContract?> by UnifyingExpressionTypeFactResolver(
        session.firCachesFactory,
        LocalityContractUnifier,
        TerminalLocalityContractResolver
    ), FirExtensionSessionComponent(session) {
    companion object : ExpressionTypeFactResolver<LocalityContract?> {
        fun getFactory(): Factory =
            Factory { session -> ExpressionLocalityContractResolver(session) }

        context(context: CheckerContext)
        override fun resolveTypeFactOf(expression: FirExpression): LocalityContract? =
            context.session.expressionLocalityContractResolver.resolveTypeFactOf(expression)
    }
}

private val FirSession.expressionLocalityContractResolver: ExpressionLocalityContractResolver
    by FirSession.sessionComponentAccessor()

context(context: CheckerContext)
fun FirExpression.resolveLocalityContract(): LocalityContract? =
    ExpressionLocalityContractResolver.resolveTypeFactOf(this)

object ReturnResultLocalityContractResolver : ReturnResultTypeFactResolver<LocalityContract?> {
    context(context: CheckerContext)
    override fun resolveResultTypeFactOf(expression: FirReturnExpression): LocalityContract? =
        expression.target.labeledElement.returnTypeRef.coneType.resolveLocalityContract(context.session)
}

private object InvokeParametersLocalityContractResolver : InvokeParameterTypeFactsResolver<LocalityContract?> {
    context(context: CheckerContext)
    override fun resolveInvokeParametersOf(receiver: FirExpression): List<LocalityContract?>? =
        receiver.resolveLocalityContract()?.parameterTypeFacts?.map { element -> element.functionTypeFact }
}

val CallArgumentLocalityContractsMapper = CallArgumentTypeFactsMapper(
    VariableLocalityContractResolver,
    InvokeParametersLocalityContractResolver
)

val QualifiedAccessArgumentLocalityContractsMapper = QualifiedAccessArgumentTypeFactMapper(
    ReceiverLocalityContractResolver,
    VariableLocalityContractResolver
)
