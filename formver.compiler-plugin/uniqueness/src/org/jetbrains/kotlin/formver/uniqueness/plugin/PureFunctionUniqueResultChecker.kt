/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.formver.readonly.plugin.isPure
import org.jetbrains.kotlin.formver.uniqueness.attribute.uniquenessAttribute
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors.INVALID_PURE_UNIQUE_RESULT

/**
 * Checks that a `@Pure` function does not declare a `@Unique` result.
 */
object PureFunctionUniqueResultChecker : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        if (!declaration.symbol.isPure(context.session)) return

        val returnTypeRef = declaration.returnTypeRef
        if (returnTypeRef.coneType.attributes.uniquenessAttribute == null) return

        reporter.reportOn(returnTypeRef.source ?: declaration.source, INVALID_PURE_UNIQUE_RESULT)
    }
}
