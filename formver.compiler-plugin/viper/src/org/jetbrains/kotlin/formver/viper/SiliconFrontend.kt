package org.jetbrains.kotlin.formver.viper

import org.jetbrains.kotlin.formver.viper.errors.GenericConsistencyError
import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import viper.silver.reporter.StdIOReporter
import java.io.Closeable
import java.io.File

/**
 * Passes Viper programs for verification to the Silicon backend via [viper.silicon.SiliconFrontendAPI].
 * Use [SiliconFrontend.verify] to consistency-check and verify a given program.
 */
class SiliconFrontend(commandLineArgs: List<String>) : Closeable {
    private val siliconApi: viper.silicon.SiliconFrontendAPI

    init {
        val args = buildList {
            addAll(commandLineArgs)
            add("--z3Exe")
            add(resolveZ3Exe(System.getenv("Z3_EXE"), System.getenv("PATH")))
            System.getenv("SILICON_PARALLEL_VERIFIERS")?.let {
                add("--numberOfParallelVerifiers")
                add(it)
            }
        }
        siliconApi = viper.silicon.SiliconFrontendAPI(StdIOReporter("stdout_reporter", true))
        siliconApi.initialize(args.toScalaSeq())
    }

    companion object {
        /**
         * Returns the absolute path of the Z3 binary. Silicon takes `Z3_EXE` as a literal file path, so a bare
         * command name (or the default `z3` when `Z3_EXE` is unset) is looked up on [path] here.
         */
        internal fun resolveZ3Exe(z3Exe: String?, path: String?): String {
            val name = z3Exe?.takeIf { it.isNotEmpty() } ?: defaultZ3Name()
            if (File(name).isAbsolute) {
                require(File(name).isFile) { "Z3_EXE is set to '$name', which is not a file." }
                return name
            }
            require(File(name).parent == null) { "Z3_EXE is the relative path '$name'." }
            return path.orEmpty().split(File.pathSeparator)
                .filter { it.isNotEmpty() }
                .map { File(it, name) }
                .firstOrNull { it.isFile && it.canExecute() }
                ?.absolutePath
                ?: throw IllegalArgumentException("Cannot find Z3 from Z3_EXE: '$name' is not on PATH.")
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

    /** Consistency-checks and verifies [viperProgram], calling [onFailure] for each error found. */
    fun verify(viperProgram: viper.silver.ast.Program, onFailure: (VerifierError) -> Unit) {
        val result = siliconApi.verify(viperProgram)
        if (result is viper.silver.verifier.Failure) {
            for (error in result.errors()) {
                when (error) {
                    is viper.silver.verifier.VerificationError ->
                        onFailure(VerificationError(error))
                    is viper.silver.verifier.ConsistencyError ->
                        onFailure(GenericConsistencyError(error))
                }
            }
        }
    }

    override fun close() {
        siliconApi.stop()
    }
}
