/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.type.plugin

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirExpression

/**
 * Reports a type-fact mismatch with a diagnostic that names its cause, in place of a checker's own diagnostic.
 *
 * @param TypeFact the type-fact class of the mismatch.
 */
interface TypeFactMismatchExplainer<TypeFact> {
    /**
     * Reports that [expression], at the [position] named as in the checker's own diagnostic, has [actualTypeFact]
     * where [requiredTypeFact] is required. Returns false, reporting nothing, when it knows no cause.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    fun reportExplained(
        source: KtSourceElement?,
        position: String,
        expression: FirExpression,
        requiredTypeFact: TypeFact,
        actualTypeFact: TypeFact,
    ): Boolean
}
