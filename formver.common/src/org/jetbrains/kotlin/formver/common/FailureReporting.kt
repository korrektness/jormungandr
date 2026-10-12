package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext

context(context: CheckerContext)
fun DiagnosticReporter.reportFailure(failure: Failure) {
    FailureToFirRenderer.diagnostic(failure, context.languageVersionSettings)?.let { report(it, context) }
}

context(context: CheckerContext)
fun DiagnosticReporter.reportLaw(
    source: KtSourceElement?, condition: KotlinCondition.Law,
    related: List<RelatedLocation> = emptyList(),
) {
    val subject = condition.subject?.source?.takeUnless { it == source }?.let { RelatedLocation("law subject", it) }
    reportFailure(Failure(FailureKind.INVALID_LAW, source, condition, related = related + listOfNotNull(subject)))
}

context(context: CheckerContext)
fun DiagnosticReporter.reportObserver(source: KtSourceElement?, condition: KotlinCondition.Observer) {
    val related = listOfNotNull(
        condition.member?.source?.takeUnless { it == source }?.let { RelatedLocation("observer declaration", it) },
        condition.implementation?.source?.takeUnless { it == source }?.let { RelatedLocation("observer implementation", it) },
    )
    reportFailure(Failure(FailureKind.INVALID_OBSERVER, source, condition, related = related))
}

context(context: CheckerContext)
fun DiagnosticReporter.reportTool(
    source: KtSourceElement?, kind: FailureKind, reason: String, detail: String,
    related: List<RelatedLocation> = emptyList(),
) {
    require(kind.category == FailureCategory.TOOL)
    reportFailure(Failure(kind, source, KotlinCondition.Tool(reason, detail), related = related))
}

context(context: CheckerContext)
fun DiagnosticReporter.reportInternalFailure(source: KtSourceElement?, exception: SnaktInternalException) {
    reportTool(source, FailureKind.INTERNAL_ERROR, exception.reason.name, exception.message,
        listOfNotNull(exception.source?.takeUnless { it == source }?.let { RelatedLocation("failure origin", it) }))
}

context(context: CheckerContext)
fun DiagnosticReporter.reportTranslation(
    source: KtSourceElement?,
    kind: FailureKind,
    detail: String = "",
    reason: String = kind.name,
    related: List<RelatedLocation> = emptyList(),
) {
    require(kind.category == FailureCategory.TRANSLATION)
    val condition = if (kind == FailureKind.UNTRACKED_WRITE) KotlinCondition.UntrackedWrite
        else KotlinCondition.Translation(reason, detail)
    reportFailure(Failure(kind, source, condition, related = related))
}
