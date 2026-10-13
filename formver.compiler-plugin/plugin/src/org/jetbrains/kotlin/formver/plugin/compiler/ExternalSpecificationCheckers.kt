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
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.formver.common.ExternalSpecRule
import org.jetbrains.kotlin.formver.common.Failure
import org.jetbrains.kotlin.formver.common.FailureKind
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.common.externalSpecifications
import org.jetbrains.kotlin.formver.common.reportFailure
import org.jetbrains.kotlin.formver.common.PluginConfiguration

/** Lists the trusted bindings of the session's registry when it has no errors. */
class ExternalSpecificationAssumptionsChecker(private val config: PluginConfiguration) : FirFileChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFile) {
        val registry = context.session.externalSpecifications ?: return
        config.externalSpecificationReporting.reportAssumptions(registry)
    }
}

/**
 * Reports binding errors on the model declarations they concern; the registry attaches every error to a source
 * declaration of the session, so each one is reported exactly once.
 */
object ExternalSpecificationBindingChecker : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirDeclaration) {
        val registry = context.session.externalSpecifications ?: return
        for (error in registry.errorsOf(declaration.symbol)) {
            reporter.reportFailure(Failure(FailureKind.INVALID_EXTERNAL_SPEC, declaration.source, error.condition))
        }
    }
}

/** Source implementations need a conformance proof rather than a dependency's trusted model. */
object ExternalSpecificationImplementationChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        val registry = context.session.externalSpecifications ?: return
        val visited = mutableSetOf<FirRegularClassSymbol>()
        fun modeledAncestor(symbol: FirRegularClassSymbol): FirRegularClassSymbol? {
            if (!visited.add(symbol)) return null
            if (registry.modelClass(symbol) != null) return symbol
            return symbol.resolvedSuperTypes.firstNotNullOfOrNull { type ->
                type.toRegularClassSymbol(context.session)?.let(::modeledAncestor)
            }
        }
        val ancestor = declaration.superTypeRefs.firstNotNullOfOrNull { ref ->
            ref.coneType.toRegularClassSymbol(context.session)?.let(::modeledAncestor)
        } ?: return
        val condition = KotlinCondition.ExternalSpec(ExternalSpecRule.SOURCE_SUBTYPE, ancestor.classId.asString())
        reporter.reportFailure(Failure(FailureKind.INVALID_EXTERNAL_SPEC, declaration.source, condition))
    }
}
