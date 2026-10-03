/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.impl.FirExpressionStub
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.embeddings.callables.CallableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.callables.insertCall
import org.jetbrains.kotlin.formver.core.embeddings.expression.Block
import org.jetbrains.kotlin.formver.core.embeddings.expression.Declare
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.utils.addIfNotNull

/**
 * Inserts a call to [callee] whose arguments are named out of parameter order or left to their defaults.
 *
 * The arguments are evaluated in Kotlin's order: receivers, the explicit arguments as written, then the default of each
 * omitted parameter in parameter order. A default may read the parameters before it, so it is converted with those
 * parameters bound to the arguments already evaluated, and with the callee's uniqueness facts, which own a path through
 * a `@Unique` parameter.
 */
@OptIn(SymbolInternals::class)
fun StmtConversionContext.insertCallWithMappedArguments(
    call: FirFunctionCall,
    symbol: FirFunctionSymbol<*>,
    callee: CallableEmbedding,
    returnType: TypeEmbedding,
): ExpEmbedding {
    val receivers = listOfNotNull(
        call.dispatchReceiver?.let { SubstitutedArgument.DispatchThis to it },
        call.extensionReceiver?.let { SubstitutedArgument.ExtensionThis to it },
    )
    val parameters = receivers.map { it.first } + symbol.valueParameterSymbols.map { SubstitutedArgument.ValueParameter(it) }
    val formalTypes = parameters.zip(callee.callableType.formalArgTypes).toMap()

    val declarations = mutableListOf<Declare>()
    val bound = mutableMapOf<SubstitutedArgument, ExpEmbedding>()
    fun bind(parameter: SubstitutedArgument, value: ExpEmbedding) {
        val (declaration, usage) = argumentDeclaration(value, formalTypes.getValue(parameter))
        declarations.addIfNotNull(declaration)
        bound[parameter] = usage
    }

    receivers.forEach { (parameter, receiver) -> bind(parameter, convert(receiver)) }
    call.resolvedArgumentMapping.orEmpty().forEach { (argument, parameter) ->
        if (argument is FirVarargArgumentsExpression) {
            throw UnsupportedFeatureException(argument.source, "vararg arguments to a function other than `verify`")
        }
        bind(SubstitutedArgument.ValueParameter(parameter.symbol), convert(argument))
    }
    val calleeAnalysis = uniquenessAnalysisOf(symbol)
    for (parameterSymbol in symbol.valueParameterSymbols) {
        val parameter = SubstitutedArgument.ValueParameter(parameterSymbol)
        if (parameter in bound) continue
        val defaultValue = parameterSymbol.fir.defaultValue
            ?: throw UnsupportedFeatureException(call.source, "omitted vararg argument")
        if (defaultValue is FirExpressionStub) {
            throw UnsupportedFeatureException(call.source, "default argument of a function compiled without its source")
        }
        val defaultCtx = MethodContextFactory(
            signature,
            InlineParameterResolver(bound.toMap(), symbol.name.asString(), defaultResolvedReturnTarget),
            ownershipFrame = ownershipFrame.forDefault(
                calleeAnalysis, call, ownershipFrame.scopeWith(retrievePropertiesAndParameters().toList())
            ),
            parent = this,
        )
        bind(parameter, withMethodCtx(defaultCtx) { convert(defaultValue) })
    }

    val result = withCallSite(call) { callee.insertCall(parameters.map { bound.getValue(it) }, this, returnType) }
    return if (declarations.isEmpty()) result else Block {
        addAll(declarations)
        add(result)
    }
}
