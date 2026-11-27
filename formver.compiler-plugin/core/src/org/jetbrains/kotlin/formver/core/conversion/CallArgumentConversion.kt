/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.formver.core.parameterArguments
import org.jetbrains.kotlin.formver.core.signatureParameterSymbols
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.impl.FirExpressionStub
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.unwrapSubstitutionOverrides
import org.jetbrains.kotlin.formver.core.boundDispatchReceiver
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
 * The arguments are evaluated in Kotlin's order: receivers, context arguments, the explicit arguments as written, then
 * the default of each omitted parameter in parameter order; an omitted vararg parameter without a default receives an
 * empty array. Deferred null checks follow the defaults, except that a generated copy checks its receiver before reading
 * its properties for the defaults. Generated copy defaults are property reads from the original FIR declaration, even
 * when FIR substitutes the call signature. A default may read the parameters before it, so
 * it is converted with those parameters bound to the arguments already evaluated, and with the callee's uniqueness
 * facts, which own a path through a `@Unique` parameter. An argument omitted for a function compiled without its source
 * follows SPECIFICATIONS.md, "Inline functions and default arguments".
 */
@OptIn(SymbolInternals::class)
fun StmtConversionContext.insertCallWithMappedArguments(
    call: FirFunctionCall,
    symbol: FirFunctionSymbol<*>,
    callee: CallableEmbedding,
    returnType: TypeEmbedding,
): ExpEmbedding {
    val original = symbol.unwrapSubstitutionOverrides()
    val generatedCopy = original.origin == FirDeclarationOrigin.Synthetic.DataClassMember && original.name.asString() == "copy"
    val defaultSource = if (generatedCopy) original else symbol
    val arguments = call.parameterArguments(symbol)
    val readsCopyDefaults = generatedCopy && arguments.size < symbol.signatureParameterSymbols.size
    val receivers = listOfNotNull(
        call.boundDispatchReceiver?.let { SubstitutedArgument.DispatchThis to it },
        call.extensionReceiver?.let { SubstitutedArgument.ExtensionThis to it },
    )
    val parameters = receivers.map { it.first } + symbol.signatureParameterSymbols.map { SubstitutedArgument.ValueParameter(it) }
    val formalTypes = parameters.zip(callee.callableType.formalArgTypes).toMap()

    val declarations = mutableListOf<Declare>()
    val bound = mutableMapOf<SubstitutedArgument, ExpEmbedding>()
    val deferredChecks = mutableMapOf<SubstitutedArgument, ConeKotlinType>()
    fun bind(parameter: SubstitutedArgument, operand: CallOperand) {
        val type = if (operand.deferredCheck == null) formalTypes.getValue(parameter) else operand.value.type
        val (declaration, usage) = argumentDeclaration(
            operand.value, type, snapshotMutableReference = readsCopyDefaults && parameter == SubstitutedArgument.DispatchThis
        )
        declarations.addIfNotNull(declaration)
        bound[parameter] = usage
        operand.deferredCheck?.let { deferredChecks[parameter] = it }
    }
    fun bind(parameter: SubstitutedArgument, value: ExpEmbedding) = bind(parameter, CallOperand(null, value))

    val expected = call.declaredArgumentTypes(symbol)
    receivers.forEach { (parameter, receiver) -> bind(parameter, operand(receiver, expected[receiver])) }
    arguments.forEach { (parameterSymbol, argument) ->
        val parameter = SubstitutedArgument.ValueParameter(parameterSymbol)
        if (argument is FirVarargArgumentsExpression) bind(parameter, convertVarargArray(call, argument, formalTypes.getValue(parameter)))
        else bind(parameter, operand(argument, expected[argument]))
    }
    if (readsCopyDefaults) {
        val dispatch = SubstitutedArgument.DispatchThis
        deferredChecks.remove(dispatch)?.let { bind(dispatch, checkedCast(bound.getValue(dispatch), embedType(it))) }
    }
    val calleeAnalysis = uniquenessAnalysisOf(symbol)
    for ((parameterSymbol, sourceParameter) in symbol.signatureParameterSymbols.zip(defaultSource.signatureParameterSymbols)) {
        val parameter = SubstitutedArgument.ValueParameter(parameterSymbol)
        if (parameter in bound) continue
        val defaultValue = sourceParameter.fir.defaultValue
        if (defaultValue == null) {
            bind(parameter, omittedArgument(call, parameterSymbol, formalTypes.getValue(parameter)))
            continue
        }
        if (defaultValue is FirExpressionStub) {
            bind(parameter, unconstrainedValue(formalTypes.getValue(parameter)))
            continue
        }
        val defaultCtx = MethodContextFactory(
            signature,
            InlineParameterResolver(bound.toMap(), symbol.name.asString(), defaultResolvedReturnTarget),
            ownershipFrame = ownershipFrame.forDefault(
                calleeAnalysis, call, ownershipFrame.scopeWith(retrievePropertiesAndParameters().toList())
            ),
            parent = this,
        )
        bind(parameter, withMethodCtx(defaultCtx) { convertExpecting(defaultValue, parameterSymbol.resolvedReturnType) })
    }

    deferredChecks.forEach { (parameter, type) -> bound[parameter] = checkedCast(bound.getValue(parameter), embedType(type)) }
    val result = withCallSite(call) { callee.insertCall(parameters.map { bound.getValue(it) }, this, returnType) }
    return if (declarations.isEmpty()) result else Block {
        addAll(declarations)
        add(result)
    }
}
