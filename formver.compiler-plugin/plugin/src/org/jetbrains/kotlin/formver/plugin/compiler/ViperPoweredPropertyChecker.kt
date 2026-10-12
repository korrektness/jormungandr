/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.formver.common.PluginConfiguration
import org.jetbrains.kotlin.formver.core.names.buildName
import org.jetbrains.kotlin.formver.core.names.embedScope
import org.jetbrains.kotlin.formver.core.names.GetterKotlinName
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.ReportedVerifierErrors

/** Verifies specified lambdas in top-level property initializers and accessors. */
class ViperPoweredPropertyChecker(
    private val session: FirSession,
    private val config: PluginConfiguration,
    verifierLocator: VerifierLocator,
    reportedErrors: ReportedVerifierErrors,
) : FirPropertyChecker(MppCheckerKind.Common) {
    private val lambdaChecker = ViperPoweredDeclarationChecker(session, config, verifierLocator, reportedErrors)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirProperty) {
        if (declaration.origin != FirDeclarationOrigin.Source || declaration.symbol.isLocal || declaration.dispatchReceiverType != null) return
        val scopeName = buildName {
            embedScope(declaration.symbol.callableId!!)
            GetterKotlinName(declaration.name)
        }
        lambdaChecker.checkStandaloneLambdas(declaration, scopeName)
    }
}
