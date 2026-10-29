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

    fun flush() {
        try {
            // The worker input contains no replay closures or checker state.
            val requests = jobs.map { it.request }
            val factory = backendFactory
            val results = FileBatchRunner.run(requests, workers, { WorkerBackends(factory) }, WorkerBackends::verify)
            var failure: Throwable? = null
            jobs.zip(results).forEach { (job, result) ->
                try {
                    job.replay { result }
                } catch (error: InterruptedException) {
                    throw error
                } catch (error: Exception) {
                    if (failure == null) failure = error else failure.addSuppressed(error)
                } catch (error: NotImplementedError) {
                    if (failure == null) failure = error else failure.addSuppressed(error)
                }
            }
            failure?.let { throw it }
        } finally {
            jobs.clear()
        }
    }
}

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
        if (current != null && current?.first != key) {
            val previous = current
            current = null
            previous?.second?.close()
        }
        val failure = try {
            if (current == null) current = key to factory(request)
            checkNotNull(current).second.verify(request.program) { errors += it }
            null
        } catch (error: InterruptedException) {
            throw error
        } catch (error: Exception) {
            error
        } catch (error: NotImplementedError) {
            error
        }
        return VerificationResult(errors.toList(), failure, System.nanoTime() - started)
    }

    override fun close() {
        val previous = current
        current = null
        previous?.second?.close()
    }
}
