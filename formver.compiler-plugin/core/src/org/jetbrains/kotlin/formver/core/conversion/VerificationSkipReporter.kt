/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.formver.common.Failure
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.common.reportFailure
import org.jetbrains.kotlin.formver.core.diagnostics.ConversionErrors
import org.jetbrains.kotlin.formver.core.neverVerify

/** Reports [ConversionErrors.VERIFICATION_SKIPPED] at most once per declaration source. */
class VerificationSkipReporter(
    private val session: FirSession,
    private val checkerContext: CheckerContext,
    private val reporter: DiagnosticReporter,
) {
    private val skippedSources: MutableSet<KtSourceElement?> = mutableSetOf()

    fun report(source: KtSourceElement?, reason: KotlinCondition.SkipReason, symbol: FirBasedSymbol<*>?) {
        if (!skippedSources.add(source)) return
        context(checkerContext) {
            reporter.reportFailure(Failure(ConversionErrors.VERIFICATION_SKIPPED, source, KotlinCondition.Skipped(reason, symbol)))
        }
    }

    /** Reports that [declaration] is not verified because its conversion stopped with an exception. */
    fun reportConversionStopped(declaration: FirDeclaration, reason: KotlinCondition.SkipReason) {
        if (declaration.symbol.neverVerify(session)) return
        report(declaration.source, reason, declaration.symbol)
    }
}
