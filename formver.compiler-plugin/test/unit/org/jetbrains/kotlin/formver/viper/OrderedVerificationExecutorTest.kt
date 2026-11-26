package org.jetbrains.kotlin.formver.viper

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OrderedVerificationExecutorTest {
    @Test
    fun `jobs run concurrently within the budget and results retain input order`() {
        for ((setting, jobs) in listOf("1" to 1, null to 4, "+8" to 8, "16" to 16)) {
            val configured = VerificationConcurrency.parseJobs(setting)
            assertEquals(jobs, configured)
            OrderedVerificationExecutor(configured).use { executor ->
                val active = AtomicInteger()
                val maximum = AtomicInteger()
                val barrier = CyclicBarrier(jobs)
                val inputs = (jobs * 2 downTo 1).toList()
                val results = executor.map(inputs) { input ->
                    val count = active.incrementAndGet()
                    maximum.accumulateAndGet(count, ::maxOf)
                    barrier.await()
                    active.decrementAndGet()
                    input * 10
                }
                assertEquals(jobs, maximum.get())
                assertEquals(inputs.map { it * 10 }, results)
            }
        }
    }

    @Test
    fun `invalid job settings are rejected`() {
        for (value in listOf("", "garbage", "1.5", " 4", "0", "-1", "17", "2147483648", "-2147483649")) {
            assertFailsWith<IllegalArgumentException> { VerificationConcurrency.parseJobs(value) }
        }
    }

    @Test
    fun `workers reuse exclusive resources without dropping repeated inputs`() {
        val opened = AtomicInteger()
        val closed = AtomicInteger()
        val started = AtomicInteger()
        val barrier = CyclicBarrier(2)
        class Resource : java.io.Closeable {
            val active = java.util.concurrent.atomic.AtomicBoolean()
            override fun close() { closed.incrementAndGet() }
        }
        OrderedVerificationExecutor(2).use { executor ->
            val results = executor.mapWithResource(listOf(4, 3, 4, 1), { opened.incrementAndGet(); Resource() }) { resource, value ->
                assertTrue(resource.active.compareAndSet(false, true))
                if (started.getAndIncrement() < 2) barrier.await()
                resource.active.set(false)
                value * 10
            }
            assertEquals(listOf(40, 30, 40, 10), results)
        }
        assertEquals(2, opened.get())
        assertEquals(2, closed.get())
    }

    @Test
    fun `resource close failure is suppressed when verification throws`() {
        val verificationFailure = IllegalStateException()
        val closeFailure = IllegalArgumentException()
        OrderedVerificationExecutor(2).use { executor ->
            val thrown = assertFailsWith<IllegalStateException> {
                executor.mapWithResource(listOf(1), { java.io.Closeable { throw closeFailure } }) { _, _ ->
                    throw verificationFailure
                }
            }
            assertSame(verificationFailure, thrown)
            assertEquals(listOf(closeFailure), thrown.suppressed.toList())
        }
    }

    @Test
    fun `closing cancels queued jobs and wakes their caller`() {
        val submitted = CountDownLatch(1)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val calls = AtomicInteger()
        val executor = OrderedVerificationExecutor(1)
        val close = FutureTask {
            submitted.await()
            started.await()
            executor.close()
        }
        val closer = Thread(close).apply { start() }
        try {
            assertFailsWith<CancellationException> {
                executor.map(notifyingList(listOf(1, 2), submitted)) {
                    calls.incrementAndGet()
                    started.countDown()
                    try {
                        release.await()
                    } catch (_: InterruptedException) {
                        // A worker can complete normally after shutdown interrupts it.
                    } finally {
                        stopped.countDown()
                    }
                }
            }
            close.get()
            stopped.await()
            assertEquals(1, calls.get())
            assertFailsWith<RejectedExecutionException> { executor.map(listOf(3)) { it } }
        } finally {
            submitted.countDown()
            started.countDown()
            release.countDown()
            executor.close()
            closer.join()
        }
    }

    /** Signals after the caller has submitted every element, while a worker can still be blocked. */
    private fun <T> notifyingList(values: List<T>, exhausted: CountDownLatch): List<T> = object : List<T> by values {
        override fun iterator(): Iterator<T> {
            val delegate = values.iterator()
            return object : Iterator<T> by delegate {
                override fun hasNext(): Boolean = delegate.hasNext().also { if (!it) exhausted.countDown() }
            }
        }
    }

    @Test
    fun `input traversal failure interrupts accepted jobs without closing the executor`() {
        val started = CountDownLatch(1)
        val release = Semaphore(0)
        val interrupted = CompletableFuture<Boolean>()
        val failure = IllegalStateException()
        val inputs = object : List<Int> by listOf(1, 2) {
            override fun iterator(): Iterator<Int> = sequence {
                yield(1)
                started.await()
                throw failure
            }.iterator()
        }
        OrderedVerificationExecutor(1).use { executor ->
            try {
                val thrown = assertFailsWith<IllegalStateException> {
                    executor.map(inputs) {
                        started.countDown()
                        release.acquireUninterruptibly()
                        interrupted.complete(Thread.currentThread().isInterrupted)
                    }
                }
                assertSame(failure, thrown)
            } finally {
                release.release()
            }
            assertTrue(interrupted.get())
            assertEquals(listOf(6, 2), executor.map(listOf(3, 1)) { it * 2 })
        }
    }

    @Test
    fun `a later worker failure interrupts an earlier blocked job and propagates to the caller`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val failure = IllegalArgumentException("second job failed")
        OrderedVerificationExecutor(2).use { executor ->
            try {
                val thrown = assertFailsWith<IllegalArgumentException> {
                    executor.map(listOf(1, 2)) { input ->
                        if (input == 1) {
                            started.countDown()
                            try {
                                release.await()
                            } finally {
                                stopped.countDown()
                            }
                        } else {
                            started.await()
                            throw failure
                        }
                    }
                }
                assertSame(failure, thrown)
                stopped.await()
            } finally {
                release.countDown()
            }
        }
    }
}
