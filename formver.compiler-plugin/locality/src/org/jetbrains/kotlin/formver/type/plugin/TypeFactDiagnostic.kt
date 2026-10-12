package org.jetbrains.kotlin.formver.type.plugin

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.formver.common.*

class TypeFactDiagnostic<T>(
    val kind: FailureKind,
    private val condition: (FactTarget, T, T) -> KotlinCondition,
) {
    fun failure(source: KtSourceElement?, target: FactTarget, expected: T, actual: T): Failure =
        Failure(kind, source, condition(target, expected, actual))
}

context(context: CheckerContext)
fun <T> DiagnosticReporter.reportTypeFactFailure(
    source: KtSourceElement?, diagnostic: TypeFactDiagnostic<T>, position: FactPosition, expected: T, actual: T,
) = reportFailure(diagnostic.failure(source, FactTarget.Value(position), expected, actual))

context(context: CheckerContext)
fun <T> DiagnosticReporter.reportTypeFactFailure(
    source: KtSourceElement?, diagnostic: TypeFactDiagnostic<T>, type: ConeKotlinType, expected: T, actual: T,
) = reportFailure(diagnostic.failure(source, FactTarget.Context(type), expected, actual))
