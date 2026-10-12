/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.type.plugin

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.formver.common.FactPosition
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirCallChecker
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.unwrapAndFlattenArgument
import org.jetbrains.kotlin.fir.types.resolvedType

/**
 * Checker for type-fact compatibility in function calls.
 *
 * @param TypeFact the type-fact class of the arguments.
 * @param typeFactJudgment the type-fact judgment to use for checking type-fact compatibility.
 * @param expressionTypeFactResolver the resolver for resolving the type facts of the arguments.
 * @param callArgumentTypeFactsMapper for resolving the type-facts of the call's parameters.
 * @param contextDiagnosticFactory the diagnostic factory to use for reporting type-fact incompatibility in context
 *  parameters.
 * @param mismatchExplainer reports a mismatch whose cause it knows, in place of the checker's own diagnostic.
 */
class CallTypeFactChecker<TypeFact>(
    kind: MppCheckerKind,
    private val typeFactJudgment: TypeFactJudgment<TypeFact>,
    private val expressionTypeFactResolver: ExpressionTypeFactResolver<TypeFact>,
    private val callArgumentTypeFactsMapper: CallArgumentTypeFactsMapper<TypeFact>,
    private val argumentDiagnosticFactory: TypeFactDiagnostic<TypeFact>,
    private val contextDiagnosticFactory: TypeFactDiagnostic<TypeFact>,
    private val mismatchExplainer: TypeFactMismatchExplainer<TypeFact>? = null,
) : FirCallChecker(kind) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirCall) {
        val requiredTypes = callArgumentTypeFactsMapper.mapArgumentTypeFactsOf(expression)

        for ((argument, requiredTypeFact) in requiredTypes) {
            val effectiveArguments = argument.unwrapAndFlattenArgument(flattenArrays = false)

            for (effectiveArgument in effectiveArguments) {
                val actualTypeFact = expressionTypeFactResolver.resolveTypeFactOf(effectiveArgument)

                if (typeFactJudgment.satisfies(requiredTypeFact, actualTypeFact)) continue

                if (argument.source?.kind is KtFakeSourceElementKind.ImplicitContextParameterArgument) {
                    reporter.reportTypeFactFailure(
                        expression.source,
                        contextDiagnosticFactory,
                        argument.resolvedType,
                        requiredTypeFact,
                        actualTypeFact
                    )
                } else if (mismatchExplainer?.reportExplained(
                        effectiveArgument.source ?: argument.source, FactPosition.ARGUMENT, effectiveArgument,
                        requiredTypeFact, actualTypeFact
                    ) != true
                ) {
                    reporter.reportTypeFactFailure(
                        effectiveArgument.source ?: argument.source,
                        argumentDiagnosticFactory,
                        FactPosition.ARGUMENT,
                        requiredTypeFact,
                        actualTypeFact
                    )
                }
            }
        }
    }
}
