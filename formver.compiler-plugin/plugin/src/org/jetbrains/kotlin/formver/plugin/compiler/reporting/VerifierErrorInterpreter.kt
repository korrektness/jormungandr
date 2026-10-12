package org.jetbrains.kotlin.formver.plugin.compiler.reporting

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.formver.common.ErrorStyle
import org.jetbrains.kotlin.formver.common.FailureToFirRenderer
import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.errors.VerifierError

context(context: CheckerContext)
fun DiagnosticReporter.reportVerifierError(
    source: KtSourceElement?,
    error: VerifierError,
    errorStyle: ErrorStyle,
) {
    val failure = when (error) {
        is VerificationError -> error.toFailure(source)
    }.copy(errorStyle = errorStyle)
    FailureToFirRenderer.diagnostic(failure, context.languageVersionSettings)?.let { report(it, context) }
}
