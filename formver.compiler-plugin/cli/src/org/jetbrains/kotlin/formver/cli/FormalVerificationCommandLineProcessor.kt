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
import org.jetbrains.kotlin.formver.cli.FormalVerificationConfigurationKeys.EXTERNAL_SPEC_FILES
import org.jetbrains.kotlin.formver.cli.FormalVerificationConfigurationKeys.LOG_LEVEL
import org.jetbrains.kotlin.formver.cli.FormalVerificationConfigurationKeys.VERIFICATION_TARGETS_SELECTION
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.common.FormalVerificationPluginNames.CONVERSION_TARGETS_SELECTION_OPTION_NAME
import org.jetbrains.kotlin.formver.common.FormalVerificationPluginNames.DUMP_UNIQUENESS_CFG_OPTION_NAME
import org.jetbrains.kotlin.formver.common.FormalVerificationPluginNames.ERROR_STYLE_NAME
import org.jetbrains.kotlin.formver.common.FormalVerificationPluginNames.EXTERNAL_SPEC_OPTION_NAME
import org.jetbrains.kotlin.formver.common.FormalVerificationPluginNames.LOG_LEVEL_OPTION_NAME
import org.jetbrains.kotlin.formver.common.FormalVerificationPluginNames.VERIFICATION_TARGETS_SELECTION_OPTION_NAME
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly
import java.io.File

object FormalVerificationConfigurationKeys {
    val COUNTEREXAMPLES: CompilerConfigurationKey<Boolean> = CompilerConfigurationKey.create("counterexamples")
    val VERIFICATION_CACHE_DIRECTORY: CompilerConfigurationKey<String> = CompilerConfigurationKey.create("verification cache directory")
    val VERIFICATION_TIMEOUT: CompilerConfigurationKey<Int> = CompilerConfigurationKey.create("verification timeout")
    val LOG_LEVEL: CompilerConfigurationKey<LogLevel> = CompilerConfigurationKey.create("viper log level")
    val ERROR_STYLE: CompilerConfigurationKey<ErrorStyle> = CompilerConfigurationKey.create("error style")
    val CONVERSION_TARGETS_SELECTION: CompilerConfigurationKey<TargetsSelection> =
        CompilerConfigurationKey.create("conversion targets selection")
    val VERIFICATION_TARGETS_SELECTION: CompilerConfigurationKey<TargetsSelection> =
        CompilerConfigurationKey.create("verification targets selection")
    val DUMP_UNIQUENESS_CFG: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create("dump uniqueness CFG")
    val EXTERNAL_SPEC_FILES: CompilerConfigurationKey<List<File>> =
        CompilerConfigurationKey.create("external specification sources")
    val TRUST_REPORT: CompilerConfigurationKey<File> = CompilerConfigurationKey.create("trust report")
}

@OptIn(ExperimentalCompilerApi::class)
class FormalVerificationCommandLineProcessor : CommandLineProcessor {
    companion object {
        val COUNTEREXAMPLES_OPTION = CliOption(
            FormalVerificationPluginNames.COUNTEREXAMPLES_OPTION_NAME, "<true|false>",
            "Generate mapped counterexamples", required = false, allowMultipleOccurrences = false,
        )
        val VERIFICATION_CACHE_DIRECTORY_OPTION = CliOption(
            FormalVerificationPluginNames.VERIFICATION_CACHE_DIRECTORY_OPTION_NAME, "<directory>",
            "Directory for successful verification results", required = false, allowMultipleOccurrences = false
        )
        val VERIFICATION_TIMEOUT_OPTION = CliOption(
            FormalVerificationPluginNames.VERIFICATION_TIMEOUT_OPTION_NAME, "<seconds>",
            "Time limit for verifying each declaration; 0 disables it", required = false, allowMultipleOccurrences = false
        )
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
        val EXTERNAL_SPEC_OPTION = CliOption(
            EXTERNAL_SPEC_OPTION_NAME,
            "<path>",
            "Kotlin source file specifying external code; analysed but not compiled",
            required = false,
            allowMultipleOccurrences = true
        )
        val TRUST_REPORT_OPTION = CliOption(
            FormalVerificationPluginNames.TRUST_REPORT_OPTION_NAME,
            "<path>",
            "File listing each verified declaration with the trusted assumptions it relies on",
            required = false,
            allowMultipleOccurrences = false
        )
    }

    override val pluginId: String = FormalVerificationPluginNames.PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        COUNTEREXAMPLES_OPTION,
        VERIFICATION_CACHE_DIRECTORY_OPTION,
        VERIFICATION_TIMEOUT_OPTION,
        LOG_LEVEL_OPTION,
        ERROR_STYLE_OPTION,
        CONVERSION_TARGETS_SELECTION_OPTION,
        VERIFICATION_TARGETS_SELECTION_OPTION,
        DUMP_UNIQUENESS_CFG_OPTION,
        EXTERNAL_SPEC_OPTION,
        TRUST_REPORT_OPTION,
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) =
        try {
            when (option) {
                COUNTEREXAMPLES_OPTION -> configuration.put(FormalVerificationConfigurationKeys.COUNTEREXAMPLES, value.toBooleanStrict())
                VERIFICATION_CACHE_DIRECTORY_OPTION -> {
                    require(value.isNotBlank())
                    configuration.put(FormalVerificationConfigurationKeys.VERIFICATION_CACHE_DIRECTORY, value)
                }
                VERIFICATION_TIMEOUT_OPTION -> {
                    val seconds = value.toInt()
                    require(seconds >= 0)
                    configuration.put(FormalVerificationConfigurationKeys.VERIFICATION_TIMEOUT, seconds)
                }
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

                EXTERNAL_SPEC_OPTION -> {
                    val file = File(value)
                    require(file.isFile && file.extension == "kt") { "$value is not a Kotlin source file." }
                    configuration.add(EXTERNAL_SPEC_FILES, file)
                }

                TRUST_REPORT_OPTION -> {
                    require(value.isNotBlank())
                    configuration.put(FormalVerificationConfigurationKeys.TRUST_REPORT, File(value))
                }

                else -> throw CliOptionProcessingException("Unknown option: ${option.optionName}")
            }
        } catch (e: IllegalArgumentException) {
            throw CliOptionProcessingException(
                "Value $value is not a valid argument for option ${option.optionName}.",
                e
            )
        }
}
