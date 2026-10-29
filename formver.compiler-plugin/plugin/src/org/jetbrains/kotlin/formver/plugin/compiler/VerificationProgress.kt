/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.formver.common.Failure
import org.jetbrains.kotlin.formver.common.FailureKind
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.viper.VerificationTimeoutException
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

/**
 * Logs the start and duration of each verification to [messages] at INFO.
 * See docs/configuration.md#diagnostics-and-progress for visibility.
 */
class VerificationProgress(
    private val messages: MessageCollector,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    /**
     * Runs [verify], the verification of the declaration called [name]. Returns the failure to report at [source] when
     * the verifier ran out of time, so that the caller can go on with the next declaration.
     */
    fun run(name: String, source: KtSourceElement?, elapsedNanos: () -> Long? = { null }, verify: () -> Unit): Failure? {
        messages.report(CompilerMessageSeverity.INFO, "Verifying $name")
        val start = nanoTime()
        try {
            verify()
            return null
        } catch (timeout: VerificationTimeoutException) {
            return Failure(FailureKind.VERIFICATION_TIMEOUT, source, KotlinCondition.Timeout(name, timeout.seconds))
        } finally {
            val millis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis((elapsedNanos() ?: (nanoTime() - start)))
            messages.report(CompilerMessageSeverity.INFO, "Finished verifying $name in $millis ms")
        }
    }
}

/**
 * The name that progress messages and timeouts give a verified declaration: its fully qualified name, or the file name
 * of a specification file whose vacuity probes are verified.
 */
val FirDeclaration.verificationLabel: String
    get() = when (this) {
        is FirSimpleFunction -> qualified(symbol.callableId, name)
        is FirPropertyAccessor -> qualified(propertySymbol.callableId, propertySymbol.name) + if (isGetter) ".get" else ".set"
        is FirRegularClass -> symbol.classId.asSingleFqName().asString()
        is FirProperty -> qualified(symbol.callableId, name)
        is FirFile -> "the specifications in $name"
        else -> error("Unexpected verification owner: ${this::class.simpleName}")
    }

/** A local declaration has no callable id, so it goes by its own [name]. */
private fun qualified(id: CallableId?, name: Name): String = id?.asSingleFqName()?.asString() ?: name.asString()
