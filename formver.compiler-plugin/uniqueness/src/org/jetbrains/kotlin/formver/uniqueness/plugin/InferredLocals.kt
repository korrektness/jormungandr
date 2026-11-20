/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import kotlinx.collections.immutable.mutate
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.formver.locality.plugin.Locality
import org.jetbrains.kotlin.formver.locality.plugin.QualifiedAccessArgumentLocalitiesMapper
import org.jetbrains.kotlin.formver.locality.plugin.receiverTemporaryInitializer
import org.jetbrains.kotlin.formver.type.plugin.CallArgumentTypeFactsMapper

/**
 * Whether [this] is a local declared without a type, whose uniqueness SnaKt infers from its initializer. Such a local
 * becomes shared where it is passed to a parameter that neither owns nor borrows it, stored in a shared property or
 * local, or assigned a value that is not unique; see SPECIFICATIONS.md, "Inferred locals".
 */
val FirBasedSymbol<*>.hasInferredUniqueness: Boolean
    get() = this is FirPropertySymbol && isLocal && receiverTemporaryInitializer == null &&
            resolvedReturnTypeRef.source?.kind !is KtRealSourceElementKind

/**
 * The inferred local [this] expression denotes as a whole, such as `c` but not `c.next`, or null when it denotes
 * something else.
 */
context(context: CheckerContext)
fun FirExpression.wholeInferredLocal(): FirBasedSymbol<*>? =
    resolveAccessState().enumeratePaths().singleOrNull()?.singleOrNull()?.takeIf { it.hasInferredUniqueness }

/**
 * The inferred locals that [this] call passes, as a whole, to a receiver or parameter that neither owns nor borrows
 * it, and to no parameter that owns it.
 */
context(context: CheckerContext)
fun FirFunctionCall.sharedInferredLocals(localities: CallArgumentTypeFactsMapper<Locality>): Set<FirBasedSymbol<*>> {
    val required = CallArgumentUniquenessesMapper.mapArgumentTypeFactsOf(this) +
            QualifiedAccessArgumentUniquenessMapper.mapArgumentTypeFactsOf(this)
    val globals = (localities.mapArgumentTypeFactsOf(this) + QualifiedAccessArgumentLocalitiesMapper.mapArgumentTypeFactsOf(this))
        .filter { (_, locality) -> locality == Locality.Global }
        .map { (argument, _) -> argument }
    val shared = mutableSetOf<FirBasedSymbol<*>>()
    val owned = mutableSetOf<FirBasedSymbol<*>>()
    for ((argument, uniqueness) in required) {
        val paths = argument.resolveAccessState().enumeratePaths().toList()
        when {
            uniqueness == Uniqueness.Unique -> paths.mapTo(owned) { it.first() }
            uniqueness == Uniqueness.Shared && globals.any { it === argument } ->
                argument.wholeInferredLocal()?.let(shared::add)
        }
    }
    return shared - owned
}

/**
 * Makes the inferred local [symbol] shared in [this] state when it was unique or borrowed in [before]. Every path below
 * it becomes shared too, except one already moved out.
 */
context(context: CheckerContext)
fun UniquenessState.shareInferredLocal(symbol: FirBasedSymbol<*>, before: UniquenessState): UniquenessState {
    val prior = before.children[symbol]?.data ?: symbol.resolveDeclaredUniqueness()
    if (prior == Uniqueness.Shared || prior == Uniqueness.Moved) return this
    val root = (children[symbol] ?: UniquenessState(prior)).joinEverywhere(Uniqueness.Shared)
    return putChild(symbol, root.copy(data = Uniqueness.Shared))
}

private fun UniquenessState.joinEverywhere(uniqueness: Uniqueness): UniquenessState =
    UniquenessState(data.join(uniqueness), children.mutate { mutable ->
        for ((key, child) in children) mutable[key] = child.joinEverywhere(uniqueness)
    })

/**
 * The uniqueness of [path] in [this] state: the join along the path, where a component with no entry has its declared
 * uniqueness.
 */
context(context: CheckerContext)
fun UniquenessState.uniquenessAlong(path: Path): Uniqueness {
    var node: UniquenessState? = this
    var uniqueness = Uniqueness.Unique
    for (symbol in path) {
        node = node?.children[symbol]
        uniqueness = uniqueness.join(node?.data ?: symbol.resolveDeclaredUniqueness())
    }
    return uniqueness
}

/**
 * Weakens the inferred local [symbol], just written with [source], to what [source] holds: the uniqueness of the paths
 * it reads in [before], joined with its declared uniqueness. A source that is shared makes [symbol] shared, and one that
 * is borrowed makes it borrowed. A moved source leaves [this] unchanged, since reading it is reported. [this] must hold
 * an entry for [symbol], as the write leaves.
 */
context(context: CheckerContext)
fun UniquenessState.weakenInferredLocal(symbol: FirBasedSymbol<*>, source: FirExpression, before: UniquenessState): UniquenessState {
    val read = source.resolveAccessState().enumeratePaths()
        .fold(Uniqueness.Unique) { result, path -> result.join(before.uniquenessAlong(path)) }
    val written = read.join(source.resolveUniqueness())
    if (written == Uniqueness.Moved) return this
    val root = children[symbol] ?: error("No uniqueness state for inferred local ${symbol} after writing it: $this")
    return putChild(symbol, root.joinEverywhere(written))
}
