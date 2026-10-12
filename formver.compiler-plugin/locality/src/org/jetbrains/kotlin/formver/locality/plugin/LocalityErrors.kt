package org.jetbrains.kotlin.formver.locality.plugin

import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.type.plugin.TypeFactDiagnostic

object LocalityErrors {
    val LOCALITY_MISMATCH = TypeFactDiagnostic(FailureKind.LOCALITY_MISMATCH, AttributeCondition::LocalityMismatch)
    val CONTEXT_LOCALITY_MISMATCH = TypeFactDiagnostic(FailureKind.CONTEXT_LOCALITY_MISMATCH, AttributeCondition::LocalityMismatch)
    val INVALID_LOCALITY_CAPTURE = FailureKind.INVALID_LOCALITY_CAPTURE
    val INVALID_LOCALITY_TYPE_TARGET = FailureKind.INVALID_LOCALITY_TYPE_TARGET
}
