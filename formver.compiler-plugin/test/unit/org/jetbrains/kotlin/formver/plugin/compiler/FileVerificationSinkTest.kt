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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
    fun `replay assertion does not discard later siblings or replace the batch failure`() {
        for (teardown in listOf(null, IllegalStateException())) {
            val sink = FileVerificationSink(1) {
                object : FileVerificationBackend {
                    override fun verify(program: Program, onError: (VerifierError) -> Unit) = Unit
                    override fun close() { teardown?.let { throw it } }
                }
            }
            val error = AssertionError()
            val replayed = mutableListOf<VerificationResult>()
            val request = VerificationRequest(program(), "z3", 0, null, emptyList(), false)
            sink.submit(request) { throw error }
            sink.submit(request) { replayed += it() }
            val thrown = assertFailsWith<Throwable> { sink.flush() }
            assertSame(teardown ?: error, thrown)
            assertEquals(listOfNotNull(error.takeIf { teardown != null }), thrown.suppressed.toList())
            assertNull(replayed.single().failure)
        }
    }

    @Test
    fun `each replay interruption is attached without replacing teardown failure`() {
        val teardown = IllegalStateException()
        val sink = FileVerificationSink(1) {
            object : FileVerificationBackend {
                override fun verify(program: Program, onError: (VerifierError) -> Unit) = Unit
                override fun close() { throw teardown }
            }
        }
        val request = VerificationRequest(program(), "z3", 0, null, emptyList(), false)
        repeat(2) {
            sink.submit(request) {
                assertFalse(Thread.currentThread().isInterrupted)
                Thread.currentThread().interrupt()
            }
        }
        val replayed = mutableListOf<VerificationResult>()
        sink.submit(request) {
            assertFalse(Thread.currentThread().isInterrupted)
            replayed += it()
        }
        try {
            assertSame(teardown, assertFailsWith<IllegalStateException> { sink.flush() })
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
        assertEquals(2, teardown.suppressed.size)
        teardown.suppressed.forEach { assertIs<InterruptedException>(it) }
        assertNull(replayed.single().failure)
    }

    @Test
    fun `overflow and its completed sibling survive concurrent cleanup failure`() {
        val programs = List(2) { program() }
        val overflow = StackOverflowError()
        val teardown = IllegalStateException()
        val siblingStarted = CountDownLatch(1)
        val overflowClosed = CountDownLatch(1)
        val sink = FileVerificationSink(2) { request ->
            object : FileVerificationBackend {
                override fun verify(program: Program, onError: (VerifierError) -> Unit) {
                    if (program === programs[0]) {
                        siblingStarted.await()
                        throw overflow
                    }
                    siblingStarted.countDown()
                    overflowClosed.await()
                }
                override fun close() {
                    if (request.program === programs[0]) overflowClosed.countDown() else throw teardown
                }
            }
        }
        val replayed = mutableListOf<Throwable?>()
        programs.forEach { program ->
            sink.submit(VerificationRequest(program, "z3", 0, null, emptyList(), false)) {
                replayed += it().failure
                it().throwFailure()
            }
        }
        assertSame(overflow, assertFailsWith<StackOverflowError> { sink.flush() })
        assertEquals(listOf(teardown), overflow.suppressed.toList())
        assertEquals(listOf<Throwable?>(overflow, null), replayed)
    }

    @Test
    fun `option switch teardown does not fill the incoming result slot`() {
        val teardown = IllegalStateException()
        val closed = mutableListOf<Boolean>()
        val sink = FileVerificationSink(1) { request ->
            object : FileVerificationBackend {
                override fun verify(program: Program, onError: (VerifierError) -> Unit) = Unit
                override fun close() {
                    closed += request.counterexamples
                    if (!request.counterexamples) throw teardown
                }
            }
        }
        val request = VerificationRequest(program(), "z3", 0, null, emptyList(), false)
        val replayed = mutableListOf<Pair<Boolean, Throwable?>>()
        for (mode in listOf(false, true)) {
            sink.submit(request.copy(counterexamples = mode)) { replayed += mode to it().failure }
        }
        assertSame(teardown, assertFailsWith<IllegalStateException> { sink.flush() })
        assertEquals(listOf(false), closed)
        assertEquals(listOf<Pair<Boolean, Throwable?>>(false to null), replayed)
    }

    @Test
    fun `worker cancellation replays the interrupted attempt without interrupting the checker`() {
        val programs = List(3) { program() }
        val cleanups = mutableListOf<Boolean>()
        val sink = FileVerificationSink(1) {
            object : FileVerificationBackend {
                override fun verify(program: Program, onError: (VerifierError) -> Unit) {
                    if (program === programs[1]) {
                        Thread.currentThread().interrupt()
                        CountDownLatch(1).await()
                    }
                }
                override fun close() { cleanups += Thread.currentThread().isInterrupted }
            }
        }
        val replayed = mutableListOf<Pair<Int, Throwable?>>()
        programs.forEachIndexed { index, program ->
            sink.submit(VerificationRequest(program, "z3", 0, null, emptyList(), false)) {
                val result = it()
                replayed += index to result.failure
                result.throwFailure()
            }
        }
        val failure = assertFailsWith<InterruptedException> { sink.flush() }
        assertFalse(Thread.interrupted())
        assertEquals(listOf(false), cleanups)
        assertEquals(listOf<Pair<Int, Throwable?>>(0 to null, 1 to failure), replayed)
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
        assertEquals(listOf(7), result.results)
        assertEquals(1, created.get())
        assertEquals(1, closed.get())
    }

    @Test
    fun `checker interruption joins cleanup and replays completed and overflowing jobs`() {
        val started = CountDownLatch(1)
        val closing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finishVerification = CountDownLatch(1)
        val outcome = AtomicReference<Throwable>()
        val interruptRestored = AtomicReference<Boolean>()
        val programs = List(2) { program() }
        val overflow = StackOverflowError()
        val cleaned = AtomicInteger()
        val sink = FileVerificationSink(1) {
            object : FileVerificationBackend {
                override fun verify(program: Program, onError: (VerifierError) -> Unit) {
                    if (program === programs[1]) {
                        started.countDown()
                        finishVerification.await()
                        throw overflow
                    }
                }
                override fun close() {
                    closing.countDown()
                    release.await()
                    cleaned.incrementAndGet()
                }
            }
        }
        val replayed = mutableListOf<Throwable?>()
        programs.forEach { program ->
            sink.submit(VerificationRequest(program, "z3", 0, null, emptyList(), false)) {
                assertEquals(1, cleaned.get())
                assertFalse(Thread.currentThread().isInterrupted)
                replayed += it().failure
            }
        }
        val caller = thread {
            try {
                sink.flush()
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
        val interruption = assertIs<InterruptedException>(outcome.get())
        assertEquals(listOf(overflow), interruption.suppressed.toList())
        assertEquals(listOf<Throwable?>(null, overflow), replayed)
        assertEquals(true, interruptRestored.get())
    }

    @Test
    fun `worker exception waits for close`() {
        val closing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val outcome = AtomicReference<FileBatchRunner.Batch<Int>>()
        val failure = AssertionError("worker")
        val caller = thread {
            outcome.set(FileBatchRunner.run(listOf(1), 1, {
                object : AutoCloseable {
                    override fun close() { closing.countDown(); release.await() }
                }
            }) { _, _ -> throw failure })
        }
        closing.await()
        try {
            assertTrue(caller.isAlive)
            assertNull(outcome.get())
        } finally {
            release.countDown()
            caller.join()
        }
        assertSame(failure, outcome.get().failures.single())
    }
}
