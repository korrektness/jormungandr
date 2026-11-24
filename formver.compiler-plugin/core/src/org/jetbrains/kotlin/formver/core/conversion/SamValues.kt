/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirSuperReceiverExpression
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.resolve.dfa.controlFlowGraph
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.scopes.getFunctions
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.common.externalSpecificationSymbol
import org.jetbrains.kotlin.formver.core.effects
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.blockOf
import org.jetbrains.kotlin.formver.core.isBorrowed
import org.jetbrains.kotlin.formver.core.isGhostObserver
import org.jetbrains.kotlin.formver.core.isLaw
import org.jetbrains.kotlin.formver.core.isPure
import org.jetbrains.kotlin.formver.core.isUnique
import org.jetbrains.kotlin.formver.core.returnsUnique
import org.jetbrains.kotlin.formver.locality.plugin.borrowsDispatchReceiver
import org.jetbrains.kotlin.formver.locality.plugin.ownsDispatchReceiver
import org.jetbrains.kotlin.formver.uniqueness.plugin.extendsLocalFlow

/** Verify the callback independently, and give the caller a shared object of the interface type. */
internal fun StmtConversionContext.convertSamValue(conversion: FirExpression, argument: FirExpression?): ExpEmbedding {
    val destination = conversion.resolvedType
    fun unsupported(reason: String): Nothing = throw UnsupportedFeatureException(conversion.source, "SAM conversion to $destination $reason")
    val lambda = (argument?.unwrapArgument() as? FirAnonymousFunctionExpression)?.anonymousFunction
        ?: unsupported("of a non-literal callback")
    if (!schedulesLambdaValue(lambda)) unsupported("in a context that does not verify callback bodies")
    if (lambda.controlFlowGraphReference?.controlFlowGraph?.extendsLocalFlow == true) {
        unsupported("of a callback analysed as called in place")
    }
    val cls = destination.toRegularClassSymbol(session) ?: unsupported("has no resolved interface class")
    val scope = cls.unsubstitutedScope(session, checkerContext.scopeSession, false, FirResolvePhase.BODY_RESOLVE)
    val members = scope.getCallableNames().flatMap { scope.getFunctions(it) }
    val families = context(checkerContext) { members.associateWith { it.overrideFamily() } }
    if (families.values.any { family -> family.any { it.isGhostObserver(session) || it.isLaw(session) } } || cls.hasSuperclassLaws(session)) {
        unsupported("requiring interface laws or observer bindings")
    }
    val obligations = families.filterKeys { it.resolvedStatus.modality == Modality.ABSTRACT }
        .values.flatten().map { it.unwrapFakeOverrides() }.distinct()
    for (original in obligations) {
        samRefinementRequirement(original)?.let { unsupported("cannot refine $it on ${original.callableId}") }
    }
    if (lambda.symbol.hasSamOwnership() || lambda.symbol.isPure(session)) {
        unsupported("of a callback with its own ownership or purity annotations")
    }
    if (lambda.capturesSuper()) unsupported("captures an enclosing `super` receiver")
    return blockOf(convertLambda(lambda, samType = embedType(conversion)))
}

private fun FirAnonymousFunction.capturesSuper(): Boolean {
    var capturesSuper = false
    val localClasses = mutableSetOf<FirClassSymbol<*>>()
    accept(object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            if (capturesSuper) return
            if (element is FirClass) localClasses.add(element.symbol)
            if (element is FirSuperReceiverExpression) {
                val receiver = element.dispatchReceiver as? FirThisReceiverExpression
                val symbol = receiver?.calleeReference?.boundSymbol
                capturesSuper = symbol !is FirClassSymbol<*> || symbol !in localClasses
            }
            element.acceptChildren(this)
            if (element is FirClass) localClasses.remove(element.symbol)
        }
    })
    return capturesSuper
}

private fun StmtConversionContext.samRefinementRequirement(method: FirFunctionSymbol<*>): String? = when {
    method.effects.isNotEmpty() -> "FIR contract effects"
    method.externalSpecificationSymbol(session) != method -> "a bound external specification"
    method.checkedSpecification(session, checkerContext) != null -> "a checked specification"
    method.isPure(session) -> "the purity guarantee"
    exceptionFreeByDefault(method, pure = false) -> "the exception-free guarantee"
    method.hasSamOwnership() -> "the ownership contract"
    else -> null
}

context(converter: ProgramConversionContext)
private fun FirFunctionSymbol<*>.hasSamOwnership(): Boolean =
    ownsDispatchReceiver(converter.session) || borrowsDispatchReceiver(converter.session) || context(converter.checkerContext) {
        returnsUnique() || (valueParameterSymbols + contextParameterSymbols + listOfNotNull(receiverParameterSymbol))
            .any { it.isUnique() || it.isBorrowed() }
    }
