/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.directOverriddenSymbolsSafe
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.formver.core.asPosition
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.core.embeddings.asInfo
import org.jetbrains.kotlin.formver.core.embeddings.callables.CompleteFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.callables.toMethodCall
import org.jetbrains.kotlin.formver.core.linearization.pureToViper
import org.jetbrains.kotlin.formver.core.names.RefinementName
import org.jetbrains.kotlin.formver.viper.ast.Stmt
import org.jetbrains.kotlin.formver.viper.ast.UserMethod

/**
 * A check that [override] refines the contract of [overridden], a declaration it directly overrides. [spelling]
 * names [overridden] as the Kotlin source does, and [source] is the declaration of [override].
 */
internal data class Refinement(
    val override: NonInlineFunctionSignature,
    val overridden: CompleteFunctionSignature,
    val spelling: String,
    val source: KtSourceElement?,
) {
    /**
     * The refinement method: it assumes the overridden precondition and that the receiver has the override's class,
     * calls the override, and asserts the overridden postcondition. A failure of the call's precondition or of an
     * assertion is reported on the override.
     */
    fun toMethod(typeResolver: TypeResolver): UserMethod {
        val position = source.asPosition
        fun role(clause: SourceRole.OverrideRefinement.Clause) = SourceRole.OverrideRefinement(spelling, clause).asInfo
        val receiverType = override.dispatchReceiver?.provenInvariants().orEmpty()
        val call = override.toMethodCall(
            overridden.formalArgs.map { it.toLocalVarUse() },
            overridden.returns.toLocalVarUse(),
            position,
            role(SourceRole.OverrideRefinement.Clause.PRECONDITION),
        )
        val assertions = overridden.postconditions.pureToViper(toBuiltin = true, typeResolver).map {
            Stmt.Assert(it, position, role(SourceRole.OverrideRefinement.Clause.POSTCONDITION))
        }
        return UserMethod(
            RefinementName(overridden.name),
            overridden.formalArgs.map { it.toLocalVarDecl() },
            overridden.returns.toLocalVarDecl(),
            (overridden.preconditions + receiverType).pureToViper(toBuiltin = true, typeResolver),
            emptyList(),
            Stmt.Seqn(listOf(call) + assertions),
            position,
        )
    }
}

/** The name of this function as the Kotlin source spells it in a diagnostic. */
internal val FirNamedFunctionSymbol.refinementSpelling: String
    get() = callableId.className?.let { "$it.$name" } ?: name.asString()

/** Whether this function can be overridden or overrides another. */
internal val FirFunctionSymbol<*>.isOpenOrOverride: Boolean
    get() = this is FirNamedFunctionSymbol && (isOverride || !isFinal)

/**
 * The function whose specification this function has: itself when it has one, and otherwise the declaration
 * it inherits one from, when exactly one declaration it overrides has a specification.
 */
context(checkerContext: CheckerContext)
@OptIn(SymbolInternals::class)
internal fun FirNamedFunctionSymbol.specificationOwner(): FirNamedFunctionSymbol? {
    if (fir.userSpecification(checkerContext.session) != null) return this
    return directOverriddenSymbolsSafe()
        .filterIsInstance<FirNamedFunctionSymbol>()
        .mapNotNull { it.specificationOwner() }
        .distinct()
        .singleOrNull()
}
