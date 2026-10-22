package org.jetbrains.kotlin.formver.viper

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.spi.FilterReply
import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import org.slf4j.Marker
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

/** Silicon did not finish verifying a program within [seconds]. */
class VerificationTimeoutException(val seconds: Int) :
    Exception("Verification did not finish within $seconds seconds")

/**
 * Marks the threads that serve one Silicon backend. A thread inherits the mark of the thread that creates it, so the
 * verifier and worker threads that Silicon starts during [run] carry this mark. Once [stop] is called, all logging from
 * threads with this mark is dropped.
 */
class SiliconThreads {
    @Volatile
    private var stopped = false

    /** Runs [action] on the current thread under this mark. */
    fun <T> run(action: () -> T): T {
        val previous = current.get()
        current.set(this)
        try {
            return action()
        } finally {
            current.set(previous)
        }
    }

    fun stop() {
        stopped = true
    }

    companion object {
        private val current = InheritableThreadLocal<SiliconThreads?>()

        private val filter = object : TurboFilter() {
            override fun decide(
                marker: Marker?, logger: Logger?, level: Level?, format: String?, params: Array<out Any?>?, t: Throwable?,
            ): FilterReply = if (current.get()?.stopped == true) FilterReply.DENY else FilterReply.NEUTRAL
        }

        init {
            // Casting LoggerFactory.getILoggerFactory() to LoggerContext, here and in the ViperStdOutLogger that
            // SiliconFrontendAPI's constructor calls, fails while SLF4J's first-time initialization is in progress in
            // another thread: it then returns a SubstituteLoggerFactory. Calling getLogger here forces logback to fully
            // initialize exactly once, under the JVM's class-initialization guarantee. SiliconFrontend creates its
            // SiliconThreads before it constructs a SiliconFrontendAPI.
            //
            // This issue is known to the Silicon team: https://github.com/viperproject/silicon/issues/968
            org.slf4j.LoggerFactory.getLogger(SiliconThreads::class.java)
            filter.start()
            (org.slf4j.LoggerFactory.getILoggerFactory() as LoggerContext).addTurboFilter(filter)
        }
    }
}

/**
 * Passes Viper programs for verification to the Silicon backend via [viper.silicon.SiliconFrontendAPI], using the Z3
 * binary at [z3Exe], as found by [SiliconFrontend.locateZ3].
 * Use [SiliconFrontend.verify] to consistency-check and verify a given program.
 * Each [verify] call is bounded by [timeoutSeconds]; zero leaves it unbounded.
 */
class SiliconFrontend(
    z3Exe: String,
    commandLineArgs: List<String> = emptyList(),
    private val timeoutSeconds: Int = 0,
    cacheDirectory: Path? = null,
    pluginOptions: List<String> = emptyList(),
    counterexamples: Boolean = false,
    cacheReportDirectory: Path? = null,
    cacheRefresh: Boolean = false,
    cacheColdSample: String? = null,
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
        // Silicon stops checking a method once it has this many errors, and it counts failures it does not report,
        // such as a trigger's precondition. RefutePlugin discards the failure that a refute expects, so with any limit
        // the statements after a refute may go unchecked without an error.
        add("--numberOfErrorsToReport")
        add("0")
        if (timeoutSeconds > 0) {
            add("--timeout")
            add(timeoutSeconds.toString())
        }
    }
    private val cache = cacheDirectory?.let {
        VerificationResultCache(it, cacheReportDirectory, refresh = cacheRefresh, coldSample = cacheColdSample)
    }
    private val cacheOptions = args.chunked(2).filter { it[0] != "--z3Exe" }.flatten() + pluginOptions + listOf(
        System.getProperty("java.runtime.version"), System.getProperty("os.name"), System.getProperty("os.arch"),
    )
    private val cacheVersion = cacheDirectory?.let {
        // Custom plugins and obligation filters can change semantics beyond the printed program.
        require(commandLineArgs.chunked(2).all { pair ->
            pair.size == 2 && pair[0] in cacheableOptions && pair[1].toLongOrNull() != null
        }) { "Verification caching requires supported numeric Silicon options." }
        VerificationResultCache.fingerprint(backendDigests + VerificationResultCache.currentFileDigest(Path.of(z3Exe)))
    }
    private var siliconApi: viper.silicon.SiliconFrontendAPI? = null
    private var siliconThreads = SiliconThreads()

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

        /** Loaded verifier classes cannot change within a JVM, so their artifacts are hashed once. */
        private val backendDigests: List<String> by lazy {
            listOf(
                artifactDigest(viper.silicon.SiliconFrontendAPI::class.java),
                artifactDigest(viper.silver.ast.Program::class.java),
                artifactDigest(SiliconFrontend::class.java),
            )
        }

        private fun artifactDigest(type: Class<*>): String {
            val path = Path.of(type.protectionDomain.codeSource.location.toURI())
            return VerificationResultCache.artifactDigest(path)
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
    }

    /**
     * Consistency-checks and verifies [viperProgram], calling [onFailure] for each verification error found.
     * Throws [VerificationTimeoutException] when Silicon runs out of time, and [InvalidViperProgramException] when
     * Viper reports anything else that is not a verification error.
     */
    fun verify(viperProgram: viper.silver.ast.Program, onFailure: (VerifierError) -> Unit) =
        verify(viperProgram, {}, onFailure)

    /** Reports whether this invocation uses the outcome cache: off, miss or hit. */
    fun verify(
        viperProgram: viper.silver.ast.Program,
        onCacheStatus: (String) -> Unit,
        onFailure: (VerifierError) -> Unit,
    ) {
        // The AST API starts in the state after the consistency check, so its ConsistencyCheck phase does nothing.
        // Silicon does not check the AST itself either: an undeclared variable makes it abort with an exception,
        // which the result reports as an error that is not a verification error.
        checkConsistency(viperProgram)
        // SilFrontend.verification invokes the default plugins' beforeVerify and mapVerificationResult hooks,
        // including RefutePlugin. Its isolated assertion branch leaves the continuation available for further checks.
        var executed = false
        val verify = {
            executed = true
            val threads = siliconThreads
            val api = siliconApi ?: configurationGate.withExclusiveConfiguration(args) { threads.run { backend() } }
            val result = configurationGate.withConfiguration(args) {
                threads.run {
                    restoreConfiguration(api)
                    api.verify(viperProgram)
                }
            }
            if (result.timedOut()) {
                configurationGate.withExclusiveConfiguration(args) {
                    // Silicon returns on a timeout while its verifier threads may still wait on Z3. Stopping the
                    // backend kills Z3, which ends those threads, and the next program starts a fresh backend.
                    // The threads then fail on the lost prover and log it; the timeout already accounts for that
                    // failure, so their logging is dropped.
                    siliconApi = null
                    siliconThreads = SiliconThreads()
                    threads.stop()
                    restoreConfiguration(api)
                    api.stop()
                }
                throw VerificationTimeoutException(timeoutSeconds)
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
        var completed = false
        try {
            if (cache == null) verify()
            else cache.verify(viperProgram.toString(), cacheVersion!!, cacheOptions, verify)
            completed = true
        } finally {
            onCacheStatus(when {
                cache == null -> "off"
                executed -> "miss"
                completed -> "hit"
                else -> "unavailable"
            })
        }
    }

    private fun viper.silver.verifier.VerificationResult.timedOut(): Boolean =
        this is viper.silver.verifier.Failure &&
            CollectionConverters.asJava(errors()).any { it is viper.silver.verifier.TimeoutOccurred }

    private fun restoreConfiguration(api: viper.silicon.SiliconFrontendAPI) {
        viper.silicon.verifier.`Verifier$`.`MODULE$`.`config_$eq`((api.verifier() as viper.silicon.Silicon).config())
    }

    override fun close() {
        configurationGate.withExclusiveConfiguration(args) {
            siliconApi?.let { api ->
                restoreConfiguration(api)
                api.stop()
            }
            siliconApi = null
        }
    }
}
