package org.jetbrains.kotlin.formver.viper

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/** Stores only successful verification outcomes; failures require current source metadata. */
class VerificationResultCache(
    private val directory: Path,
    private val reportDirectory: Path? = null,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun verify(program: String, version: String, options: List<String>, verify: () -> Boolean): Boolean {
        val format = "formver-verification-success-v2"
        val key = fingerprint(listOf(format, program, version) + options)
        val shard = directory.resolve(key.take(2))
        val entry = shard.resolve("$key.success")
        val expected = "$format\n$key\nsuccess\n".toByteArray(Charsets.UTF_8)
        val stored = try {
            Files.readAllBytes(entry)
        } catch (_: NoSuchFileException) {
            null
        }
        if (stored != null) {
            if (stored.contentEquals(expected)) {
                try {
                    if (Duration.between(Files.getLastModifiedTime(entry).toInstant(), clock.instant()).toDays() >= 1) {
                        Files.setLastModifiedTime(entry, FileTime.from(clock.instant()))
                    }
                } catch (_: NoSuchFileException) {
                    // Hit maintenance is best-effort: pruning can remove the record after it is read.
                }
                report(key, "hit")
                return true
            }
            // The store is best-effort: damaged records require fresh verification.
            Files.deleteIfExists(entry)
            report(key, "corrupt")
        }
        val start = System.nanoTime()
        val success = try {
            verify()
        } finally {
            report(key, "miss", (System.nanoTime() - start) / NANOS_PER_SECOND)
        }
        if (success) {
            Files.createDirectories(shard)
            val temporary = Files.createTempFile(shard, ".verification-", ".tmp")
            try {
                Files.write(temporary, expected)
                Files.move(temporary, entry, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                report(key, "stored")
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
        if (!success) report(key, "unstorable")
        return success
    }

    private fun report(key: String, event: String, seconds: Double = 0.0) {
        reportDirectory?.let {
            Files.createDirectories(it)
            Files.writeString(Files.createTempFile(it, "program-", ".tsv"), "${key.take(REPORT_KEY_LENGTH)}\t$event\t$seconds\n")
        }
    }

    companion object {
        private const val NANOS_PER_SECOND = 1e9
        private const val REPORT_KEY_LENGTH = 16

        fun fingerprint(parts: List<String>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            parts.forEach { part ->
                val bytes = part.toByteArray(Charsets.UTF_8)
                digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
                digest.update(bytes)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private data class FileVersion(val path: Path, val size: Long, val modified: FileTime)

        private val currentDigests = ConcurrentHashMap<FileVersion, String>()

        /**
         * The [fileDigest] of [path], computed once for each size and modification time the file has had in this JVM.
         */
        fun currentFileDigest(path: Path): String {
            val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
            val version = FileVersion(path.toAbsolutePath(), attributes.size(), attributes.lastModifiedTime())
            return currentDigests.computeIfAbsent(version) { fileDigest(path) }
        }

        /** Covers packaged modules and sorted class-directory contents, including relative class names. */
        fun artifactDigest(path: Path): String {
            if (Files.isRegularFile(path)) return fileDigest(path)
            require(Files.isDirectory(path)) { "Verification module artifact is absent: $path" }
            val parts = Files.walk(path).use { files ->
                files.filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }
                    .sorted().map { listOf(path.relativize(it).toString(), fileDigest(it)) }.toList().flatten()
            }
            require(parts.isNotEmpty()) { "Verification module contains no classes: $path" }
            return fingerprint(parts)
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
