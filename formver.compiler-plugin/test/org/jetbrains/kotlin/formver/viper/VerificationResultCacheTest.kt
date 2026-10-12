package org.jetbrains.kotlin.formver.viper

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
        assertFailsWith<IllegalStateException> {
            cache.verify("program", "version", emptyList()) { error("backend failure") }
        }
        Files.list(directory).use { assertEquals(0L, it.count()) }
        assertTrue(cache.verify("program", "version", emptyList()) { true })
    }

    @Test
    fun `corrupt and misplaced entries are reported before verification`() {
        val cache = VerificationResultCache(directory)
        cache.verify("program", "version", emptyList()) { true }
        val entry = Files.list(directory).use { it.findFirst().orElseThrow() }
        val valid = Files.readAllBytes(entry)
        Files.write(entry, byteArrayOf(0, 1, 2))
        assertFailsWith<CorruptVerificationCacheException> {
            cache.verify("program", "version", emptyList()) { error("Corruption must surface before proof search") }
        }
        Files.write(entry, valid)
        cache.verify("other program", "version", emptyList()) { true }
        val other = Files.list(directory).use { stream -> stream.filter { it != entry }.findFirst().orElseThrow() }
        Files.write(other, valid)
        assertFailsWith<CorruptVerificationCacheException> {
            cache.verify("other program", "version", emptyList()) { error("Wrong-key entry must not be trusted") }
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
        Files.list(directory).use { assertEquals(1L, it.count()) }
    }
}
