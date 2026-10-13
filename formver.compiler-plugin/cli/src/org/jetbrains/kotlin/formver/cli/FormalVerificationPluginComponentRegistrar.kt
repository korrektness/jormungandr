/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.cli

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.locality.plugin.LocalityExtensionRegistrar
import org.jetbrains.kotlin.formver.plugin.compiler.FormalVerificationPluginExtensionRegistrar
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessExtensionRegistrar

@OptIn(ExperimentalCompilerApi::class)
class FormalVerificationPluginComponentRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = FormalVerificationPluginNames.PLUGIN_ID

    override val supportsK2: Boolean
        get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val logLevel =
            configuration.get(FormalVerificationConfigurationKeys.LOG_LEVEL, LogLevel.Companion.defaultLogLevel())
        val errorStyle = configuration.get(
            FormalVerificationConfigurationKeys.ERROR_STYLE,
            ErrorStyle.Companion.defaultBehaviour()
        )
        val conversionSelection = configuration.get(
            FormalVerificationConfigurationKeys.CONVERSION_TARGETS_SELECTION,
            TargetsSelection.Companion.defaultBehaviour()
        )
        val verificationSelection = configuration.get(
            FormalVerificationConfigurationKeys.VERIFICATION_TARGETS_SELECTION,
            TargetsSelection.Companion.defaultBehaviour()
        )
        // The dump needs the uniqueness extension, which is registered only when converting.
        val dumpUniquenessCFG = configuration.get(FormalVerificationConfigurationKeys.DUMP_UNIQUENESS_CFG, false)
                && conversionSelection != TargetsSelection.NO_TARGETS
        val externalSpecs = ExternalSpecSources(
            configuration.getList(FormalVerificationConfigurationKeys.EXTERNAL_SPEC_FILES) +
                    ExternalSpecSources.extractBundled(this@FormalVerificationPluginComponentRegistrar.javaClass.classLoader)
        )
        for (file in externalSpecs.files) {
            configuration.addKotlinSourceRoot(file.path)
            configuration.messageCollector.report(CompilerMessageSeverity.LOGGING, "Loading external specification $file")
        }
        val config = PluginConfiguration(
            logLevel, errorStyle, conversionSelection, verificationSelection,
            dumpUniquenessCFG, configuration.messageCollector,
            verificationCacheDirectory = configuration.get(FormalVerificationConfigurationKeys.VERIFICATION_CACHE_DIRECTORY),
            externalSpecs = externalSpecs,
            counterexamples = configuration.get(FormalVerificationConfigurationKeys.COUNTEREXAMPLES, false),
        )
        FirExtensionRegistrarAdapter.registerExtension(FormalVerificationPluginExtensionRegistrar(config))
        if (!externalSpecs.isEmpty()) {
            FirExtensionRegistrarAdapter.registerExtension(ExternalSpecExtensionRegistrar(externalSpecs))
            IrGenerationExtension.registerExtension(ExternalSpecIrStripper(externalSpecs))
        }

        if (config.conversionSelection != TargetsSelection.NO_TARGETS) {
            // Locality must run before uniqueness.
            FirExtensionRegistrarAdapter.registerExtension(LocalityExtensionRegistrar())
            FirExtensionRegistrarAdapter.registerExtension(UniquenessExtensionRegistrar())
        }
    }
}
