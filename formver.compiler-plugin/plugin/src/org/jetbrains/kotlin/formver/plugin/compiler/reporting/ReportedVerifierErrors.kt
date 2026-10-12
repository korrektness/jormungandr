package org.jetbrains.kotlin.formver.plugin.compiler.reporting

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import java.util.concurrent.ConcurrentHashMap

/** Tracks repeated reports of a clause at its Kotlin origin, independently of presentation. */
class ReportedVerifierErrors {
    private data class Key(
        val kind: FailureKind,
        val source: KtSourceElement?,
        val obligation: ObligationIdentity,
        val errorId: String?,
        val reasonId: String?,
    )

    private val reported = ConcurrentHashMap.newKeySet<Key>()

    fun add(failure: Failure): Boolean = reported.add(
        Key(failure.kind, failure.primary, failure.obligation, failure.backend?.errorId, failure.backend?.reasonId),
    )

    fun add(error: VerifierError, source: KtSourceElement?): Boolean = add(
        when (error) {
            is VerificationError -> error.toFailure(source)
        },
    )
}
