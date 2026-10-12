package org.jetbrains.kotlin.formver.viper

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class CorruptVerificationCacheException(path: Path) :
    Exception("Corrupt verification cache entry: $path")

/** Stores only successful verification outcomes; failures require current source metadata. */
class VerificationResultCache(private val directory: Path) {
    fun verify(program: String, version: String, options: List<String>, verify: () -> Boolean): Boolean {
        val key = fingerprint(listOf("formver-verification-success-v1", program, version) + options)
        val entry = directory.resolve("$key.success")
        val expected = "formver-verification-success-v1\n$key\nsuccess\n".toByteArray(Charsets.UTF_8)
        val stored = try {
            Files.readAllBytes(entry)
        } catch (_: NoSuchFileException) {
            null
        }
        if (stored != null) {
            if (!stored.contentEquals(expected)) throw CorruptVerificationCacheException(entry)
            return true
        }
        val success = verify()
        if (success) {
            Files.createDirectories(directory)
            val temporary = Files.createTempFile(directory, ".verification-", ".tmp")
            try {
                Files.write(temporary, expected)
                Files.move(temporary, entry, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
        return success
    }

    companion object {
        fun fingerprint(parts: List<String>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            parts.forEach { part ->
                val bytes = part.toByteArray(Charsets.UTF_8)
                digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
                digest.update(bytes)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun fileDigest(path: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
