/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.services

import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar.ExtensionStorage
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.locality.plugin.LocalityExtensionRegistrar
import org.jetbrains.kotlin.formver.plugin.compiler.FormalVerificationPluginExtensionRegistrar
import org.jetbrains.kotlin.formver.plugin.services.FormVerDirectives.ALWAYS_VALIDATE
import org.jetbrains.kotlin.formver.plugin.services.FormVerDirectives.DUMP_UNIQUENESS_CFG
import org.jetbrains.kotlin.formver.plugin.services.FormVerDirectives.EXTERNAL_SPEC
import org.jetbrains.kotlin.formver.plugin.services.FormVerDirectives.EXTERNAL_TEST_LIBRARY
import org.jetbrains.kotlin.formver.plugin.services.FormVerDirectives.PROBE_BUNDLED_SPECS
import org.jetbrains.kotlin.formver.plugin.services.FormVerDirectives.FULL_VIPER_DUMP
import org.jetbrains.kotlin.formver.plugin.services.FormVerDirectives.LOCALITY_CHECK_ONLY
import org.jetbrains.kotlin.formver.plugin.services.FormVerDirectives.NEVER_VALIDATE
import org.jetbrains.kotlin.formver.plugin.services.FormVerDirectives.RENDER_PREDICATES
import org.jetbrains.kotlin.formver.plugin.services.FormVerDirectives.REPLACE_STDLIB_EXTENSIONS
import org.jetbrains.kotlin.formver.plugin.services.FormVerDirectives.UNIQUE_CHECK_ONLY
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessExtensionRegistrar
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives
import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer
import org.jetbrains.kotlin.test.model.TestFile
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.AdditionalSourceProvider
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestModuleStructure
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.moduleStructure
import org.jetbrains.kotlin.test.services.sourceFileProvider
import java.io.File

class ExtensionRegistrarConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override val directiveContainers: List<DirectivesContainer>
        get() = listOf(FormVerDirectives)

    override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
        for (path in module.directives[EXTERNAL_TEST_LIBRARY]) {
            configuration.addJvmClasspathRoot(ExternalTestLibraries.compiled(File(path)))
        }
    }

    override fun ExtensionStorage.registerCompilerExtensions(module: TestModule, configuration: CompilerConfiguration) {
        require(!(FULL_VIPER_DUMP in module.directives && RENDER_PREDICATES in module.directives)) {
            "Directives FULL_VIPER_DUMP and RENDER_PREDICATES cannot be present in the same test file."
        }

        val logLevel = when {
            FULL_VIPER_DUMP in module.directives -> LogLevel.FULL_VIPER_DUMP
            RENDER_PREDICATES in module.directives -> LogLevel.SHORT_VIPER_DUMP_WITH_PREDICATES
            else -> LogLevel.SHORT_VIPER_DUMP
        }
        val errorStyle = ErrorStyle.USER_FRIENDLY
        val conversionOnly = (System.getProperty("formver.conversionOnly")?.toBoolean() ?: false)
                || NEVER_VALIDATE in module.directives
        val uniquenessOnly = UNIQUE_CHECK_ONLY in module.directives
        val localityOnly = LOCALITY_CHECK_ONLY in module.directives
        // The dump needs the uniqueness extension, which LOCALITY_CHECK_ONLY leaves out.
        val dumpUniquenessCFG = DUMP_UNIQUENESS_CFG in module.directives && !localityOnly
        val verificationSelection = when {
            conversionOnly -> TargetsSelection.FORCE_DISABLE
            ALWAYS_VALIDATE in module.directives -> TargetsSelection.ALL_TARGETS
            uniquenessOnly || localityOnly -> TargetsSelection.NO_TARGETS
            else -> TargetsSelection.ALL_TARGETS
        }
        val conversionSelection = when {
            uniquenessOnly || localityOnly -> TargetsSelection.NO_TARGETS
            else -> TargetsSelection.ALL_TARGETS
        }
        val config = PluginConfiguration(
            logLevel,
            errorStyle,
            conversionSelection = conversionSelection,
            verificationSelection = verificationSelection,
            dumpUniquenessCFG = dumpUniquenessCFG,
            // Stack traces go to the test's stderr, outside the goldens.
            messageCollector = PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, true),
            externalSpecs = externalSpecs(module),
        )
        FirExtensionRegistrarAdapter.registerExtension(FormalVerificationPluginExtensionRegistrar(config))
        if (!config.externalSpecs.isEmpty()) {
            FirExtensionRegistrarAdapter.registerExtension(ExternalSpecExtensionRegistrar(config.externalSpecs))
        }
        // Locality must run before uniqueness.
        // LOCALITY_CHECK_ONLY runs locality alone; every other mode runs both.
        FirExtensionRegistrarAdapter.registerExtension(LocalityExtensionRegistrar())
        if (!localityOnly) {
            FirExtensionRegistrarAdapter.registerExtension(UniquenessExtensionRegistrar())
        }
    }

    /**
     * The spec files that [ExternalSpecProvider] added to [module] and to the common modules it depends on, which a
     * multiplatform compilation analyses with it, at the paths the compiler reads them from.
     */
    private fun externalSpecs(module: TestModule): ExternalSpecSources {
        val modules = generateSequence(listOf(module)) { level ->
            level.flatMap { m -> m.dependsOnDependencies.map { it.dependencyModule } }.ifEmpty { null }
        }.flatten().distinct()
        val bundledDirectory = File("formver.compiler-plugin/plugin/resources/external-specs").canonicalFile
        val specs = modules.flatMap { addedExternalSpecs(it) }.toList()
        if (PROBE_BUNDLED_SPECS in module.directives) return ExternalSpecSources(specs.map { it.file })
        val (bundled, user) = specs.partition { it.originalFile.canonicalFile.parentFile == bundledDirectory }
        return ExternalSpecSources(user.map { it.file }, bundled = bundled.map { it.file })
    }

    /** A spec file as the compiler reads it, and the test data file it was copied from. */
    private data class AddedSpec(val file: File, val originalFile: File)

    private fun addedExternalSpecs(module: TestModule): List<AddedSpec> {
        val requested = ExternalSpecProvider.specsFor(module, module.directives[EXTERNAL_SPEC])
            .mapTo(HashSet()) { it.canonicalFile }
        val added = module.files.filter { it.isAdditional && it.originalFile.canonicalFile in requested }
        val missing = requested - added.mapTo(HashSet()) { it.originalFile.canonicalFile }
        check(missing.isEmpty()) { "External specs $missing were not added to module ${module.name}." }
        return added.map { AddedSpec(testServices.sourceFileProvider.getOrCreateRealFileForSourceFile(it), it.originalFile) }
    }
}

object FormVerDirectives : SimpleDirectivesContainer() {
    val COUNTEREXAMPLES by directive(
        description = "Include counterexamples in verification diagnostics for uniquely constrained models.",
    )

    val DIAGNOSTIC_KINDS by directive(
        description = "Record diagnostic kinds instead of rendered messages"
    )

    val RENDER_PREDICATES by directive(
        description = "Outputs class predicates in diagnostic"
    )

    val FULL_VIPER_DUMP by directive(
        description = "Outputs the whole Viper code in diagnostic"
    )

    val ALWAYS_VALIDATE by directive(
        description = "Always validate functions"
    )

    val UNIQUE_CHECK_ONLY by directive(
        description = "Do uniqueness checking (and run locality first)"
    )

    val LOCALITY_CHECK_ONLY by directive(
        description = "Do locality checking"
    )

    val DUMP_UNIQUENESS_CFG by directive(
        description = "dumps the CFG augmented with flow information"
    )

    val REPLACE_STDLIB_EXTENSIONS by directive(
        description = "Use replacements for stdlib functions like run with accessible bodies"
    )

    val NEVER_VALIDATE by directive(
        description = "Run in conversion-only mode: skip verification, keep consistency checking"
    )

    val TRUST_REPORT by directive(
        description = "Compare the trust report of the verified declarations with the .trust.txt golden file"
    )

    val EXTERNAL_SPEC by stringDirective(
        description = "Analyse the Kotlin file at this path, relative to the repository root, as an external specification, and record its diagnostics"
    )

    val PROBE_BUNDLED_SPECS by directive(
        description = "Treat the bundled spec sources as user-supplied ones, which compilations check for contradictions"
    )

    val EXTERNAL_TEST_LIBRARY by stringDirective(
        description = "Compile the Kotlin file at this path, relative to the repository root, without the plugin and put it on the classpath"
    )
}

class StdlibReplacementsProvider(testServices: TestServices, baseDir: String = ".") :
    AdditionalSourceProvider(testServices) {
    private val libraryPath = "$baseDir/formver.compiler-plugin/testData/support/libraries/stdlibReplacements.kt"

    override fun produceAdditionalFiles(
        globalDirectives: RegisteredDirectives,
        module: TestModule,
        testModuleStructure: TestModuleStructure,
    ): List<TestFile> =
        if (containsDirective(globalDirectives, module, REPLACE_STDLIB_EXTENSIONS))
            listOf(File(libraryPath).toTestFile())
        else emptyList()

}

/**
 * Adds the spec sources that the plugin jar bundles, as the plugin does for every compilation, and those that the test
 * names with [EXTERNAL_SPEC].
 */
class ExternalSpecProvider(testServices: TestServices) : AdditionalSourceProvider(testServices) {
    override fun produceAdditionalFiles(
        globalDirectives: RegisteredDirectives,
        module: TestModule,
        testModuleStructure: TestModuleStructure,
    ): List<TestFile> = specsFor(module, globalDirectives[EXTERNAL_SPEC] + module.directives[EXTERNAL_SPEC]).mapIndexed { index, spec ->
        // Additional sources must not overwrite a test source with the same filename.
        spec.toTestFile(if (module.files.any { it.relativePath == spec.name }) "external-spec-$index" else null)
    }

    companion object {
        private val bundled: List<File> by lazy {
            ExternalSpecSources.bundledIn(File("formver.compiler-plugin/plugin/resources"))
        }

        /**
         * The spec files for [module] given the [named] paths. The bundled specs go into a module that depends on no
         * other fragment, which is where the plugin puts them in a multiplatform compilation.
         */
        fun specsFor(module: TestModule, named: List<String>): List<File> {
            val root = if (module.dependsOnDependencies.isEmpty()) bundled else emptyList()
            return (root + named.map(::File)).distinctBy { it.canonicalFile }
        }
    }
}
