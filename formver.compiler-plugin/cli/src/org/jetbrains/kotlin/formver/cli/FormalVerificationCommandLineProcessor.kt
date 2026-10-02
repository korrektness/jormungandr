/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.cli

import org.jetbrains.kotlin.compiler.plugin.*
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.formver.cli.FormalVerificationConfigurationKeys.CONVERSION_TARGETS_SELECTION
import org.jetbrains.kotlin.formver.cli.FormalVerificationConfigurationKeys.DUMP_UNIQUENESS_CFG
import org.jetbrains.kotlin.formver.cli.FormalVerificationConfigurationKeys.ERROR_STYLE
import org.jetbrains.kotlin.formver.cli.FormalVerificationConfigurationKeys.LOG_LEVEL
import org.jetbrains.kotlin.formver.cli.FormalVerificationConfigurationKeys.VERIFICATION_TARGETS_SELECTION
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.common.FormalVerificationPluginNames.CONVERSION_TARGETS_SELECTION_OPTION_NAME
import org.jetbrains.kotlin.formver.common.FormalVerificationPluginNames.DUMP_UNIQUENESS_CFG_OPTION_NAME
import org.jetbrains.kotlin.formver.common.FormalVerificationPluginNames.ERROR_STYLE_NAME
import org.jetbrains.kotlin.formver.common.FormalVerificationPluginNames.LOG_LEVEL_OPTION_NAME
import org.jetbrains.kotlin.formver.common.FormalVerificationPluginNames.VERIFICATION_TARGETS_SELECTION_OPTION_NAME
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly

object FormalVerificationConfigurationKeys {
    val LOG_LEVEL: CompilerConfigurationKey<LogLevel> = CompilerConfigurationKey.create("viper log level")
    val ERROR_STYLE: CompilerConfigurationKey<ErrorStyle> = CompilerConfigurationKey.create("error style")
    val CONVERSION_TARGETS_SELECTION: CompilerConfigurationKey<TargetsSelection> =
        CompilerConfigurationKey.create("conversion targets selection")
    val VERIFICATION_TARGETS_SELECTION: CompilerConfigurationKey<TargetsSelection> =
        CompilerConfigurationKey.create("verification targets selection")
    val DUMP_UNIQUENESS_CFG: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create("dump uniqueness CFG")
}

@OptIn(ExperimentalCompilerApi::class)
class FormalVerificationCommandLineProcessor : CommandLineProcessor {
    companion object {
        val LOG_LEVEL_OPTION = CliOption(
            LOG_LEVEL_OPTION_NAME, "<log_level>", "Viper log level",
            required = false, allowMultipleOccurrences = false
        )
        val ERROR_STYLE_OPTION = CliOption(
            ERROR_STYLE_NAME, "<error_style>", "Style of error messages",
            required = false, allowMultipleOccurrences = false
        )
        val CONVERSION_TARGETS_SELECTION_OPTION = CliOption(
            CONVERSION_TARGETS_SELECTION_OPTION_NAME,
            "<conversion_targets_selection>",
            "Choice of targets to convert to Viper",
            required = false,
            allowMultipleOccurrences = false
        )
        val VERIFICATION_TARGETS_SELECTION_OPTION = CliOption(
            VERIFICATION_TARGETS_SELECTION_OPTION_NAME,
            "<verification_targets_selection>",
            "Choice of targets to verify",
            required = false,
            allowMultipleOccurrences = false
        )
        val DUMP_UNIQUENESS_CFG_OPTION = CliOption(
            DUMP_UNIQUENESS_CFG_OPTION_NAME,
            "<true|false>",
            "Dump the uniqueness CFG augmented with flow information",
            required = false,
            allowMultipleOccurrences = false
        )
    }

    override val pluginId: String = FormalVerificationPluginNames.PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        LOG_LEVEL_OPTION,
        ERROR_STYLE_OPTION,
        CONVERSION_TARGETS_SELECTION_OPTION,
        VERIFICATION_TARGETS_SELECTION_OPTION,
        DUMP_UNIQUENESS_CFG_OPTION,
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) =
        try {
            when (option) {
                LOG_LEVEL_OPTION -> configuration.put(LOG_LEVEL, LogLevel.valueOf(value.toUpperCaseAsciiOnly()))
                ERROR_STYLE_OPTION ->
                    configuration.put(ERROR_STYLE, ErrorStyle.valueOf(value.toUpperCaseAsciiOnly()))

                CONVERSION_TARGETS_SELECTION_OPTION ->
                    configuration.put(
                        CONVERSION_TARGETS_SELECTION,
                        TargetsSelection.valueOf(value.toUpperCaseAsciiOnly())
                    )

                VERIFICATION_TARGETS_SELECTION_OPTION ->
                    configuration.put(
                        VERIFICATION_TARGETS_SELECTION,
                        TargetsSelection.valueOf(value.toUpperCaseAsciiOnly())
                    )

                DUMP_UNIQUENESS_CFG_OPTION ->
                    configuration.put(DUMP_UNIQUENESS_CFG, value.toBooleanStrict())

                else -> throw CliOptionProcessingException("Unknown option: ${option.optionName}")
            }
        } catch (e: IllegalArgumentException) {
            throw CliOptionProcessingException(
                "Value $value is not a valid argument for option ${option.optionName}.",
                e
            )
        }
}
