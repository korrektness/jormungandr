/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.formver.readonly.plugin.isInSpecification
import org.jetbrains.kotlin.formver.readonly.plugin.isUniquePredConstruction
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors.INVALID_UNIQUE_PRED_PLACEMENT

/**
 * Checks that `UniquePred` is constructed only inside the arguments of a specification builtin, where its borrowed
 * argument cannot escape through the object.
 */
object UniquePredPlacementChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        if (!expression.isUniquePredConstruction() || context.isInSpecification()) return

        reporter.reportOn(expression.source, INVALID_UNIQUE_PRED_PLACEMENT)
    }
}
