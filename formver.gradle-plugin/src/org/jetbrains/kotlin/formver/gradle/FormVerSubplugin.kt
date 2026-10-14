/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.jetbrains.kotlin.formver.BuildConfig
import org.jetbrains.kotlin.formver.common.FormalVerificationPluginNames
import org.jetbrains.kotlin.formver.gradle.model.builder.FormVerModelBuilder
import org.jetbrains.kotlin.gradle.plugin.FilesOptionKind
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import javax.inject.Inject

class FormVerGradleSubplugin
@Inject internal constructor(
    private val registry: ToolingModelBuilderRegistry,
) : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create("formver", FormVerExtension::class.java)
        registry.register(FormVerModelBuilder())
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project

        kotlinCompilation.defaultSourceSet.dependencies {
            implementation(BuildConfig.ANNOTATIONS_LIBRARY_COORDINATES)
        }

        val formVerExtension = project.extensions.getByType(FormVerExtension::class.java)
        val externalSpecs = project.files(formVerExtension.myExternalSpecs)
        // KGP does not track files options as task inputs; without this, an edited spec leaves the compilation
        // up to date and lets the build cache restore output verified against the old spec.
        kotlinCompilation.compileTaskProvider.configure { task ->
            task.inputs.files(externalSpecs)
                .withPropertyName("formverExternalSpecs")
                .withPathSensitivity(PathSensitivity.RELATIVE)
        }

        // A metadata compilation generates no code, so it never writes the report.
        val trustReport = if (kotlinCompilation.platformType == KotlinPlatformType.common) null else
            project.layout.buildDirectory.file("formver/${kotlinCompilation.compileKotlinTaskName}/trust-report.txt")
        if (trustReport != null) {
            kotlinCompilation.compileTaskProvider.configure { task ->
                task.outputs.file(trustReport).withPropertyName("formverTrustReport")
                task.doLast { action ->
                    val report = trustReport.get().asFile
                    check(report.isFile) { "SnaKt wrote no trust report at $report." }
                    // The compiler puts the report's summary on its first line.
                    action.logger.lifecycle("${report.readText().substringBefore('\n')} Details: $report")
                }
            }
        }

        return project.provider {
            val options = mutableListOf<SubpluginOption>()

            trustReport?.let {
                options += FilesSubpluginOption(
                    FormalVerificationPluginNames.TRUST_REPORT_OPTION_NAME, listOf(it.get().asFile), FilesOptionKind.INTERNAL
                )
            }

            formVerExtension.myCounterexamples?.let {
                options += SubpluginOption(FormalVerificationPluginNames.COUNTEREXAMPLES_OPTION_NAME, it.toString())
            }

            formVerExtension.myVerificationCacheDirectory?.let {
                options += SubpluginOption(FormalVerificationPluginNames.VERIFICATION_CACHE_DIRECTORY_OPTION_NAME, project.file(it).absolutePath)
            }

            formVerExtension.myVerificationTimeout?.let {
                options += SubpluginOption(FormalVerificationPluginNames.VERIFICATION_TIMEOUT_OPTION_NAME, it.toString())
            }

            formVerExtension.myLogLevel?.let {
                options += SubpluginOption(FormalVerificationPluginNames.LOG_LEVEL_OPTION_NAME, it)
            }

            formVerExtension.myErrorStyle?.let {
                options += SubpluginOption(FormalVerificationPluginNames.ERROR_STYLE_NAME, it)
            }

            formVerExtension.myConversionTargetsSelection?.let {
                options += SubpluginOption(FormalVerificationPluginNames.CONVERSION_TARGETS_SELECTION_OPTION_NAME, it)
            }

            formVerExtension.myVerificationTargetsSelection?.let {
                options += SubpluginOption(FormalVerificationPluginNames.VERIFICATION_TARGETS_SELECTION_OPTION_NAME, it)
            }

            formVerExtension.myDumpUniquenessCFG?.let {
                options += SubpluginOption(FormalVerificationPluginNames.DUMP_UNIQUENESS_CFG_OPTION_NAME, it.toString())
            }

            // One option per file: a files option joins its files into a single value.
            for (spec in externalSpecs.files.sorted()) {
                options += FilesSubpluginOption(
                    FormalVerificationPluginNames.EXTERNAL_SPEC_OPTION_NAME, listOf(spec), FilesOptionKind.INTERNAL
                )
            }

            options
        }
    }

    override fun getCompilerPluginId(): String = FormalVerificationPluginNames.PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            BuildConfig.COMPILER_PLUGIN_GROUP,
            BuildConfig.COMPILER_PLUGIN_NAME,
            BuildConfig.COMPILER_PLUGIN_VERSION
        )
}
