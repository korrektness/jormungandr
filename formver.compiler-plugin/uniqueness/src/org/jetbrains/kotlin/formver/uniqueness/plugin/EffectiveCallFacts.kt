/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirSafeCallExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.formver.intrinsics.plugin.aliasedArrayCopyDestination
import org.jetbrains.kotlin.formver.intrinsics.plugin.aliasedStringBuilderReceiver
import org.jetbrains.kotlin.formver.locality.plugin.receiverTemporaryInitializer
import org.jetbrains.kotlin.formver.type.plugin.collectTails
import org.jetbrains.kotlin.formver.type.plugin.removeCast
import org.jetbrains.kotlin.formver.type.plugin.unwrapResolvedExpression

/** The selection made before a receiver-gated access takes any operands. */
data class ModelAccessFact(val checked: Boolean, val result: Uniqueness) {
    fun join(other: ModelAccessFact): ModelAccessFact =
        ModelAccessFact(checked && other.checked, result.join(other.result))
}

/** The pre-move facts used to restore borrowed operands and publish the result on call completion. */
data class CallEntryFact(val paths: UniquenessState, val model: ModelAccessFact?) {
    fun join(other: CallEntryFact): CallEntryFact = CallEntryFact(
        paths.join(other.paths),
        if (model == null || other.model == null) null else model.join(other.model),
    )
}

/**
 * The paths, pending calls, and completed receiver-gated accesses on the routes to a CFG point. Where a producer
 * occurs on only one arm of a join, its fact describes the routes on which that producer supplies a value.
 */
data class UniquenessFlowFacts(
    val paths: UniquenessState,
    val accesses: PersistentMap<FirQualifiedAccessExpression, ModelAccessFact> = persistentMapOf(),
    val callEntries: PersistentMap<FirFunctionCall, CallEntryFact> = persistentMapOf(),
) {
    fun join(other: UniquenessFlowFacts): UniquenessFlowFacts = UniquenessFlowFacts(
        paths.join(other.paths),
        if (accesses == other.accesses) accesses else accesses.builder().apply {
            for ((site, fact) in other.accesses) put(site, get(site)?.join(fact) ?: fact)
        }.build(),
        if (callEntries == other.callEntries) callEntries else callEntries.builder().apply {
            for ((site, fact) in other.callEntries) put(site, get(site)?.join(fact) ?: fact)
        }.build(),
    )

    context(context: CheckerContext)
    fun classify(access: FirQualifiedAccessExpression): ModelAccessFact? {
        val callee = access.toResolvedCallableSymbol() ?: return null
        val receiver = access.receiverRequiringModelOwnership(callee) ?: return null
        val checked = ownsReceiver(receiver)
        val result = if (checked) access.resolveTerminalUniqueness() else access.uncheckedModelResultUniqueness(callee)
        return ModelAccessFact(checked, result)
    }

    context(context: CheckerContext)
    fun ownsReceiver(receiver: FirExpression): Boolean {
        val denoted = receiver.unwrapResolvedExpression().removeCast()
        val symbol = (denoted as? FirPropertyAccessExpression)?.calleeReference?.symbol
        if (symbol?.borrowedViewSource() != null) return false
        val paths = denoted.resolveAccessState().enumeratePaths().take(2).toList()
        val root = paths.singleOrNull()?.first()
        if (paths.size > 1) return false
        if (root is FirPropertySymbol && !root.isLocal && !denoted.isOwnedConstructionRootOnEveryPath(root)) return false
        return uniquenessOf(denoted) == Uniqueness.Unique
    }

    context(context: CheckerContext)
    fun uniquenessOf(expression: FirExpression): Uniqueness {
        return resultProducers(expression).fold(Uniqueness.Unique) { result, terminal ->
            result.join(terminalUniqueness(terminal))
        }
    }

    context(context: CheckerContext)
    fun hasSharedModelResult(expression: FirExpression): Boolean = resultProducers(expression).any { terminal ->
        terminal is FirQualifiedAccessExpression && terminal.hasReceiverGate() && accesses[terminal]?.result != Uniqueness.Unique
    }

    context(context: CheckerContext)
    private fun terminalUniqueness(terminal: FirExpression): Uniqueness {
        if (terminal is FirQualifiedAccessExpression) {
            accesses[terminal]?.let { return it.result }
            if (terminal.hasReceiverGate()) return Uniqueness.Shared
        }
        val declared = terminal.resolveTerminalUniqueness()
        return terminal.resolveAccessState().enumeratePaths().fold(declared) { result, path ->
            val root = path.first()
            val pathFact = if (root.hasInferredUniqueness && root !in paths.children) Uniqueness.Shared else paths.uniquenessAlong(path)
            result.join(pathFact)
        }
    }
}

context(context: CheckerContext)
private fun FirExpression.isOwnedConstructionRootOnEveryPath(root: FirPropertySymbol): Boolean {
    val origins = constructionRootOwnership(root).toList()
    return origins.isNotEmpty() && origins.all { it }
}

context(context: CheckerContext)
internal fun FirExpression.isOnlyUnownedPropertyRoot(root: FirBasedSymbol<*>): Boolean =
    root is FirPropertySymbol && !root.isLocal && constructionRootOwnership(root).none { it }

context(context: CheckerContext)
private fun FirExpression.constructionRootOwnership(root: FirPropertySymbol): Sequence<Boolean> =
    resultProducers(this).filter { root in it.resolveAccessState().children }.flatMap { terminal ->
        val access = terminal as? FirPropertyAccessExpression
        val receiver = access?.pathReceiver
        if (receiver != null && root in receiver.resolveAccessState().children) {
            receiver.constructionRootOwnership(root)
        } else {
            sequenceOf(access != null && access.calleeReference.symbol == root && access.isOwnedConstructionField())
        }
    }

context(context: CheckerContext)
private fun FirQualifiedAccessExpression.hasReceiverGate(): Boolean =
    toResolvedCallableSymbol()?.let { receiverRequiringModelOwnership(it) } != null

context(context: CheckerContext)
private fun resultProducers(expression: FirExpression): Sequence<FirExpression> {
    val terminal = expression.unwrapResolvedExpression().removeCast()
    val tails = terminal.collectTails().toList()
    if (tails.isNotEmpty()) return tails.asSequence().flatMap { resultProducers(it) }
    val source = when (terminal) {
        is FirPropertyAccessExpression -> terminal.calleeReference.symbol?.receiverTemporaryInitializer
        is FirSafeCallExpression -> terminal.selector as? FirExpression
        else -> terminal.aliasedArrayCopyDestination() ?: terminal.aliasedStringBuilderReceiver(context.session)
    }
    return if (source == null) sequenceOf(terminal) else resultProducers(source)
}
