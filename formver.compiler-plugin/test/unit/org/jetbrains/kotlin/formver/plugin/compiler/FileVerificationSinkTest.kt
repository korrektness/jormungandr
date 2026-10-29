@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.formver.viper.emptySeq
import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import viper.silver.ast.Program
import viper.silver.ast.`NoInfo$`
import viper.silver.ast.`NoPosition$`
import viper.silver.ast.`NoTrafos$`
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FileVerificationSinkTest {
    private fun program() = Program(emptySeq(), emptySeq(), emptySeq(), emptySeq(), emptySeq(), emptySeq(),
        `NoPosition$`.`MODULE$`, `NoInfo$`.`MODULE$`, `NoTrafos$`.`MODULE$`)

    @Test
    fun `worker reuses matching backend and replays failures and siblings on caller`() {
        val programs = List(4) { program() }
        val created = mutableListOf<Boolean>()
        val closed = mutableListOf<Boolean>()
        val caller = Thread.currentThread()
        val failure = IllegalStateException("unit")
        val sink = FileVerificationSink(1) { request ->
            val mode = request.counterexamples
            created += mode
            object : FileVerificationBackend {
                override fun verify(program: Program, onError: (VerifierError) -> Unit) {
                    if (program === programs[1]) throw failure
                }
                override fun close() { closed += mode }
            }
        }
        val replayed = mutableListOf<VerificationResult>()
        programs.forEachIndexed { index, program ->
            sink.submit(VerificationRequest(program, "z3", 0, null, emptyList(), index == 2)) { result ->
                assertSame(caller, Thread.currentThread())
                replayed += result()
            }
        }
        sink.flush()
        assertEquals(listOf(false, true, false), created)
        assertEquals(created, closed)
        assertEquals(4, replayed.size)
        assertSame(failure, replayed[1].failure)
        assertEquals(listOf(null, failure, null, null), replayed.map { it.failure })
    }

    @Test
    fun `out of order completion replays in submission order`() {
        val programs = List(2) { program() }
        val firstStarted = CountDownLatch(1)
        val secondDone = CountDownLatch(1)
        val closed = AtomicInteger()
        val sink = FileVerificationSink(2) {
            object : FileVerificationBackend {
                override fun verify(program: Program, onError: (VerifierError) -> Unit) {
                    if (program === programs[0]) {
                        firstStarted.countDown()
                        secondDone.await()
                    } else {
                        firstStarted.await()
                        secondDone.countDown()
                    }
                }
                override fun close() { closed.incrementAndGet() }
            }
        }
        val replayed = mutableListOf<Int>()
        programs.forEachIndexed { index, program ->
            sink.submit(VerificationRequest(program, "z3", 0, null, emptyList(), false)) {
                assertEquals(2, closed.get())
                replayed += index
                assertNull(it().failure)
            }
        }
        sink.flush()
        assertEquals(listOf(0, 1), replayed)
    }

    @Test
    fun `replay exception does not discard later siblings`() {
        val sink = FileVerificationSink(1) {
            object : FileVerificationBackend {
                override fun verify(program: Program, onError: (VerifierError) -> Unit) = Unit
                override fun close() = Unit
            }
        }
        val error = IllegalArgumentException("replay")
        var sibling = false
        val request = VerificationRequest(program(), "z3", 0, null, emptyList(), false)
        sink.submit(request) { throw error }
        sink.submit(request) { sibling = true }
        val thrown = kotlin.runCatching { sink.flush() }.exceptionOrNull()
        assertSame(error, thrown)
        assertTrue(sibling)
    }

    @Test
    fun `backend construction failure is replayed and the next job retries`() {
        val attempts = AtomicInteger()
        val closed = AtomicInteger()
        val failure = IllegalStateException("construction")
        val sink = FileVerificationSink(1) {
            if (attempts.incrementAndGet() == 1) throw failure
            object : FileVerificationBackend {
                override fun verify(program: Program, onError: (VerifierError) -> Unit) = Unit
                override fun close() { closed.incrementAndGet() }
            }
        }
        val results = mutableListOf<VerificationResult>()
        val request = VerificationRequest(program(), "z3", 0, null, emptyList(), false)
        repeat(2) { sink.submit(request) { results += it() } }
        sink.flush()
        assertEquals(listOf(failure, null), results.map { it.failure })
        assertEquals(1, closed.get())
        sink.flush()
        assertEquals(2, attempts.get())
    }

    @Test
    fun `worker count is capped by the number of jobs`() {
        val created = AtomicInteger()
        val closed = AtomicInteger()
        val result = FileBatchRunner.run(listOf(7), 8, {
            created.incrementAndGet()
            AutoCloseable { closed.incrementAndGet() }
        }) { _, input -> input }
        assertEquals(listOf(7), result)
        assertEquals(1, created.get())
        assertEquals(1, closed.get())
    }

    @Test
    fun `interruption waits for worker cleanup before returning`() {
        val started = CountDownLatch(1)
        val closing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finishVerification = CountDownLatch(1)
        val outcome = AtomicReference<Throwable>()
        val interruptRestored = AtomicReference<Boolean>()
        val caller = thread {
            try {
                FileBatchRunner.run(listOf(1), 4, {
                    object : AutoCloseable {
                        override fun close() { closing.countDown(); release.await() }
                    }
                }) { _, _ ->
                    started.countDown()
                    finishVerification.await()
                    1
                }
            } catch (error: Throwable) {
                outcome.set(error)
            } finally {
                interruptRestored.set(Thread.currentThread().isInterrupted)
            }
        }
        started.await()
        caller.interrupt()
        finishVerification.countDown()
        closing.await()
        try {
            assertTrue(caller.isAlive)
            assertNull(outcome.get())
        } finally {
            release.countDown()
            caller.join()
        }
        assertIs<InterruptedException>(outcome.get())
        assertEquals(true, interruptRestored.get())
    }

    @Test
    fun `worker exception waits for close`() {
        val closing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val outcome = AtomicReference<Throwable>()
        val failure = AssertionError("worker")
        val caller = thread {
            try {
                FileBatchRunner.run(listOf(1), 1, {
                    object : AutoCloseable {
                        override fun close() { closing.countDown(); release.await() }
                    }
                }) { _, _ -> throw failure }
            } catch (error: Throwable) { outcome.set(error) }
        }
        closing.await()
        try {
            assertTrue(caller.isAlive)
            assertNull(outcome.get())
        } finally {
            release.countDown()
            caller.join()
        }
        assertSame(failure, outcome.get())
    }
}
