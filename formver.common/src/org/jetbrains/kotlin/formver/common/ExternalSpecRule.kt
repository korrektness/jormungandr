/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.common

/** Violated binding constraint of an external specification model. */
enum class ExternalSpecRule {
    MISSING_TARGET_ARGUMENT, INVALID_TARGET_NAME, OUTSIDE_SPECIFICATION_INPUTS, FUNCTION_NOT_TOP_LEVEL,
    MEMBER_OUTSIDE_CLASS_MODEL, CLASS_MODEL_NOT_INTERFACE, AMBIGUOUS_CLASS, UNRESOLVED_CLASS, SOURCE_TARGET,
    DUPLICATE_CLASS, TYPE_PARAMETERS, TYPE_PARAMETER_BOUNDS, UNSUPPORTED_MEMBER, NO_COMPATIBLE_TARGET,
    DUPLICATE_MEMBER, SOURCE_SUBTYPE,
}
