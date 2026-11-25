package org.jetbrains.kotlin.formver.common.source

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

private val compilationIdPattern = Regex("(?::[A-Za-z0-9_-][A-Za-z0-9_.-]*)*:compileKotlin")

internal fun requireCensusCompilationId(compilation: String) {
    require(compilation.matches(compilationIdPattern)) { "Invalid census compilation ID: $compilation" }
}

fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHexString()

/** Decodes exact input bytes to the text used for half-open UTF-16 syntax spans. */
fun ByteArray.normalizedKotlinText(path: Path): String {
    val text = try {
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(this)).toString()
    } catch (error: CharacterCodingException) {
        throw IllegalArgumentException("Invalid UTF-8 in Kotlin input $path", error)
    }
    return text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
}

/** The file prefix of a census key, independent of where the checkout is mounted. */
data class SourceIdentity(val compilation: String, val root: String, val path: String, val digest: String) {
    init {
        requireCensusCompilationId(compilation)
        val relative = Path.of(path)
        require(root == "checkout" && path.isNotEmpty() &&
            path.none { it in "\t\n\r" } && relative.root == null && !relative.startsWith("..") &&
            relative.normalize().censusPath() == path && digest.matches(sha256Pattern)) {
            "Invalid source identity: $compilation, $root, $path, $digest"
        }
    }

    fun columns(): String = "$compilation\t$root\t$path\t$digest"

    fun regionId(region: RegionId): String = stableId("census-region-v1", region.reference())

    fun stableId(domain: String, anchor: String): String = "$domain\t${columns()}\t$anchor".toByteArray().sha256()

    companion object {
        private val sha256Pattern = Regex("[0-9a-f]{64}")

        /** Reads a checkout input and checks that its bytes normalize to the compiler's syntax text. */
        fun fromParsedFile(compilation: String, checkout: Path, path: Path, parsedText: String): SourceIdentity {
            val actual = path.toRealPath()
            require(actual.startsWith(checkout)) { "Input $actual is outside checkout $checkout" }
            val bytes = Files.readAllBytes(actual)
            require(bytes.normalizedKotlinText(actual) == parsedText) { "Compiler PSI text differs from normalized input: $actual" }
            return SourceIdentity(compilation, "checkout", checkout.relativize(actual).censusPath(), bytes.sha256())
        }
    }
}

private fun Path.censusPath(): String = joinToString("/")
