package org.jetbrains.kotlin.formver.plugin.compiler.reporting

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.common.*
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
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

    fun add(error: VerifierError, source: KtSourceElement?): Boolean = when (error) {
        is VerificationError -> addIfReportable(error) { error.toFailure(source) }
    }

    /**
     * Records the interpreted [failure] and says whether to report it. A failure at a [SourceRole.ThrowsAssumption]
     * is not reported: the call it belongs to checks and reports the same precondition in the same state.
     */
    fun add(error: VerificationError, failure: Failure): Boolean = addIfReportable(error) { failure }

    private inline fun addIfReportable(error: VerificationError, failure: () -> Failure): Boolean =
        !error.atThrowsAssumption() && add(failure())
}

private fun VerificationError.atThrowsAssumption(): Boolean =
    locationNode.getInfoOrNull<SourceRole>() == SourceRole.ThrowsAssumption ||
        unverifiableProposition.getInfoOrNull<SourceRole>() == SourceRole.ThrowsAssumption
