package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.formver.viper.VerifierUnavailableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class VerifierLocatorTest {
    @Test
    fun `a missing verifier is reported once for every function`() {
        var lookups = 0
        val locator = VerifierLocator { lookups++; throw VerifierUnavailableException("no z3") }
        val reports = mutableListOf<String>()

        repeat(3) { assertNull(locator.z3Exe { reports.add(it) }) }

        assertEquals(listOf("no z3"), reports)
        assertEquals(1, lookups)
    }

    @Test
    fun `a found verifier is looked up once and never reported`() {
        var lookups = 0
        val locator = VerifierLocator { lookups++; "/usr/bin/z3" }
        val reports = mutableListOf<String>()

        repeat(3) { assertEquals("/usr/bin/z3", locator.z3Exe { reports.add(it) }) }

        assertEquals(emptyList(), reports)
        assertEquals(1, lookups)
    }

    @Test
    fun `a failure other than a missing verifier propagates`() {
        val locator = VerifierLocator { error("broken") }

        assertFailsWith<IllegalStateException> { locator.z3Exe { } }
    }
}
