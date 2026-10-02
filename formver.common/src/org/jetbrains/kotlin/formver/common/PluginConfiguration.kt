/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.cli.common.messages.MessageCollector

data class PluginConfiguration(
    val logLevel: LogLevel,
    val errorStyle: ErrorStyle,
    val conversionSelection: TargetsSelection,
    val verificationSelection: TargetsSelection,
    val dumpUniquenessCFG: Boolean,
    /** Receives the stack traces of internal errors, which diagnostics do not carry. */
    val messageCollector: MessageCollector,
) {
    init {
        require(conversionSelection >= verificationSelection) {
            "Conversion options may not be stricter than verification options; converting $conversionSelection but verifying $verificationSelection."
        }
    }
}
