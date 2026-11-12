/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.utils.isStatic
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFieldSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.isNothing
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.formver.common.Failure
import org.jetbrains.kotlin.formver.common.FailureKind
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.common.externalSpecificationSymbol
import org.jetbrains.kotlin.formver.core.boundDispatchReceiver
import org.jetbrains.kotlin.formver.core.embeddings.callables.CallableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.callables.insertCall
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.CustomGetter
import org.jetbrains.kotlin.formver.core.isPure
import org.jetbrains.kotlin.formver.core.names.SpecialPackages

/**
 * Whether [callee], from [ProgramConversionContext.embedAnyFunction] or a property's getter, is an opaque library
 * callable (SPECIFICATIONS.md, "Verification Control").
 */
internal fun ProgramConversionContext.isOpaqueLibraryCallable(callee: CallableEmbedding): Boolean {
    val signature = callee as? NonInlineFunctionSignature ?: return false
    val symbol = signature.symbol
    if (symbol != null && (!symbol.isOpaqueCandidate() || ContractDescriptionConversionVisitor(this, signature, symbol)
            .getPostconditions().isNotEmpty())) return false
    return signature.stdLibPreconditions(typeResolver).isEmpty() &&
        signature.stdLibPostconditions(signature.returns, typeResolver).isEmpty()
}

/** Origins of declarations read from compiled dependencies, as opposed to sources and members generated for them. */
private val libraryOrigins = setOf(
    FirDeclarationOrigin.Library, FirDeclarationOrigin.Java.Library, FirDeclarationOrigin.BuiltIns,
    FirDeclarationOrigin.BuiltInsFallback, FirDeclarationOrigin.Precompiled, FirDeclarationOrigin.Enhancement,
)

/** Whether this library callable may be opaque (SPECIFICATIONS.md, "Verification Control"); SnaKt's own are not. */
internal fun FirCallableSymbol<*>.isOpaqueCandidate(): Boolean {
    val declaration = unwrapFakeOverrides()
    return declaration.origin in libraryOrigins && declaration !is FirConstructorSymbol &&
        !resolvedReturnType.isNothing &&
        declaration.callableId?.packageName?.pathSegments()?.map { it.asString() } != SpecialPackages.formver
}

/** Reports [call] of [symbol] as `OPAQUE_CALL` when [callee] is a library function without a model. */
internal fun StmtConversionContext.reportOpaqueLibraryCall(
    call: FirFunctionCall, symbol: FirFunctionSymbol<*>, callee: CallableEmbedding,
) {
    if (isOpaqueLibraryCallable(callee)) reportOpaqueCall(call.source, symbol, KotlinCondition.OpaqueReason.NO_MODEL)
}

/** Reports [access] as `OPAQUE_CALL` when it reads a library property without a model. */
internal fun StmtConversionContext.reportOpaqueLibraryRead(access: FirPropertyAccessExpression) {
    val symbol = access.toResolvedCallableSymbol() as? FirPropertySymbol ?: return
    if (symbol.isLocal || !symbol.isOpaqueCandidate() || symbol.externalSpecificationSymbol(session) != symbol) return
    val getter = (embedProperty(symbol).getter as? CustomGetter)?.getterMethod ?: return
    if (isOpaqueLibraryCallable(getter)) reportOpaqueCall(access.source, symbol, KotlinCondition.OpaqueReason.READ_NO_MODEL)
}

/** Reads a property whose value does not depend on a receiver, or returns null for any other access. */
internal fun StmtConversionContext.convertReceiverlessRead(access: FirPropertyAccessExpression): ExpEmbedding? {
    val symbol = access.calleeReference.symbol
    // A `const val`, top-level or in an object, reads as the literal of its initializer.
    if (symbol is FirPropertySymbol && symbol.resolvedStatus.isConst) return convert(symbol.resolvedInitializer!!)
    access.topLevelPropertySymbol()?.let { return convertTopLevelPropertyRead(it, access.source) }
    if (symbol is FirFieldSymbol && symbol.isStatic) {
        reportOpaqueCall(access.source, symbol, KotlinCondition.OpaqueReason.READ_NO_MODEL)
        return unconstrainedValue(embedType(symbol.resolvedReturnType))
    }
    return null
}

private fun StmtConversionContext.reportOpaqueCall(
    source: KtSourceElement?, symbol: FirCallableSymbol<*>, reason: KotlinCondition.OpaqueReason,
) {
    val callee = symbol.callableId?.asSingleFqName()?.asString() ?: symbol.name.asString()
    reportWarning(Failure(FailureKind.OPAQUE_CALL, source, KotlinCondition.OpaqueCall(callee, reason)))
}

private fun FirPropertyAccessExpression.topLevelPropertySymbol(): FirPropertySymbol? =
    (calleeReference.symbol as? FirPropertySymbol)?.takeIf {
        !it.isLocal && boundDispatchReceiver == null && extensionReceiver == null
    }

/** A property without a receiver is read as an unconstrained value of its type. */
@OptIn(org.jetbrains.kotlin.fir.symbols.SymbolInternals::class)
private fun StmtConversionContext.convertTopLevelPropertyRead(symbol: FirPropertySymbol, source: KtSourceElement?): ExpEmbedding {
    if (symbol.isPure(session) && symbol.getterSymbol?.fir?.body != null) {
        return embedAnyFunction(symbol.getterSymbol!!).insertCall(emptyList(), this)
    }
    val reason = if (symbol.origin == FirDeclarationOrigin.Source) KotlinCondition.OpaqueReason.TOP_LEVEL_PROPERTY
        else KotlinCondition.OpaqueReason.READ_NO_MODEL
    reportOpaqueCall(source, symbol, reason)
    return unconstrainedValue(embedType(symbol.resolvedReturnType))
}
