/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpWrapper
import org.jetbrains.kotlin.formver.core.embeddings.expression.RepresentationObserverCall
import org.jetbrains.kotlin.formver.core.linearization.SharedLinearizationState
import org.jetbrains.kotlin.formver.core.linearization.PureFunBodyLinearizer
import org.jetbrains.kotlin.formver.core.linearization.toLinearizable
import org.jetbrains.kotlin.formver.viper.ast.Exp

/** Expands receiver observations without requiring the predicate being defined. */
class ClassLawExpansion(
    private val program: ProgramConversionContext,
    private val owner: FirRegularClassSymbol,
    private val initializer: (org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol) -> ExpEmbedding? = { null },
) {
    fun initializedField(symbol: org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol): ExpEmbedding? =
        initializer(symbol.unwrapFakeOverrides())
    private val active = mutableSetOf<FirFunctionSymbol<*>>()

    fun isOwnAccess(access: FirQualifiedAccessExpression): Boolean =
        access.extensionReceiver == null &&
            (access.dispatchReceiver as? FirThisReceiverExpression)?.calleeReference?.symbol?.let { receiver ->
                receiver == owner || active.any { it.getContainingClassSymbol() == receiver }
            } == true

    @OptIn(SymbolInternals::class)
    fun call(symbol: FirCallableSymbol<*>, args: List<ExpEmbedding>): ExpEmbedding {
        check(classLawObserverFailure(symbol) == null) { "Invalid class-law observer reached conversion" }
        val function = classLawObserverBody(symbol)!!
        val signature = with(program) { function.toFunctionSignature() }.signature
        val keys = listOf(SubstitutedArgument.DispatchThis) + function.valueParameterSymbols.map {
            SubstitutedArgument.ValueParameter(it)
        }
        if (args.size != signature.formalArgs.size || keys.size != signature.formalArgs.size) {
            throw UnsupportedFeatureException(function.source, "Mismatched observer argument arity in class law")
        }
        return RepresentationObserverCall(args, signature.callableType.returnType) { actuals ->
            if (actuals.size != signature.formalArgs.size) {
                throw UnsupportedFeatureException(function.source, "Mismatched observer argument arity in class law")
            }
            check(active.add(function)) { "Recursive class-law observer reached conversion" }
            try {
                val substitutions = keys.zip(actuals.zip(signature.formalArgs).map { (arg, formal) ->
                    ExpWrapper(arg, formal.type)
                }).toMap()
                body(function, substitutions)
            } finally {
                active.remove(function)
            }
        }
    }

    @OptIn(SymbolInternals::class)
    fun body(function: FirFunctionSymbol<*>, substitutions: Map<SubstitutedArgument, ExpEmbedding>): Exp {
        val signature = with(program) { function.toFunctionSignature() }
        val resolver = InlineParameterResolver(substitutions, function.name.asString(), signature.returnTarget)
        val context = MethodConverter(
            program, signature.signature, resolver, program.scopeIndexProducer.getFresh(), OwnershipFrame.root(null, emptyList()),
        ).statementCtxt()
        val saved = program.typeResolver.classLawExpansion
        program.typeResolver.classLawExpansion = this
        try {
            val body = context.convert(function.fir.body!!)
            val linearizer = PureFunBodyLinearizer(
                function.source, SharedLinearizationState(program.anonVarProducer),
                typeResolver = program.typeResolver, representationLaw = true,
            )
            body.toLinearizable(function.source).toViperUnusedResult(linearizer)
            return linearizer.constructExpression()
        } finally {
            program.typeResolver.classLawExpansion = saved
        }
    }
}
