/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.formver.common.KotlinCondition

val ProgramConverter.verificationSkipReason: KotlinCondition.SkipReason?
    get() = when {
        hadUniquenessError -> KotlinCondition.SkipReason.UNIQUENESS_OR_LOCALITY_ERRORS
        hadOwnershipError -> KotlinCondition.SkipReason.UNSUPPORTED_OWNERSHIP
        hadMissingMeasure -> KotlinCondition.SkipReason.MISSING_TERMINATION_MEASURE
        else -> null
    }

val ProgramConverter.skipsVerification: Boolean
    get() = verificationSkipReason != null
