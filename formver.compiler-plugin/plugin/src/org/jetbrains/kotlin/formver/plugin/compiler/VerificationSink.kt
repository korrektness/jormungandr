/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.formver.common.PluginConfiguration
import org.jetbrains.kotlin.formver.viper.SiliconFrontend
import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import java.nio.file.Path

/** A complete independent program and the backend options needed to verify it. */
internal data class VerificationRequest(
    val program: viper.silver.ast.Program,
    val z3Exe: String,
    val timeoutSeconds: Int,
    val cacheDirectory: Path?,
    val pluginOptions: List<String>,
    val counterexamples: Boolean,
)

internal fun PluginConfiguration.verificationRequest(
    program: viper.silver.ast.Program, z3Exe: String, counterexamples: Boolean = this.counterexamples,
) = VerificationRequest(
    program, z3Exe, verificationTimeoutSeconds, verificationCacheDirectory?.let { Path.of(it) },
    verificationCacheOptions.toList(), counterexamples,
)

internal data class VerificationResult(val errors: List<VerifierError>, val failure: Throwable? = null, val elapsedNanos: Long? = null) {
    fun throwFailure() {
        failure?.let { throw it }
    }
}

/**
 * Submit in producer order. Replay exactly once, in that order, on the checker thread while its
 * captured contexts remain valid. Workers receive only requests and return results, never replay
 * closures. At replay, the supplied function returns the result; a deferred sink supplies the
 * backend elapsed time in [VerificationResult.elapsedNanos]. The immediate sink executes the
 * backend in that function, inside the checker's progress scope.
 */
internal fun interface VerificationSink {
    fun submit(request: VerificationRequest, replay: (() -> VerificationResult) -> Unit)
}

internal object ImmediateVerificationSink : VerificationSink {
    override fun submit(request: VerificationRequest, replay: (() -> VerificationResult) -> Unit) {
        replay {
            val errors = mutableListOf<VerifierError>()
            val failure = try {
                SiliconFrontend(
                    request.z3Exe, timeoutSeconds = request.timeoutSeconds, cacheDirectory = request.cacheDirectory,
                    pluginOptions = request.pluginOptions, counterexamples = request.counterexamples,
                ).use { it.verify(request.program) { error -> errors += error } }
                null
            } catch (error: Exception) {
                error
            }
            VerificationResult(errors.toList(), failure)
        }
    }
}
