package org.jetbrains.kotlin.formver.viper

import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import scala.jdk.javaapi.CollectionConverters
import viper.silver.reporter.StdIOReporter
import java.io.Closeable
import java.io.File
import java.nio.file.Path

/** The verifier cannot run in this environment. */
class VerifierUnavailableException(message: String) : Exception(message)

/** Viper rejected a program as malformed, so verifying it says nothing about the program's correctness. */
class InvalidViperProgramException(errors: List<String>) :
    Exception("Viper rejected the generated program: ${errors.joinToString("; ")}")

/**
 * Passes Viper programs for verification to the Silicon backend via [viper.silicon.SiliconFrontendAPI], using the Z3
 * binary at [z3Exe], as found by [SiliconFrontend.locateZ3].
 * Use [SiliconFrontend.verify] to consistency-check and verify a given program.
 */
class SiliconFrontend(
    z3Exe: String,
    commandLineArgs: List<String> = emptyList(),
    cacheDirectory: Path? = null,
    pluginOptions: List<String> = emptyList(),
    counterexamples: Boolean = false,
) : Closeable {
    private val args = buildList {
        addAll(commandLineArgs)
        if (counterexamples) {
            add("--counterexample")
            add("mapped")
            add("--exhaleMode")
            add("1")
        }
        add("--z3Exe")
        add(z3Exe)
        add("--numberOfParallelVerifiers")
        add(System.getenv("SILICON_PARALLEL_VERIFIERS") ?: "2")
    }
    private val cache = cacheDirectory?.let { VerificationResultCache(it) }
    private val cacheOptions = args + pluginOptions + listOf(
        System.getProperty("java.runtime.version"), System.getProperty("os.name"), System.getProperty("os.arch"),
    )
    private val cacheVersion = cacheDirectory?.let {
        // Custom plugins and obligation filters can change semantics beyond the printed program.
        require(commandLineArgs.chunked(2).all { pair ->
            pair.size == 2 && pair[0] in cacheableOptions && pair[1].toLongOrNull() != null
        }) { "Verification caching requires supported numeric Silicon options." }
        VerificationResultCache.fingerprint(listOf(
            artifactDigest(viper.silicon.SiliconFrontendAPI::class.java),
            artifactDigest(viper.silver.ast.Program::class.java),
            VerificationResultCache.fileDigest(Path.of(z3Exe)),
        ))
    }
    private var siliconApi: viper.silicon.SiliconFrontendAPI? = null

    private fun backend(): viper.silicon.SiliconFrontendAPI = siliconApi ?: run {
        val api = viper.silicon.SiliconFrontendAPI(StdIOReporter("stdout_reporter", true))
        try {
            api.initialize(args.toScalaSeq())
        } catch (error: Throwable) {
            try {
                api.stop()
            } catch (cleanup: Throwable) {
                error.addSuppressed(cleanup)
            }
            throw error
        }
        siliconApi = api
        api
    }

    companion object {
        private val configurationGate = SiliconConfigurationGate()
        private val cacheableOptions = setOf(
            "--timeout", "--assertTimeout", "--proverSaturationTimeout", "--randomizeSeeds",
        )

        private fun artifactDigest(type: Class<*>): String {
            val path = Path.of(type.protectionDomain.codeSource.location.toURI())
            require(path.toFile().isFile) { "Verification caching requires a packaged Silicon/Silver artifact: $path" }
            return VerificationResultCache.fileDigest(path)
        }

        /**
         * Returns the absolute path of the Z3 binary named by the environment, or throws [VerifierUnavailableException].
         */
        fun locateZ3(): String = resolveZ3Exe(System.getenv("Z3_EXE"), System.getenv("PATH"))

        /**
         * Returns the absolute path of the Z3 binary. Silicon takes `Z3_EXE` as a literal file path, so a bare
         * command name (or the default `z3` when `Z3_EXE` is unset) is looked up on [path] here.
         */
        private fun resolveZ3Exe(z3Exe: String?, path: String?): String {
            val name = z3Exe?.takeIf { it.isNotEmpty() } ?: defaultZ3Name()
            if (File(name).isAbsolute) {
                if (!File(name).isFile) throw VerifierUnavailableException("Z3_EXE is set to '$name', which is not a file.")
                return name
            }
            if (File(name).parent != null) throw VerifierUnavailableException("Z3_EXE is the relative path '$name'.")
            return path.orEmpty().split(File.pathSeparator)
                .filter { it.isNotEmpty() }
                .map { File(it, name) }
                .firstOrNull { it.isFile && it.canExecute() }
                ?.absolutePath
                ?: throw VerifierUnavailableException("Cannot find Z3 from Z3_EXE: '$name' is not on PATH.")
        }

        /**
         * Throws [InvalidViperProgramException] when [viperProgram] fails Viper's consistency checks, such as a
         * reference to an undeclared variable.
         */
        fun checkConsistency(viperProgram: viper.silver.ast.Program) {
            val errors = viperProgram.checkTransitively()
            if (!errors.isEmpty) throw InvalidViperProgramException(CollectionConverters.asJava(errors).map { it.readableMessage() })
        }

        private fun defaultZ3Name(): String =
            if (System.getProperty("os.name").startsWith("Windows")) "z3.exe" else "z3"

        init {
            // SiliconFrontendAPI's constructor calls ViperStdOutLogger, which casts LoggerFactory.getILoggerFactory()
            // to LoggerContext. Under concurrent construction (parallel tests), SLF4J's first-time initialization may
            // still be in progress and return a SubstituteLoggerFactory instead, causing a ClassCastException.
            //
            // Calling getLogger here forces logback to fully initialize exactly once, under the JVM's
            // class-initialization guarantee, before any constructor runs.
            //
            // This issue is known to the Silicon team: https://github.com/viperproject/silicon/issues/968
            org.slf4j.LoggerFactory.getLogger(SiliconFrontend::class.java)
        }
    }

    /**
     * Consistency-checks and verifies [viperProgram], calling [onFailure] for each verification error found.
     * Throws [InvalidViperProgramException] when Viper reports anything other than verification errors.
     */
    fun verify(viperProgram: viper.silver.ast.Program, onFailure: (VerifierError) -> Unit) {
        // The AST API starts in the state after the consistency check, so its ConsistencyCheck phase does nothing.
        // Silicon does not check the AST itself either: an undeclared variable makes it abort with an exception,
        // which the result reports as an error that is not a verification error.
        checkConsistency(viperProgram)
        // SilFrontend.verification invokes the default plugins' beforeVerify and mapVerificationResult hooks,
        // including RefutePlugin. Its isolated assertion branch leaves the continuation available for further checks.
        val verify = {
            val result = configurationGate.withConfiguration(args) {
                val api = backend()
                restoreConfiguration(api)
                api.verify(viperProgram)
            }
            when (result) {
                is viper.silver.verifier.Failure -> {
                    val errors = CollectionConverters.asJava(result.errors())
                    val rejections = errors.filter { it !is viper.silver.verifier.VerificationError }
                    if (rejections.isNotEmpty()) throw InvalidViperProgramException(rejections.map { it.readableMessage() })
                    errors.forEach { onFailure(VerificationError(it as viper.silver.verifier.VerificationError)) }
                    false
                }
                viper.silver.verifier.`Success$`.`MODULE$` -> true
                else -> error("Unexpected Silicon verification result: $result")
            }
        }
        if (cache == null) verify()
        else cache.verify(viperProgram.toString(), cacheVersion!!, cacheOptions, verify)
    }

    private fun restoreConfiguration(api: viper.silicon.SiliconFrontendAPI) {
        viper.silicon.verifier.`Verifier$`.`MODULE$`.`config_$eq`((api.verifier() as viper.silicon.Silicon).config())
    }

    override fun close() {
        configurationGate.withConfiguration(args) {
            siliconApi?.let { api ->
                restoreConfiguration(api)
                api.stop()
            }
            siliconApi = null
        }
    }
}
