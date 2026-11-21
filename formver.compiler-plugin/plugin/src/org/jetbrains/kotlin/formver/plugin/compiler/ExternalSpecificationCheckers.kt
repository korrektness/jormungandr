/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.formver.common.Failure
import org.jetbrains.kotlin.formver.common.FailureKind
import org.jetbrains.kotlin.formver.common.externalSpecifications
import org.jetbrains.kotlin.formver.common.reportFailure
import org.jetbrains.kotlin.formver.common.PluginConfiguration

/**
 * Reports binding errors, and bindings to declarations that SnaKt verified, on the model declarations they concern; the
 * registry attaches each of them to a source declaration of the session, so each one is reported exactly once.
 */
object ExternalSpecificationBindingChecker : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirDeclaration) {
        val registry = context.session.externalSpecifications ?: return
        for (error in registry.errorsOf(declaration.symbol)) {
            reporter.reportFailure(Failure(FailureKind.INVALID_EXTERNAL_SPEC, declaration.source, error.condition))
        }
        for (rebinding in registry.rebindingsOf(declaration.symbol)) {
            reporter.reportFailure(Failure(FailureKind.EXTERNAL_SPEC_OF_VERIFIED_DECLARATION, declaration.source, rebinding.condition))
        }
    }
}
