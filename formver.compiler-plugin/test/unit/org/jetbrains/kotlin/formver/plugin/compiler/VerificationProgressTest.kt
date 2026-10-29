package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.formver.common.FailureKind
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.viper.VerificationTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerificationProgressTest {
    private class RecordingCollector : MessageCollector {
        val messages = mutableListOf<Pair<CompilerMessageSeverity, String>>()
        override fun clear() = messages.clear()
        override fun hasErrors(): Boolean = false
        override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
            messages += severity to message
        }
    }

    @Test
    fun `a timed-out declaration yields a timeout failure naming it and the next declaration still runs`() {
        val collector = RecordingCollector()
        val ticks = listOf(0L, 1_500_000_000L, 2_000_000_000L, 2_250_000_000L).iterator()
        val progress = VerificationProgress(collector) { ticks.next() }
        var nextRan = false

        val timeout = progress.run("a.Slow.f", null) { throw VerificationTimeoutException(7) }
        val next = progress.run("a.fast", null) { nextRan = true }

        assertEquals(FailureKind.VERIFICATION_TIMEOUT, timeout?.kind)
        assertEquals(KotlinCondition.Timeout("a.Slow.f", 7), timeout?.condition)
        assertNull(next)
        assertTrue(nextRan)
        assertEquals(List(4) { CompilerMessageSeverity.INFO }, collector.messages.map { it.first })
        val (slowStart, slowEnd, fastStart, fastEnd) = collector.messages.map { it.second }
        assertTrue("a.Slow.f" in slowStart && "a.fast" !in slowStart)
        assertTrue("a.Slow.f" in slowEnd && "1500" in slowEnd)
        assertTrue("a.fast" in fastStart)
        assertTrue("a.fast" in fastEnd && "250" in fastEnd)
    }
}
