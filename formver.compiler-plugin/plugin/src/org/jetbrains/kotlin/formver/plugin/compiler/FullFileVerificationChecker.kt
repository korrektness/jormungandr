/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContextForProvider
import org.jetbrains.kotlin.fir.analysis.checkers.context.PersistentCheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.analysis.collectors.AbstractDiagnosticCollectorVisitor
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousFunctionSymbol
import org.jetbrains.kotlin.formver.common.PluginConfiguration
import org.jetbrains.kotlin.formver.plugin.compiler.reporting.ReportedVerifierErrors

/** Explicit full-file host capability: this callback runs before normal descendant checkers. */
internal class FullFileVerificationChecker(private val session: FirSession, private val config: PluginConfiguration) :
    FirFileChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFile) {
        val targets = mutableListOf<Pair<FirElement, CheckerContextForProvider>>()
        val visitor = object : AbstractDiagnosticCollectorVisitor(
            PersistentCheckerContext(context.sessionHolder, context.returnTypeCalculator),
        ) {
            override fun checkElement(element: FirElement) {
                when (element) {
                    is FirFile, is FirFunction, is FirProperty, is FirRegularClass -> targets += element to this.context
                    else -> Unit
                }
            }
        }
        declaration.accept(visitor, null)
        val sink = FileVerificationSink(config.verificationWorkers)
        val lambdas = mutableSetOf<FirAnonymousFunctionSymbol>()
        val locator = VerifierLocator()
        val functions = ViperPoweredDeclarationChecker(session, config, locator, ReportedVerifierErrors(), sink, lambdas)
        val properties = ViperPoweredPropertyChecker(session, config, locator, ReportedVerifierErrors(), sink, lambdas)
        val classes = ViperPoweredClassChecker(session, config, locator, ReportedVerifierErrors(), sink, lambdas)
        val vacuity = SpecVacuityChecker(session, config, locator, sink)
        for ((element, targetContext) in targets) {
            with(targetContext) {
                with(reporter) {
                    when (element) {
                        is FirFile -> vacuity.check(element)
                        is FirFunction -> functions.check(element)
                        is FirProperty -> properties.check(element)
                        is FirRegularClass -> classes.check(element)
                    }
                }
            }
        }
        sink.flush()
    }
}
