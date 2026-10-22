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

    @Test
    fun `exclusive teardown protects recorder iteration from verification and other teardown`() {
        for (exclusiveContender in listOf(false, true)) {
            val attempted = CountDownLatch(1)
            val gate = SiliconConfigurationGate { condition ->
                attempted.countDown()
                check(condition.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Lease was not released." }
            }
            val sinks = scala.collection.mutable.ArrayBuffer<String>()
            sinks.addOne("recorded output")
            val iterating = CountDownLatch(1)
            val release = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val first = executor.submit<List<String>> {
                    gate.withExclusiveConfiguration(listOf("same")) {
                        val iterator = sinks.iterator()
                        iterating.countDown()
                        release.awaitBounded()
                        buildList { while (iterator.hasNext()) add(iterator.next()) }
                    }
                }
                iterating.awaitBounded()
                val second = executor.submit {
                    val clear = { sinks.clear(); attempted.countDown() }
                    if (exclusiveContender) gate.withExclusiveConfiguration(listOf("same"), clear)
                    else gate.withConfiguration(listOf("same"), clear)
                }
                attempted.awaitBounded()
                release.countDown()
                assertEquals(listOf("recorded output"), first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertTrue(sinks.isEmpty())
            } finally {
                release.countDown()
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `teardown waits for all verification leases and releases its lease after failure`() {
        val attempted = CountDownLatch(1)
        val gate = SiliconConfigurationGate { condition ->
            attempted.countDown()
            check(condition.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Lease was not released." }
        }
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val active = AtomicInteger()
        val executor = Executors.newFixedThreadPool(3)
        try {
            val readers = (0..1).map {
                executor.submit {
                    gate.withConfiguration(listOf("same")) {
                        active.incrementAndGet()
                        entered.countDown()
                        release.awaitBounded()
                        active.decrementAndGet()
                    }
                }
            }
            entered.awaitBounded()
            val teardown = executor.submit {
                gate.withExclusiveConfiguration(listOf("same")) {
                    val remaining = active.get()
                    attempted.countDown()
                    assertEquals(0, remaining)
                    error("teardown failure")
                }
            }
            attempted.awaitBounded()
            release.countDown()
            readers.forEach { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            val failure = assertFailsWith<java.util.concurrent.ExecutionException> { teardown.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            assertIs<IllegalStateException>(failure.cause)
            assertEquals(7, gate.withConfiguration(listOf("other")) { 7 })
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
    }
}
