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
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.formver.readonly.plugin.isPure
import org.jetbrains.kotlin.formver.uniqueness.attribute.uniquenessAttribute
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors.INVALID_PURE_REFERENCE_RESULT
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors.INVALID_PURE_UNIQUE_RESULT

private val FirTypeRef.isUnique: Boolean
    get() = coneType.attributes.uniquenessAttribute != null

/**
 * Checks the result of a `@Pure` function. It may not be `@Unique`, and when the function has a `@Unique` parameter,
 * receiver or context parameter it must be a value type: a Viper function cannot allocate, so any reference it
 * returns aliases its inputs.
 */
object PureFunctionResultChecker : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        if (!declaration.symbol.isPure(context.session)) return

        val returnTypeRef = declaration.returnTypeRef
        val source = returnTypeRef.source ?: declaration.source

        if (returnTypeRef.isUnique) {
            reporter.reportOn(source, INVALID_PURE_UNIQUE_RESULT)
            return
        }

        val parameterTypeRefs =
            listOfNotNull(declaration.receiverParameter?.typeRef) +
                    declaration.contextParameters.map { it.returnTypeRef } +
                    declaration.valueParameters.map { it.returnTypeRef }

        if (parameterTypeRefs.any { it.isUnique } && !returnTypeRef.coneType.isValueType()) {
            reporter.reportOn(source, INVALID_PURE_REFERENCE_RESULT)
        }
    }
}
