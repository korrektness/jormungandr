package org.jetbrains.kotlin.formver.plugin.services

import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class TestVerifierPoolTest {
    private class Resource : Closeable {
        var closes = 0
        override fun close() { closes++ }
    }

    private val configuration = TestVerifierConfiguration("z3", false, 30, null, null, false, null)

    @Test
    fun `separate batches reuse resources only for matching configurations`() {
        val configurations = listOf(
            configuration.copy(z3 = "other-z3"),
            configuration.copy(counterexamples = true),
            configuration.copy(timeoutSeconds = 60),
            configuration.copy(cacheDirectory = Path.of("cache")),
            configuration.copy(cacheReportDirectory = Path.of("report")),
            configuration.copy(cacheRefresh = true),
            configuration.copy(cacheColdSample = "sample"),
        )
        val created = mutableListOf<Resource>()
        TestVerifierPool<TestVerifierConfiguration, Resource>(configurations.size + 1) {
            Resource().also { created.add(it) }
        }.use { pool ->
            val first = pool.checkout(configuration).use { it.run { resource -> resource } }
            configurations.forEach { other ->
                val isolated = pool.checkout(other).use { it.run { resource -> resource } }
                assertNotSame(first, isolated)
                pool.checkout(other.copy()).use { lease -> lease.run { assertSame(isolated, it) } }
            }
            pool.checkout(configuration.copy()).use { lease -> lease.run { assertSame(first, it) } }
            assertEquals(configurations.size + 1, created.size)
        }
        assertEquals(List(created.size) { 1 }, created.map { it.closes })
    }

    @Test
    fun `caught verification exceptions still discard the resource`() {
        TestVerifierPool<String, Resource>(1) { Resource() }.use { pool ->
            lateinit var discarded: Resource
            pool.checkout("key").use { lease ->
                lease.run { discarded = it }
                assertFailsWith<IllegalArgumentException> {
                    lease.run { throw IllegalArgumentException("verification failed") }
                }
                assertFailsWith<IllegalStateException> { lease.run { } }
            }
            assertEquals(1, discarded.closes)
            pool.checkout("key").use { lease -> lease.run { assertNotSame(discarded, it) } }
        }
    }

    @Test
    fun `capacity includes exclusive checkouts and idle resources across keys`() {
        val waiting = CountDownLatch(1)
        val alive = AtomicInteger()
        val maximum = AtomicInteger()
        class TrackedResource : Closeable {
            init { maximum.accumulateAndGet(alive.incrementAndGet(), ::maxOf) }
            override fun close() { alive.decrementAndGet() }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            TestVerifierPool<String, TrackedResource>(2, awaitAvailable = {
                waiting.countDown()
                it.await()
            }) { TrackedResource() }.use { pool ->
                val first = pool.checkout("same")
                val second = pool.checkout("same")
                try {
                    assertNotSame(first.run { it }, second.run { it })
                    val pending = executor.submit<TrackedResource> { checkoutIdentity(pool, "different") }
                    waiting.await()
                    first.close()
                    val replacement = pending.get()
                    assertNotSame(second.run { it }, replacement)
                    assertEquals(2, maximum.get())
                    assertEquals(2, alive.get())
                } finally {
                    first.close()
                    second.close()
                }
                // Both slots are idle; a new key must evict rather than expand ownership.
                pool.checkout("third").use { it.run { assertEquals(2, alive.get()) } }
                assertEquals(2, maximum.get())
            }
            assertEquals(0, alive.get())
        } finally {
            executor.shutdownNow()
        }
    }

    private fun <K, V : Closeable> checkoutIdentity(pool: TestVerifierPool<K, V>, key: K): V =
        pool.checkout(key).use { lease -> lease.run { it } }

    @Test
    fun `closing drains idle resources and closes outstanding leases on return`() {
        val pool = TestVerifierPool<String, Resource>(2) { Resource() }
        val active = pool.checkout("active")
        val activeResource = active.run { it }
        val idle = pool.checkout("idle").use { it.run { resource -> resource } }
        pool.close()
        assertEquals(1, idle.closes)
        assertEquals(0, activeResource.closes)
        assertFailsWith<IllegalStateException> { pool.checkout("idle") }
        active.close()
        active.close()
        pool.close()
        assertEquals(1, activeResource.closes)
        assertEquals(1, idle.closes)
    }

    @Test
    fun `construction and cleanup failures propagate without losing capacity`() {
        var creations = 0
        val pool = TestVerifierPool<String, Closeable>(1) {
            when (++creations) {
                1 -> throw IllegalArgumentException("construction failed")
                2 -> Closeable { throw IllegalStateException("cleanup failed") }
                else -> Resource()
            }
        }
        pool.use {
            assertFailsWith<IllegalArgumentException> { pool.checkout("key") }
            pool.checkout("key").use { lease -> lease.run { } }
            assertFailsWith<IllegalStateException> { pool.checkout("other") }
            pool.checkout("other").use { lease -> lease.run { assertEquals(3, creations) } }
        }
    }
}
