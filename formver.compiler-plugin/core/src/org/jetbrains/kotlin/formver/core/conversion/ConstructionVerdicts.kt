/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.formver.common.SnaktInternalException

/**
 * Whether the construction of each source class is verified: its class program converts and nothing skips its
 * verification. Constructor headers carry synthesized field facts only for a verified construction.
 *
 * The class checker decides a class when it converts it. A client may need the verdict first; [compute] then converts
 * the class the same way and discards the diagnostics, which the class checker reports later.
 */
class ConstructionVerdicts(
    session: FirSession,
    private val compute: (FirRegularClassSymbol, CheckerContext) -> Boolean,
) : FirExtensionSessionComponent(session) {
    private val verdicts = mutableMapOf<FirRegularClassSymbol, Boolean>()
    private val pending = mutableSetOf<FirRegularClassSymbol>()

    fun verifies(owner: FirRegularClassSymbol, context: CheckerContext): Boolean {
        verdicts[owner]?.let { return it }
        // Only a conversion of [owner]'s own class program reaches here; that program proves the facts it assumes.
        if (owner in pending) return true
        return decide(owner) { compute(owner, context) }
    }

    /** Runs [convert], a conversion of [owner]'s class program, and records whether it verifies the construction. */
    fun decide(owner: FirRegularClassSymbol, convert: () -> Boolean): Boolean {
        pending += owner
        val verdict = try {
            convert()
        } finally {
            pending -= owner
        }
        val previous = verdicts.put(owner, verdict)
        if (previous != null && previous != verdict) {
            throw SnaktInternalException(owner.source, "two conversions of ${owner.classId} disagree on whether its construction verifies")
        }
        return verdict
    }
}

private val constructionVerdictsAccessor = FirSession.nullableSessionComponentAccessor<ConstructionVerdicts>()

/** Sessions without this component verify no construction. */
val FirSession.constructionVerdicts: ConstructionVerdicts? by constructionVerdictsAccessor
