/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler.reporting

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import java.util.concurrent.ConcurrentHashMap

/**
 * The verifier errors already reported in one FIR session.
 *
 * Each function is verified in a program of its own, and a pure function is embedded with its body in the program of
 * every function that calls it, so Silicon finds an error in that body once per caller. The errors are identical, down
 * to their source, so recording the errors reported lets each be reported once.
 */
class ReportedVerifierErrors {
    private data class Key(val id: String, val source: KtSourceElement?, val msg: String)

    private val reported = ConcurrentHashMap.newKeySet<Key>()

    /** Records [err], reported at [source], and returns whether it had not been reported before. */
    fun add(err: VerifierError, source: KtSourceElement?): Boolean = reported.add(Key(err.id, source, err.msg))
}
