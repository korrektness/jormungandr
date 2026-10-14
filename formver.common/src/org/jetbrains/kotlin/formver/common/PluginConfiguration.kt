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
    val verificationCacheDirectory: String? = null,
    val externalSpecs: ExternalSpecSources = ExternalSpecSources.NONE,
    val counterexamples: Boolean = false,
    /** Bounds the verification of each Viper program, in seconds; zero leaves it unbounded. */
    val verificationTimeoutSeconds: Int = DEFAULT_VERIFICATION_TIMEOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_VERIFICATION_TIMEOUT_SECONDS = 300
    }

    /** Includes selection and diagnostic settings conservatively in the verification identity. */
    val verificationCacheOptions: List<String>
        get() = listOf(logLevel.name, errorStyle.name, conversionSelection.name, verificationSelection.name, dumpUniquenessCFG.toString())

    val externalSpecificationReporting = ExternalSpecificationReporting(messageCollector)

    init {
        require(verificationTimeoutSeconds >= 0) { "The verification timeout may not be negative." }
        require(conversionSelection >= verificationSelection) {
            "Conversion options may not be stricter than verification options; converting $conversionSelection but verifying $verificationSelection."
        }
    }
}
