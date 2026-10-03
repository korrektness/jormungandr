/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.unwrapExpression
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.formver.type.plugin.AssignmentTypeFactChecker
import org.jetbrains.kotlin.formver.type.plugin.CallTypeFactChecker
import org.jetbrains.kotlin.formver.type.plugin.PropertyTypeFactChecker
import org.jetbrains.kotlin.formver.type.plugin.QualifiedAccessTypeFactChecker
import org.jetbrains.kotlin.formver.type.plugin.ReturnTypeFactChecker
import org.jetbrains.kotlin.formver.type.plugin.ThrowTypeFactChecker
import org.jetbrains.kotlin.formver.type.plugin.TypeFactMismatchExplainer
import org.jetbrains.kotlin.formver.type.plugin.ValueParameterTypeFactChecker
import org.jetbrains.kotlin.formver.type.plugin.removeCast

val AssignmentUniquenessChecker = AssignmentTypeFactChecker(
    kind = MppCheckerKind.Common,
    typeFactJudgment = UniquenessJudgment,
    expressionTypeFactResolver = ExpressionUniquenessResolver,
    diagnosticFactory = UniquenessErrors.UNIQUENESS_MISMATCH,
    mismatchExplainer = SharedConstructionExplainer,
)

val CallUniquenessChecker = CallTypeFactChecker(
    kind = MppCheckerKind.Common,
    typeFactJudgment = UniquenessJudgment,
    expressionTypeFactResolver = ExpressionUniquenessResolver,
    callArgumentTypeFactsMapper = CallArgumentUniquenessesMapper,
    argumentDiagnosticFactory = UniquenessErrors.UNIQUENESS_MISMATCH,
    contextDiagnosticFactory = UniquenessErrors.CONTEXT_UNIQUENESS_MISMATCH,
    mismatchExplainer = SharedConstructionExplainer,
)

val PropertyUniquenessChecker = PropertyTypeFactChecker(
    kind = MppCheckerKind.Common,
    typeFactJudgment = UniquenessJudgment,
    expressionTypeFactResolver = ExpressionUniquenessResolver,
    variableTypeFactResolver = VariableUniquenessResolver,
    diagnosticFactory = UniquenessErrors.UNIQUENESS_MISMATCH,
    mismatchExplainer = SharedConstructionExplainer,
)

val QualifiedAccessUniquenessChecker = QualifiedAccessTypeFactChecker(
    kind = MppCheckerKind.Common,
    typeFactJudgment = UniquenessJudgment,
    expressionTypeFactResolver = ExpressionUniquenessResolver,
    qualifiedAccessArgumentTypeFactMapper = QualifiedAccessArgumentUniquenessMapper,
    receiverDiagnosticFactory = UniquenessErrors.UNIQUENESS_MISMATCH,
    contextArgumentDiagnosticFactory = UniquenessErrors.CONTEXT_UNIQUENESS_MISMATCH,
    mismatchExplainer = SharedConstructionExplainer,
)

val ReturnUniquenessChecker = ReturnTypeFactChecker(
    kind = MppCheckerKind.Common,
    typeFactJudgment = UniquenessJudgment,
    expressionTypeFactResolver = ExpressionUniquenessResolver,
    returnResultTypeFactResolver = { expression -> expression.resolveResultUniqueness() },
    diagnosticFactory = UniquenessErrors.UNIQUENESS_MISMATCH,
    mismatchExplainer = SharedConstructionExplainer,
)

val ThrowUniquenessChecker = ThrowTypeFactChecker(
    kind = MppCheckerKind.Common,
    typeFactJudgment = UniquenessJudgment,
    expressionTypeFactResolver = ExpressionUniquenessResolver,
    throwExceptionTypeFactResolver = { Uniqueness.Shared },
    diagnosticFactory = UniquenessErrors.UNIQUENESS_MISMATCH,
)

val ValueParameterUniquenessChecker = ValueParameterTypeFactChecker(
    kind = MppCheckerKind.Common,
    typeFactJudgment = UniquenessJudgment,
    expressionTypeFactResolver = ExpressionUniquenessResolver,
    parameterDeclaredTypeFactResolver = ParameterUniquenessResolver,
    diagnosticFactory = UniquenessErrors.UNIQUENESS_MISMATCH,
    mismatchExplainer = SharedConstructionExplainer,
)

/**
 * Explains a uniqueness mismatch at a constructor call whose class lets the object under construction escape.
 */
object SharedConstructionExplainer : TypeFactMismatchExplainer<Uniqueness> {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun reportExplained(
        source: KtSourceElement?,
        position: String,
        expression: FirExpression,
        requiredTypeFact: Uniqueness,
        actualTypeFact: Uniqueness,
    ): Boolean {
        if (requiredTypeFact != Uniqueness.Unique) return false
        val call = expression.unwrapExpression().removeCast() as? FirFunctionCall ?: return false
        val constructor = call.calleeReference.symbol as? FirConstructorSymbol ?: return false
        val escape = constructor.resolveConstructionEscape(context.session) ?: return false
        reporter.reportOn(source, UniquenessErrors.SHARED_CONSTRUCTION_MISMATCH, position, escape)
        return true
    }
}
