/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.type.plugin

import org.jetbrains.kotlin.formver.common.isExternalSpecificationFile
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.formver.common.FactPosition
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirQualifiedAccessExpressionChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.isEnabled
import org.jetbrains.kotlin.fir.types.resolvedType

/**
 * Checker for type-fact compatibility in qualified access expressions.
 *
 * @param TypeFact the type-fact class of the receiver and context arguments.
 * @param typeFactJudgment the type-fact judgment to use for checking type-fact compatibility.
 * @param expressionTypeFactResolver the resolver for resolving the actual type fact of the receiver and context
 *  arguments.
 * @param qualifiedAccessArgumentTypeFactMapper the mapper for resolving declared type facts of qualified-access
 *  receiver and context arguments.
 * @param receiverDiagnosticFactory the diagnostic factory to use for reporting a type-fact mismatch in the receiver.
 * @param contextArgumentDiagnosticFactory the diagnostic factory to use for reporting type-fact mismatch in the context
 *  arguments.
 * @param mismatchExplainer reports a mismatch whose cause it knows, in place of the checker's own diagnostic.
 */
class QualifiedAccessTypeFactChecker<TypeFact>(
    kind: MppCheckerKind,
    private val typeFactJudgment: TypeFactJudgment<TypeFact>,
    private val expressionTypeFactResolver: ExpressionTypeFactResolver<TypeFact>,
    private val qualifiedAccessArgumentTypeFactMapper: QualifiedAccessArgumentTypeFactMapper<TypeFact>,
    private val receiverDiagnosticFactory: TypeFactDiagnostic<TypeFact>,
    private val contextArgumentDiagnosticFactory: TypeFactDiagnostic<TypeFact>,
    private val mismatchExplainer: TypeFactMismatchExplainer<TypeFact>? = null,
) : FirQualifiedAccessExpressionChecker(kind) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirQualifiedAccessExpression) {
        if (context.session.isExternalSpecificationFile(context.containingFilePath)) return
        if (expression !is FirFunctionCall && expression !is FirPropertyAccessExpression) return

        for ((argument, requiredTypeFact) in qualifiedAccessArgumentTypeFactMapper.mapArgumentTypeFactsOf(expression)) {
            val actualTypeFact = expressionTypeFactResolver.resolveTypeFactOf(argument)

            if (typeFactJudgment.satisfies(requiredTypeFact, actualTypeFact)) continue

            if (argument in expression.contextArguments) {
                reporter.reportTypeFactFailure(
                    expression.source,
                    contextArgumentDiagnosticFactory,
                    argument.resolvedType,
                    requiredTypeFact,
                    actualTypeFact
                )
            } else if (mismatchExplainer?.reportExplained(
                    argument.source ?: expression.source, FactPosition.RECEIVER, argument, requiredTypeFact, actualTypeFact
                ) != true
            ) {
                reporter.reportTypeFactFailure(
                    argument.source ?: expression.source,
                    receiverDiagnosticFactory,
                    FactPosition.RECEIVER,
                    requiredTypeFact,
                    actualTypeFact
                )
            }
        }
    }
}
