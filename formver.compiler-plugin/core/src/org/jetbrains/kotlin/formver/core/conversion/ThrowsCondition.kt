/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.unwrapUseSiteSubstitutionOverrides
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.embeddings.callables.CompleteFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.callables.NamedFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.ThrowsConditionCheck
import org.jetbrains.kotlin.formver.core.embeddings.expression.toConjunction
import org.jetbrains.kotlin.formver.core.purity.checkReadOnlyVarReads
import org.jetbrains.kotlin.formver.uniqueness.plugin.FunctionUniquenessAnalysis

/**
 * The `throwsOnlyIf` condition of [symbol] over the parameters of [signature], or `null` when it has none. Its reads
 * are checked when [reported] is set, which also reports an unsupported block.
 */
internal fun ProgramConverter.throwsConditionOf(
    symbol: FirFunctionSymbol<*>,
    signature: NamedFunctionSignature,
    returnTarget: ReturnTarget,
    reported: Boolean,
): ExpEmbedding? {
    val specification = symbol.checkedSpecification(session, checkerContext) ?: return null
    val analysis = uniquenessOutcomeOf(specification.holder).analysis
    val conditions = convertThrowsCondition(specification, signature, returnTarget, analysis, reported) ?: return null
    @OptIn(SymbolInternals::class) val declaration = symbol.unwrapUseSiteSubstitutionOverrides().fir
    checkVarReads(declaration, analysis) { source -> conditions.forEach { it.checkReadOnlyVarReads(source, this) } }
    return conditions.toConjunction()
}

/** The check that an exception may leave [symbol], whose signature this is; `null` when nothing is checked. */
internal fun CompleteFunctionSignature.exceptionEscape(symbol: FirFunctionSymbol<*>): ExpEmbedding? =
    (this as? NonInlineFunctionSignature)?.throwsCondition?.let { ThrowsConditionCheck(it, formalArgs, symbol) }

/**
 * The conditions of the `throwsOnlyIf` block of [specification] over the parameters of [signature], read in the entry
 * state, or `null` when it has none. The block is an unsupported construct on a pure function; it is reported at the
 * block when [reported] is set.
 */
private fun ProgramConversionContext.convertThrowsCondition(
    specification: UserSpecification,
    signature: NamedFunctionSignature,
    returnTarget: ReturnTarget,
    holderAnalysis: FunctionUniquenessAnalysis?,
    reported: Boolean,
): List<ExpEmbedding>? {
    val (holder, firSpec) = specification
    val block = firSpec.throwsOnlyIf ?: return null
    if (signature.isPure) {
        throw UnsupportedFeatureException(block.source.takeIf { reported }, "`throwsOnlyIf` on a `@Pure` function")
    }
    val (preconditionContext, _) = createContractConversionContext(holder.symbol, signature, firSpec, returnTarget, holderAnalysis)
    return preconditionContext.withEntryStateReads { collectInvariants(block) }
}
