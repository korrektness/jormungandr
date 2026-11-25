/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.formver.viper.SiliconFrontend
import org.jetbrains.kotlin.formver.viper.errors.VerifierError

/** File-owned batch. Each worker reuses its own backends; replay stays on the checker thread. */
internal class FileVerificationSink(
    private val workers: Int,
    private val backendFactory: (VerificationRequest) -> FileVerificationBackend = ::siliconBackend,
) : VerificationSink {
    private data class Job(val request: VerificationRequest, val replay: (() -> VerificationResult) -> Unit)
    private val jobs = mutableListOf<Job>()

    override fun submit(request: VerificationRequest, replay: (() -> VerificationResult) -> Unit) {
        jobs += Job(request, replay)
    }

    /**
     * Replay all available results before propagating failure. In order of precedence: checker
     * interruption captured by the batch runner, terminal job failures, worker lifecycle failures,
     * then replay failures. Interruption during replay counts as a replay failure. If interrupted,
     * the checker restores its interrupt flag after replay.
     */
    fun flush() {
        var restoreInterrupt = false
        try {
            // The worker input contains no replay closures or checker state.
            val requests = jobs.map { it.request }
            val factory = backendFactory
            val batch = FileBatchRunner.run(requests, workers, { WorkerBackends(factory) },
                VerificationResult::terminalFailure, WorkerBackends::verify)
            restoreInterrupt = batch.interruption != null
            var failure: Throwable? = batch.interruption
            fun collect(error: Throwable) {
                val primary = failure
                if (primary == null) failure = error
                else if (primary !== error && primary.suppressed.none { it === error }) primary.addSuppressed(error)
            }
            batch.results.forEach { it?.terminalFailure()?.let(::collect) }
            batch.failures.forEach(::collect)
            for ((index, result) in batch.results.withIndex()) {
                if (result == null) continue
                var replayInterrupted = false
                try {
                    jobs[index].replay { result }
                } catch (error: Throwable) {
                    replayInterrupted = error is InterruptedException && error !== result.failure
                    collect(error)
                } finally {
                    val flagged = Thread.interrupted()
                    if (flagged && !replayInterrupted) collect(InterruptedException("File verification replay interrupted"))
                    restoreInterrupt = restoreInterrupt || replayInterrupted || flagged
                }
            }
            failure?.let { throw it }
            check(batch.results.none { it == null }) { "File verification stopped without a batch failure" }
        } finally {
            jobs.clear()
            if (restoreInterrupt) Thread.currentThread().interrupt()
        }
    }
}

private fun Throwable.isTerminalVerificationFailure() = this is InterruptedException || (this !is Exception && this !is NotImplementedError)

private fun VerificationResult.terminalFailure() = failure?.takeIf { it.isTerminalVerificationFailure() }

internal interface FileVerificationBackend : AutoCloseable {
    fun verify(program: viper.silver.ast.Program, onError: (VerifierError) -> Unit)
}

private fun siliconBackend(request: VerificationRequest): FileVerificationBackend {
    val frontend = SiliconFrontend(request.z3Exe, timeoutSeconds = request.timeoutSeconds,
        cacheDirectory = request.cacheDirectory, pluginOptions = request.pluginOptions, counterexamples = request.counterexamples)
    return object : FileVerificationBackend {
        override fun verify(program: viper.silver.ast.Program, onError: (VerifierError) -> Unit) = frontend.verify(program, onError)
        override fun close() = frontend.close()
    }
}

private class WorkerBackends(private val factory: (VerificationRequest) -> FileVerificationBackend) : AutoCloseable {
    private data class Options(
        val z3: String, val timeout: Int, val cache: java.nio.file.Path?,
        val plugins: List<String>, val counterexamples: Boolean,
    )
    private var current: Pair<Options, FileVerificationBackend>? = null

    fun verify(request: VerificationRequest): VerificationResult {
        val key = Options(request.z3Exe, request.timeoutSeconds, request.cacheDirectory, request.pluginOptions, request.counterexamples)
        val errors = mutableListOf<VerifierError>()
        val started = System.nanoTime()
        if (current != null && current?.first != key) close()
        val failure = try {
            if (current == null) current = key to factory(request)
            checkNotNull(current).second.verify(request.program) { errors += it }
            null
        } catch (error: Throwable) {
            error
        }
        val outcome = if (Thread.currentThread().isInterrupted && failure?.isTerminalVerificationFailure() != true) {
            InterruptedException("File verification worker interrupted").apply { failure?.let(::addSuppressed) }
        } else failure
        return VerificationResult(errors.toList(), outcome, System.nanoTime() - started)
    }

    override fun close() {
        val previous = current
        current = null
        previous?.second?.close()
    }
}
