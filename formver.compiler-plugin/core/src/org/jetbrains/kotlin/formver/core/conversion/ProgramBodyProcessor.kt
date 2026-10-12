/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.formver.core.embeddings.callables.CompleteFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.callables.SpecialFunctions
import org.jetbrains.kotlin.formver.core.embeddings.callables.toViperFunction
import org.jetbrains.kotlin.formver.core.embeddings.callables.toViperMethod
import org.jetbrains.kotlin.formver.core.linearization.FoldStateException
import org.jetbrains.kotlin.formver.core.linearization.nullBaseCase
import org.jetbrains.kotlin.formver.core.purity.checkValidity
import org.jetbrains.kotlin.formver.core.purity.isPure
import org.jetbrains.kotlin.formver.viper.SymbolicName

/** Validates converted bodies and produces callable definitions, including headers for absent bodies. */
internal class ProgramBodyProcessor(
    private val converter: ProgramConversionContext,
    private val uniqueness: UniquenessOutcomes,
    private val signatures: Map<SymbolicName, CompleteFunctionSignature>,
    private val refinements: List<Refinement>,
    private val onOwnershipError: (FoldStateException, CompleteFunctionSignature) -> Unit,
) {
    private val operatorFunctions: Set<SymbolicName> by lazy {
        (SpecialFunctions.all + SpecialFunctions.multiset).map { it.name }.toSet()
    }

    fun validate() {
        converter.convertedBodyResolver.forEachImpure { name, body ->
            body.bodyExp.checkValidity(signatures[name]?.declarationSource, converter)
        }
        converter.convertedBodyResolver.forEachPure { name, body ->
            if (!body.isPure()) {
                converter.reportPurityViolation(signatures[name]?.declarationSource, "Impure function body detected in pure function")
            }
        }
        for (signature in signatures.values) {
            val source = signature.declarationSource ?: continue
            for (condition in signature.preconditions + signature.postconditions) {
                condition.checkValidity(source, converter)
            }
        }
    }

    private fun linearizePure(name: SymbolicName, signature: CompleteFunctionSignature) {
        val converted = converter.convertedBodyResolver.lookupPure(name)
        val linearized = converted?.let { converter.linearizePureBody(signature.declarationSource, it) }
        val baseCases = linearized?.let { body ->
            signature.formalArgs.filter { it.isUnique && it.type.isNullable }.mapNotNull { body.nullBaseCase(it.name, operatorFunctions) }
        }.orEmpty()
        converter.linearizedBodyResolver.storeFunction(name, signature.toViperFunction(converter.typeResolver, linearized, baseCases))
    }

    private fun linearizeImpure(name: SymbolicName, signature: CompleteFunctionSignature) {
        val converted = converter.convertedBodyResolver.lookupImpure(name)
        val tracksOwnership = signature.symbol?.let { uniqueness.cached(it)?.hasErrors } != true
        val body = converted?.let {
            try {
                converter.linearizeImpureBody(signature.declarationSource, it, tracksOwnership)
            } catch (e: FoldStateException) {
                onOwnershipError(e, signature)
                null
            }
        }
        val method = body?.toViperMethod(signature, converter.typeResolver) ?: signature.toViperMethod(converter.typeResolver, null)
        converter.linearizedBodyResolver.storeMethod(name, method)
    }

    fun linearize() {
        val (pure, impure) = signatures.entries.partition { it.value.isPure }
        pure.forEach { linearizePure(it.key, it.value) }
        impure.forEach { linearizeImpure(it.key, it.value) }
        refinements.map { it.toMethod(converter.typeResolver) }.forEach { converter.linearizedBodyResolver.storeMethod(it.name, it) }
    }
}
