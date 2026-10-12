/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.common

enum class TargetsSelection {
    FORCE_DISABLE, NO_TARGETS, ALL_TARGETS;


    companion object {
        @JvmStatic
        fun defaultBehaviour(): TargetsSelection {
            return ALL_TARGETS
        }
    }
}
