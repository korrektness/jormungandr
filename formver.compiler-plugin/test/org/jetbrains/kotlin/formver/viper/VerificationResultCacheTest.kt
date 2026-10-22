package org.jetbrains.kotlin.formver.viper

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VerificationResultCacheTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `hit returns stored success without invoking verifier`() {
        val cache = VerificationResultCache(directory)
        assertTrue(cache.verify("program", "silicon-1", listOf("--timeout", "10")) { true })
        assertTrue(VerificationResultCache(directory).verify("program", "silicon-1", listOf("--timeout", "10")) {
            error("Verifier must not run on a hit")
        })
    }

    @Test
    fun `program version and options changes miss`() {
        val cache = VerificationResultCache(directory)
        var calls = 0
        val verify = { calls++; true }
        cache.verify("program", "silicon-1", listOf("a", "bc"), verify)
        cache.verify("changed program", "silicon-1", listOf("a", "bc"), verify)
        cache.verify("program", "silicon-2", listOf("a", "bc"), verify)
        cache.verify("program", "silicon-1", listOf("ab", "c"), verify)
        cache.verify("program", "silicon-1", listOf("bc", "a"), verify)
        assertEquals(5, calls)
    }

    @Test
    fun `failed and exceptional verification is never stored`() {
        val cache = VerificationResultCache(directory)
        repeat(2) { assertFalse(cache.verify("program", "version", emptyList()) { false }) }
        assertFailsWith<VerificationTimeoutException> {
            cache.verify("program", "version", emptyList()) { throw VerificationTimeoutException(1) }
        }
        assertTrue(entries().isEmpty())
        assertTrue(cache.verify("program", "version", emptyList()) { true })
    }

    @Test
    fun `corrupt and misplaced entries reverify and report`() {
        val reports = directory.resolve("reports")
        val cache = VerificationResultCache(directory, reports)
        cache.verify("program", "version", emptyList()) { true }
        val entry = entries().single()
        assertEquals(entry.fileName.toString().take(2), entry.parent.fileName.toString())
        val valid = Files.readAllBytes(entry)
        Files.write(entry, byteArrayOf(0, 1, 2))
        var calls = 0
        assertTrue(cache.verify("program", "version", emptyList()) { calls++; true })
        assertEquals(1, calls)
        Files.write(entry, valid)
        cache.verify("other program", "version", emptyList()) { true }
        val other = entries().single { it != entry }
        Files.write(other, valid)
        assertFalse(cache.verify("other program", "version", emptyList()) { calls++; false })
        assertEquals(2, calls)
        assertFalse(Files.exists(other))
        Files.list(reports).use { files ->
            assertEquals(2L, files.filter { Files.readString(it).contains("\tcorrupt\t") }.count())
        }
    }

    @Test
    fun `concurrent writers publish complete entries`() {
        OrderedVerificationExecutor(2).use { executor ->
            val barrier = java.util.concurrent.CyclicBarrier(2)
            assertEquals(listOf(true, true), executor.map(listOf(1, 2)) {
                VerificationResultCache(directory).verify("program", "version", emptyList()) {
                    barrier.await()
                    true
                }
            })
        }
        assertTrue(VerificationResultCache(directory).verify("program", "version", emptyList()) { error("Expected hit") })
        assertEquals(1, entries().size)
    }

    private fun entries(): List<Path> = Files.walk(directory).use { files ->
        files.filter { it.toString().endsWith(".success") }.toList()
    }

    @Test
    fun `hit refreshes old entry but leaves recent entry untouched`() {
        val now = Instant.parse("2026-01-03T00:00:00Z")
        val cache = VerificationResultCache(directory, clock = Clock.fixed(now, ZoneOffset.UTC))
        cache.verify("program", "version", emptyList()) { true }
        val entry = entries().single()
        val recent = FileTime.from(now.minusSeconds(60))
        Files.setLastModifiedTime(entry, recent)
        cache.verify("program", "version", emptyList()) { error("Expected hit") }
        assertEquals(recent, Files.getLastModifiedTime(entry))
        Files.setLastModifiedTime(entry, FileTime.from(now.minusSeconds(2 * 86400)))
        cache.verify("program", "version", emptyList()) { error("Expected hit") }
        assertEquals(FileTime.from(now), Files.getLastModifiedTime(entry))
        Files.setLastModifiedTime(entry, FileTime.from(now.minusSeconds(2 * 86400)))
        val pruningClock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId): Clock = this
            override fun instant(): Instant {
                Files.deleteIfExists(entry)
                return now
            }
        }
        assertTrue(VerificationResultCache(directory, clock = pruningClock).verify("program", "version", emptyList()) {
            error("Pruning after read must not discard the hit")
        })
        assertFalse(Files.exists(entry))
    }

    @Test
    fun `module content and relative class names invalidate cached success`() {
        val classes = directory.resolve("classes")
        Files.createDirectories(classes)
        val type = classes.resolve("Verifier.class")
        Files.write(type, byteArrayOf(1))
        val cache = VerificationResultCache(directory)
        var calls = 0
        fun verify() = cache.verify("program", VerificationResultCache.artifactDigest(classes), emptyList()) { calls++; true }
        verify()
        verify()
        assertEquals(1, calls)
        Files.write(type, byteArrayOf(2))
        verify()
        Files.move(type, classes.resolve("Renamed.class"))
        verify()
        assertEquals(3, calls)
    }

    @Test
    fun `a rewritten file gets the digest of its new content`() {
        val file = directory.resolve("z3")
        Files.write(file, byteArrayOf(1))
        val first = VerificationResultCache.currentFileDigest(file)
        assertEquals(first, VerificationResultCache.currentFileDigest(file))
        Files.write(file, byteArrayOf(1, 2))
        assertEquals(VerificationResultCache.fileDigest(file), VerificationResultCache.currentFileDigest(file))
        assertNotEquals(first, VerificationResultCache.currentFileDigest(file))
    }
}
