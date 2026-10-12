package org.jetbrains.kotlin.formver.core.diagnostics

import org.jetbrains.kotlin.formver.common.FailureKind

/** Conversion entry points use the shared failure registry. */
object ConversionErrors {
    val PURITY_VIOLATION get() = FailureKind.PURITY_VIOLATION
    val UNSUPPORTED_OWNERSHIP get() = FailureKind.UNSUPPORTED_OWNERSHIP
    val UNTRACKED_WRITE get() = FailureKind.UNTRACKED_WRITE
    val UNSUPPORTED_FEATURE get() = FailureKind.UNSUPPORTED_FEATURE
    val VERIFICATION_SKIPPED get() = FailureKind.VERIFICATION_SKIPPED
}
