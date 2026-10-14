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
import org.jetbrains.kotlin.config.HmppCliModule
import org.jetbrains.kotlin.config.HmppCliModuleStructure
import org.jetbrains.kotlin.config.hmppModuleStructure
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.locality.plugin.LocalityExtensionRegistrar
import org.jetbrains.kotlin.formver.plugin.compiler.FormalVerificationPluginExtensionRegistrar
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessExtensionRegistrar
import java.io.File

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
            configuration.getList(FormalVerificationConfigurationKeys.EXTERNAL_SPEC_FILES),
            bundled = ExternalSpecSources.extractBundled(File(FormalVerificationPluginComponentRegistrar::class.java.protectionDomain.codeSource.location.toURI())),
        )
        val specFragment = configuration.addToRootFragment(externalSpecs.files.map { it.path })
        for (file in externalSpecs.files) {
            configuration.addKotlinSourceRoot(file.path, isCommon = specFragment != null, hmppModuleName = specFragment)
            configuration.messageCollector.report(CompilerMessageSeverity.LOGGING, "Loading external specification $file")
        }
        val config = PluginConfiguration(
            logLevel, errorStyle, conversionSelection, verificationSelection,
            dumpUniquenessCFG, configuration.messageCollector,
            verificationCacheDirectory = configuration.get(FormalVerificationConfigurationKeys.VERIFICATION_CACHE_DIRECTORY),
            externalSpecs = externalSpecs,
            counterexamples = configuration.get(FormalVerificationConfigurationKeys.COUNTEREXAMPLES, false),
            verificationTimeoutSeconds = configuration.get(
                FormalVerificationConfigurationKeys.VERIFICATION_TIMEOUT, PluginConfiguration.DEFAULT_VERIFICATION_TIMEOUT_SECONDS,
            ),
            trustLedger = TrustLedger(),
        )
        FirExtensionRegistrarAdapter.registerExtension(FormalVerificationPluginExtensionRegistrar(config))
        IrGenerationExtension.registerExtension(
            TrustReportWriter(config.trustLedger!!, configuration.get(FormalVerificationConfigurationKeys.TRUST_REPORT), configuration.messageCollector)
        )
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

/**
 * Adds [paths] to the sources of the fragment that every other fragment of a multiplatform compilation depends on, so
 * that the specifications they hold reach code in every fragment. Returns that fragment's name, or null when the
 * compilation has no fragment structure or no single root fragment; the compiler then puts [paths] in the platform
 * fragment, where common code does not see them.
 */
private fun CompilerConfiguration.addToRootFragment(paths: List<String>): String? {
    val structure = hmppModuleStructure ?: return null
    val root = structure.modules.filter { structure.sourceDependencies[it].isNullOrEmpty() }.singleOrNull() ?: return null
    val extended = HmppCliModule(root.name, root.sources + paths)
    fun HmppCliModule.updated() = if (this == root) extended else this
    hmppModuleStructure = HmppCliModuleStructure(
        structure.modules.map { it.updated() },
        structure.sourceDependencies.entries.associate { (module, dependencies) -> module.updated() to dependencies.map { it.updated() } },
        structure.moduleDependencies.mapKeys { it.key.updated() },
        structure.friendDependencies.mapKeys { it.key.updated() },
    )
    return root.name
}
