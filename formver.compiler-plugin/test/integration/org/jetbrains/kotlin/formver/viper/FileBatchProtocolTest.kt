@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.jetbrains.kotlin.formver.viper

import org.jetbrains.kotlin.formver.plugin.compiler.FileVerificationBackend
import org.jetbrains.kotlin.formver.plugin.compiler.FileVerificationSink
import org.jetbrains.kotlin.formver.plugin.compiler.VerificationRequest
import org.jetbrains.kotlin.formver.plugin.compiler.VerificationResult
import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import viper.silver.ast.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileBatchProtocolTest {
    private fun assertion(succeeds: Boolean): Program {
        val pos = `NoPosition$`.`MODULE$`
        val info = `NoInfo$`.`MODULE$`
        val trafos = `NoTrafos$`.`MODULE$`
        val condition: Exp = if (succeeds) TrueLit(pos, info, trafos) else FalseLit(pos, info, trafos)
        val body = Seqn(seqOf<Stmt>(Assert(condition, pos, info, trafos)), emptySeq(), pos, info, trafos)
        val method = Method("m", emptySeq(), emptySeq(), emptySeq(), emptySeq(), body.toScalaOption(), pos, info, trafos)
        return Program(emptySeq(), emptySeq(), emptySeq(), emptySeq(), seqOf(method), emptySeq(), pos, info, trafos)
    }

    @Test
    fun `concurrent Silicon backends preserve sibling outcomes and close before replay`() {
        val entered = CountDownLatch(2)
        val threads = ConcurrentLinkedQueue<Thread>()
        val closed = AtomicInteger()
        val sink = FileVerificationSink(2) { request ->
            val frontend = SiliconFrontend(request.z3Exe)
            object : FileVerificationBackend {
                private var first = true
                override fun verify(program: Program, onError: (VerifierError) -> Unit) {
                    if (first) {
                        first = false
                        threads.add(Thread.currentThread())
                        entered.countDown()
                        entered.await()
                    }
                    frontend.verify(program, onError)
                }
                override fun close() {
                    frontend.close()
                    closed.incrementAndGet()
                }
            }
        }
        val results = mutableListOf<VerificationResult>()
        val z3 = SiliconFrontend.locateZ3()
        val outcomes = listOf(false, true, false, true)
        outcomes.forEach { succeeds ->
            sink.submit(VerificationRequest(assertion(succeeds), z3, 0, null, emptyList(), false)) {
                assertEquals(2, closed.get())
                results += it()
            }
        }
        sink.flush()
        assertEquals(2, threads.toSet().size)
        threads.forEach { assertFalse(it.isAlive) }
        assertEquals(outcomes.size, results.size)
        results.zip(outcomes).forEach { (result, succeeds) ->
            assertNull(result.failure)
            val assertions = result.errors.filterIsInstance<VerificationError>().filter { it.result.offendingNode() is Assert }
            if (succeeds) assertTrue(result.errors.isEmpty()) else assertEquals(1, assertions.size)
        }
    }
}
