package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext

context(context: CheckerContext)
fun DiagnosticReporter.reportAttributeFailure(
    source: KtSourceElement?, kind: FailureKind,
) = reportFailure(Failure(kind, source, AttributeCondition.Rule(kind)))

fun pathFailure(
    source: KtSourceElement?, kind: FailureKind, path: KotlinPath,
    other: KotlinPath? = null, related: List<RelatedLocation> = emptyList(),
): Failure = Failure(kind, source, AttributeCondition.Path(path, other), related = related)

context(context: CheckerContext)
fun DiagnosticReporter.reportAttributeFailure(
    source: KtSourceElement?, kind: FailureKind, path: KotlinPath,
    related: List<RelatedLocation> = emptyList(),
) = reportFailure(pathFailure(source, kind, path, related = related))

context(context: CheckerContext)
fun DiagnosticReporter.reportAttributeFailure(
    source: KtSourceElement?, kind: FailureKind, path: KotlinPath, other: KotlinPath,
    related: List<RelatedLocation> = emptyList(),
) = reportFailure(pathFailure(source, kind, path, other, related))
