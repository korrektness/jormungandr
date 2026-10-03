/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.formver.core.embeddings.expression.BindingMode
import org.jetbrains.kotlin.formver.core.embeddings.expression.FirVariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.RootBinding
import org.jetbrains.kotlin.formver.core.embeddings.expression.VariableEmbedding
import org.jetbrains.kotlin.formver.uniqueness.plugin.FunctionUniquenessAnalysis
import org.jetbrains.kotlin.formver.uniqueness.plugin.Path
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessState

/**
 * The uniqueness facts for one body being converted: a function's own body, or a body inlined at a call, which is an
 * inline function's body, a lambda invoked in place, or a default argument.
 *
 * Paths in the body are rooted at the symbols of the function the body belongs to, and [analysis] answers for them.
 * An inlined body's parameters are bound to the variables that stand for them at the call by [bindings]. [parent] is
 * the frame of the body containing the call, which is the body that runs the inlined one: for a lambda, the body that
 * invokes it, not the one it is written in. [analysis] is `null` when there are no facts, and then nothing is owned.
 *
 * [receiver] is the extension receiver of a function's own body, with the symbol [analysis] roots its paths at.
 */
class OwnershipFrame private constructor(
    val analysis: FunctionUniquenessAnalysis?,
    private val parent: OwnershipFrame?,
    private val stateInParent: (FunctionUniquenessAnalysis) -> UniquenessState,
    val bindings: List<RootBinding>,
    private val outerScope: List<VariableEmbedding>,
    private val receiver: Pair<VariableEmbedding, FirBasedSymbol<*>>?,
) {
    /**
     * A frame for a body inlined at [callSite], which runs while the call holds its arguments. [outerScope] are the
     * variables in scope at the call.
     */
    fun inlined(
        analysis: FunctionUniquenessAnalysis?,
        callSite: FirElement,
        bindings: List<RootBinding>,
        outerScope: List<VariableEmbedding>,
    ) = OwnershipFrame(analysis, this, { it.stateInsideCall(callSite) }, bindings, outerScope, null)

    /** A frame for a default argument of the function called at [callSite], evaluated before the call takes anything. */
    fun forDefault(analysis: FunctionUniquenessAnalysis?, callSite: FirElement, outerScope: List<VariableEmbedding>) =
        OwnershipFrame(analysis, this, { it.stateBefore(callSite) }, emptyList(), outerScope, null)

    /**
     * The variables in scope at the calls that run this body, besides [ownScope], the variables of the body itself,
     * and the function's own extension receiver.
     */
    fun scopeWith(ownScope: List<VariableEmbedding>): List<VariableEmbedding> =
        (ownScope + outerScope + listOfNotNull(receiver?.first)).distinctBy { it.name }

    fun pathOf(expression: FirExpression): Path? = analysis?.pathOf(expression)

    /** Whether [path] is `Unique` on entry to [element]. */
    fun ownsBefore(element: FirElement, path: Path): Boolean =
        analysis?.let { it.owns(it.stateBefore(element), path, ownedView(path)) } ?: false

    /** Whether [path] is `Unique` on exit from [element]. */
    fun ownsAfter(element: FirElement, path: Path): Boolean =
        analysis?.let { it.owns(it.stateAfter(element), path, ownedView(path)) } ?: false

    /** Whether some root is `Unique` somewhere in the body's function. */
    val ownsAnyPath: Boolean
        get() = analysis?.ownsAnyPath == true

    /** Whether [path] is rooted at a borrowed parameter whose argument the caller owns, which the body owns too. */
    private fun ownedView(path: Path): Boolean =
        bindings.any { it.root == path.first() && it.mode == BindingMode.Borrowed && !it.formal.isUnique }

    /**
     * The paths below [variable] that hold nothing at the point [point] gives in this frame's body, when [variable]
     * is owned there, and `null` when it is not.
     *
     * The frame that answers for [variable] is the innermost one that binds it, and otherwise the innermost one whose
     * state at the point has it as a root. In an ancestor frame the point is inside the call that runs the next frame.
     */
    fun movedBelowOwned(variable: VariableEmbedding, point: (FunctionUniquenessAnalysis) -> UniquenessState): List<Path>? {
        val chain = generateSequence(this to analysis?.let(point)) { (frame, _) ->
            frame.parent?.let { parent -> parent to parent.analysis?.let(frame.stateInParent) }
        }.toList()
        for ((frame, state) in chain) {
            val binding = frame.bindings.firstOrNull { it.variable.name == variable.name } ?: continue
            val root = binding.root ?: return null
            val analysis = frame.analysis ?: return null
            if (state == null || binding.mode == BindingMode.Released) return null
            val ownedView = binding.mode == BindingMode.Borrowed && !binding.formal.isUnique
            return if (analysis.owns(state, listOf(root), ownedView)) analysis.movedBelow(state, root) else null
        }
        val symbol = (variable as? FirVariableEmbedding)?.symbol
            ?: chain.last().first.receiver?.takeIf { it.first.name == variable.name }?.second
            ?: return null
        for ((frame, state) in chain) {
            val analysis = frame.analysis ?: continue
            if (state == null || !analysis.hasRoot(state, symbol)) continue
            return if (analysis.owns(state, listOf(symbol))) analysis.movedBelow(state, symbol) else null
        }
        return null
    }

    companion object {
        /** The frame of a function's own body, whose extension receiver is [receiver], rooted at [receiverRoot]. */
        fun root(analysis: FunctionUniquenessAnalysis?, receiver: VariableEmbedding?, receiverRoot: FirBasedSymbol<*>?) =
            OwnershipFrame(
                analysis, null, { error("A function's own body has no caller.") }, emptyList(), emptyList(),
                receiver?.let { variable -> receiverRoot?.let { variable to it } },
            )
    }
}
