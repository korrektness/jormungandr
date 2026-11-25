/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.formver.viper.SiliconFrontend
import org.jetbrains.kotlin.formver.viper.VerifierUnavailableException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Finds the Z3 binary once per FIR session with [locate], so that a missing verifier is reported once per module rather
 * than at every function to verify.
 */
class VerifierLocator(private val locate: () -> String = SiliconFrontend::locateZ3) {
    private val z3Exe: Result<String> by lazy {
        try {
            Result.success(locate())
        } catch (e: VerifierUnavailableException) {
            Result.failure(e)
        }
    }

    private val unavailabilityReported = AtomicBoolean(false)

    /** Returns the solver path, or `null` and reports unavailability once per locator. */
    fun z3Exe(reportUnavailable: (String) -> Unit): String? = z3Exe({}, reportUnavailable)

    /** Also passes the cached failure to [recordUnavailable] on every unsuccessful lookup. */
    internal fun z3Exe(recordUnavailable: (String) -> Unit, reportUnavailable: (String) -> Unit): String? =
        z3Exe.getOrElse { e ->
            val reason = e.message.orEmpty()
            if (unavailabilityReported.compareAndSet(false, true)) reportUnavailable(reason)
            recordUnavailable(reason)
            null
        }
}
