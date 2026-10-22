package org.jetbrains.kotlin.formver.plugin.services

import java.nio.file.Files
import java.nio.file.Path

/** Opt-in timing of the harness's declaration verification groups, outside goldens. */
class DeclarationVerificationReport(
    private val directory: Path,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    fun run(
        file: String,
        declaration: String,
        offset: Int?,
        timeoutSeconds: Int,
        verify: (onCacheStatus: (String) -> Unit) -> String,
    ) {
        val statuses = mutableListOf<String>()
        var outcome = "error"
        val start = nanoTime()
        try {
            outcome = verify { statuses.add(it) }
        } finally {
            val elapsed = (nanoTime() - start) / NANOS_PER_SECOND
            Files.createDirectories(directory)
            // One file per attempt keeps concurrent test JVMs and verifier workers independent.
            Files.writeString(
                Files.createTempFile(directory, "declaration-", ".tsv"),
                listOf("file", "declaration", "offset", "outcome", "seconds", "timeout_seconds", "cache").joinToString("\t") + "\n" +
                    listOf(file, declaration, offset?.toString().orEmpty(), outcome, elapsed.toString(),
                        timeoutSeconds.toString(), statuses.joinToString(",").ifEmpty { "unavailable" })
                        .joinToString("\t") { it.replace("\t", " ").replace("\n", " ").replace("\r", " ") } + "\n",
            )
        }
    }

    companion object {
        private const val NANOS_PER_SECOND = 1e9

        fun fromEnvironment(): DeclarationVerificationReport? =
            System.getenv("SNAKT_VERIFICATION_TIMINGS")?.let { DeclarationVerificationReport(Path.of(it)) }
    }
}
