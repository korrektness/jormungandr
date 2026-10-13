/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.formver.common.PurityCondition
import org.jetbrains.kotlin.formver.common.PurityRule
import org.jetbrains.kotlin.formver.core.embeddings.callables.CompleteFunctionSignature
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.names.InheritedMemberName
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.UserFunction
import org.jetbrains.kotlin.formver.viper.ast.UserMethod
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
    private val measures: Map<SymbolicName, TerminationMeasure>,
    private val onOwnershipError: (FoldStateException, CompleteFunctionSignature) -> Unit,
    /** Called with each recursive function that has no measure, and a call path from it back to itself. */
    private val onMissingMeasure: (CompleteFunctionSignature, List<CompleteFunctionSignature>) -> Unit,
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
                converter.reportPurityViolation(signatures[name]?.declarationSource,
                    PurityCondition(PurityRule.FUNCTION_BODY, signatures[name]?.symbol))
            }
        }
        for (signature in signatures.values) {
            val source = signature.declarationSource ?: continue
            for (condition in signature.preconditions + signature.postconditions) {
                condition.checkValidity(source, converter)
            }
        }
    }

    private fun linearizePure(name: SymbolicName, signature: CompleteFunctionSignature): UserFunction {
        val converted = converter.convertedBodyResolver.lookupPure(name)
        val linearized = converted?.let { converter.linearizePureBody(signature.declarationSource, it) }
        val baseCases = linearized?.let { body ->
            signature.formalArgs.filter { it.isUnique && it.type.isNullable }.mapNotNull { body.nullBaseCase(it.name, operatorFunctions) }
        }.orEmpty()
        val function = signature.toViperFunction(converter.typeResolver, linearized, baseCases)
        val measure = measures[name] ?: return function
        return function.copy(pres = function.pres + measure.toViper(converter.typeResolver))
    }

    /**
     * Stores [functions]. A recursive function without a measure keeps only its specification: its definition is not
     * known to have a solution.
     */
    private fun storePure(functions: Map<SymbolicName, UserFunction>) {
        val recursion = PureRecursion(functions.values.toList(), converter.typeResolver.predicates())
        for ((name, function) in functions) {
            val cycle = recursion.cycle(name)?.takeIf { name !in measures }
            if (cycle != null) onMissingMeasure(signatures.getValue(name), cycle.map(signatures::getValue))
            converter.linearizedBodyResolver.storeFunction(name, if (cycle == null) function else function.copy(body = null))
        }
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
        val receiver = signature.dispatchReceiver
        val view = receiver?.type?.pretype as? ClassTypeEmbedding
        if (body != null && view != null && converter.typeResolver.hasRuntimeObserverDispatch(view)) {
            // The declaration proves this runtime class; inherited-body obligations cover each concrete subclass.
            val proofName = InheritedMemberName(name, "runtime_body", name)
            val proof = UserMethod(proofName, method.formalArgs, method.formalReturns.single(),
                method.pres + Exp.EqCmp(RuntimeTypeDomain.typeOf(receiver.toLocalVarUse()), view.runtimeType),
                method.posts, method.body, method.pos, method.info)
            converter.linearizedBodyResolver.storeMethod(proofName, proof)
            converter.linearizedBodyResolver.storeMethod(name, signature.toViperMethod(converter.typeResolver, null))
        } else {
            converter.linearizedBodyResolver.storeMethod(name, method)
        }
    }

    fun linearize() {
        val (pure, impure) = signatures.entries.partition { it.value.isPure }
        storePure(pure.associate { it.key to linearizePure(it.key, it.value) })
        impure.forEach { linearizeImpure(it.key, it.value) }
        refinements.map { it.toMethod(converter.typeResolver) }.forEach { converter.linearizedBodyResolver.storeMethod(it.name, it) }
    }
}
