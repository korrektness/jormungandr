/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.formver.core.embeddings.callables.NamedFunctionSignature
import org.jetbrains.kotlin.formver.uniqueness.plugin.FunctionUniquenessAnalysis

/** Resolves a specification holder's parameters into the checked signature, including its postcondition result. */
internal fun ProgramConversionContext.createContractConversionContext(
    symbol: FirFunctionSymbol<*>,
    signature: NamedFunctionSignature,
    firSpec: FirSpecification,
    returnTarget: ReturnTarget,
    uniquenessAnalysis: FunctionUniquenessAnalysis?,
): Pair<StmtConversionContext, StmtConversionContext> {
    val rootResolver = RootParameterResolver(
        this,
        signature,
        symbol.valueParameterSymbols,
        signature.labelName,
        returnTarget,
    )
    val wrappedResolver = firSpec.returnVar?.let { ReturnVarSubstitutor(it, signature.returns) }
        ?.let { ctx -> SubstitutedReturnParameterResolver(rootResolver, ctx) } ?: rootResolver
    val preconditionContext = MethodConverter(
        this,
        signature,
        rootResolver,
        ScopeIndex.NoScope,
        OwnershipFrame.root(uniquenessAnalysis, symbol.receiverRoots(signature)),
    ).statementCtxt()
    val postconditionContext = MethodConverter(
        this,
        signature,
        wrappedResolver,
        ScopeIndex.NoScope,
        OwnershipFrame.root(uniquenessAnalysis, symbol.receiverRoots(signature)),
    ).statementCtxt()
    return Pair(preconditionContext, postconditionContext)
}
