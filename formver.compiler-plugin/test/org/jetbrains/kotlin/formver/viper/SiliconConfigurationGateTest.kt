package org.jetbrains.kotlin.formver.viper

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class SiliconConfigurationGateTest {
    private fun CountDownLatch.awaitBounded() = check(await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Latch was not released." }

    @Test
    fun `identical configurations can run concurrently`() {
        val gate = SiliconConfigurationGate { error("Identical configurations must not wait.") }
        val entered = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = (0..1).map {
                executor.submit<Int> {
                    gate.withConfiguration(listOf("mapped")) {
                        entered.countDown()
                        entered.awaitBounded()
                        7
                    }
                }
            }
            assertEquals(listOf(7, 7), futures.map { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `a different configuration waits until a failed action releases its lease`() {
        val waiting = CountDownLatch(1)
        val gate = SiliconConfigurationGate { condition ->
            waiting.countDown()
            check(condition.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Lease was not released." }
        }
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val active = AtomicInteger()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit {
                gate.withConfiguration(listOf("first")) {
                    active.incrementAndGet()
                    holding.countDown()
                    release.awaitBounded()
                    active.decrementAndGet()
                    error("test failure")
                }
            }
            holding.awaitBounded()
            val second = executor.submit<Int> { gate.withConfiguration(listOf("second")) { active.get() } }
            waiting.awaitBounded()
            assertFalse(second.isDone)
            release.countDown()
            val failure = assertFailsWith<java.util.concurrent.ExecutionException> { first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            assertIs<IllegalStateException>(failure.cause)
            assertEquals(0, second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
    }
}
