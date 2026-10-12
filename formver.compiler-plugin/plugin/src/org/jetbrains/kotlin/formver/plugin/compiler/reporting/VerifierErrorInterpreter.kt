/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler.reporting

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.formver.common.ErrorStyle
import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.errors.VerifierError

private fun VerificationError.formatByErrorStyle(errorStyle: ErrorStyle): List<FormattedError> = when (errorStyle) {
    ErrorStyle.USER_FRIENDLY -> listOf(formatUserFriendly() ?: DefaultError(this))
    ErrorStyle.ORIGINAL_VIPER -> listOf(DefaultError(this))
    ErrorStyle.BOTH -> listOfNotNull(formatUserFriendly(), DefaultError(this))
}

context(context: CheckerContext)
private fun DiagnosticReporter.reportVerificationError(
    source: KtSourceElement?,
    error: VerificationError,
    errorStyle: ErrorStyle,
) = error.formatByErrorStyle(errorStyle).forEach { it.report(source) }

context(context: CheckerContext)
fun DiagnosticReporter.reportVerifierError(
    source: KtSourceElement?,
    error: VerifierError,
    errorStyle: ErrorStyle,
) = when (error) {
    is VerificationError -> reportVerificationError(source, error, errorStyle)
}
