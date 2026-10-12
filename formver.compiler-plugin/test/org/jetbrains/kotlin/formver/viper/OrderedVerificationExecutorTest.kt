package org.jetbrains.kotlin.formver.viper

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrderedVerificationExecutorTest {
    @Test
    fun `jobs run concurrently within the budget and results retain input order`() {
        OrderedVerificationExecutor(2).use { executor ->
            val active = AtomicInteger()
            val maximum = AtomicInteger()
            val barrier = CyclicBarrier(2)
            val results = executor.map(listOf(4, 3, 2, 1)) { value ->
                val count = active.incrementAndGet()
                maximum.accumulateAndGet(count, ::maxOf)
                barrier.await()
                active.decrementAndGet()
                value * 10
            }
            assertEquals(2, maximum.get())
            assertEquals(listOf(40, 30, 20, 10), results)
        }
    }

    @Test
    fun `workers reuse exclusive resources and close them`() {
        val opened = AtomicInteger()
        val closed = AtomicInteger()
        val barrier = CyclicBarrier(2)
        class Resource : java.io.Closeable {
            val active = java.util.concurrent.atomic.AtomicBoolean()
            override fun close() { closed.incrementAndGet() }
        }
        OrderedVerificationExecutor(2).use { executor ->
            val results = executor.mapWithResource(listOf(4, 3, 2, 1), { opened.incrementAndGet(); Resource() }) { resource, value ->
                kotlin.test.assertTrue(resource.active.compareAndSet(false, true))
                barrier.await()
                resource.active.set(false)
                value
            }
            assertEquals(listOf(4, 3, 2, 1), results)
        }
        assertEquals(2, opened.get())
        assertEquals(2, closed.get())
    }

    @Test
    fun `resource closes when verification throws`() {
        val closed = AtomicInteger()
        OrderedVerificationExecutor(2).use { executor ->
            assertFailsWith<IllegalStateException> {
                executor.mapWithResource(listOf(1), { java.io.Closeable { closed.incrementAndGet() } }) { _, _ ->
                    error("verification failed")
                }
            }
        }
        assertEquals(1, closed.get())
    }

    @Test
    fun `worker exceptions propagate to the caller`() {
        OrderedVerificationExecutor(2).use { executor ->
            assertFailsWith<IllegalArgumentException> {
                executor.map(listOf(1)) { throw IllegalArgumentException() }
            }
        }
    }
}
