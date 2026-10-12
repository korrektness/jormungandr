/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.formver.core.embeddings.callables.CompleteFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineFunctionSignature
import org.jetbrains.kotlin.formver.viper.SymbolicName

internal class LambdaRegistry(
    private val converter: ProgramConversionContext,
    private val signatures: MutableMap<SymbolicName, CompleteFunctionSignature>,
) {
    private val lifted = mutableMapOf<FirExpression, LiftedLambda>()

    val sources: List<SpecifiedLambda>
        get() = lifted.values.map { it.source }

    operator fun get(expression: FirExpression): LiftedLambda? = lifted[expression]

    fun prepare(lambdas: List<SpecifiedLambda>) {
        for (lambda in lambdas) {
            if (lambda.expression in lifted) continue
            val implementation = with(converter) { lambda.lift() }
            lifted[lambda.expression] = implementation
            signatures[implementation.constructor.name] = implementation.constructor
            for (observer in with(converter) { implementation.bindObservers() }) {
                signatures[observer.signature.name] = observer.signature
                converter.convertedBodyResolver.storePure(observer.signature.name, observer.body)
            }
        }
    }

    fun finalizeCreationFacts() {
        for (implementation in lifted.values.sortedBy { lambda ->
            generateSequence(lambda.source.parent) { it.parent }.count()
        }) with(converter) { implementation.finalizeCreationFacts() }
    }

    fun register(
        lambda: SpecifiedLambda,
        lambdas: List<SpecifiedLambda>,
        inheritedSignature: () -> CompleteFunctionSignature,
    ): LambdaRegistration {
        prepare(lambdas)
        val implementation = lifted.getValue(lambda.expression)
        val inherited = inheritedSignature()
        val (signature, target) = with(converter) { implementation.invokeSignature(inherited) }
        signatures[signature.name] = signature
        val analysis = converter.uniquenessAnalysisOf(lambda.function.symbol)
        converter.convertedBodyResolver.storeImpure(signature.name, with(converter) {
            implementation.invokeBody(signature, target, analysis)
        })
        val refinement = if (context(converter.checkerContext) { lambda.invoke.specificationOwner() } != null) {
            Refinement(signature, inherited, lambda.invoke.refinementSpelling, lambda.function.source)
        } else null
        return LambdaRegistration(signature, target, refinement)
    }
}

internal data class LambdaRegistration(
    val signature: NonInlineFunctionSignature,
    val target: ReturnTarget,
    val refinement: Refinement?,
)
