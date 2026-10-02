package org.jetbrains.kotlin.formver.plugin.services

import org.jetbrains.kotlin.codeMetaInfo.CodeMetaInfoParser
import org.jetbrains.kotlin.codeMetaInfo.clearTextFromDiagnosticMarkup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VerificationOutcomesTest {
    private val clean = """
        fun f() { verify(false) }
        fun g() { verify(true) }
    """.trimIndent()

    private val functions = listOf("f", "g").map { name ->
        val start = clean.indexOf("fun $name")
        FunctionSpan(name, start, clean.indexOf('}', start) + 1)
    }

    private fun findings(marked: String): List<Finding> {
        assertEquals(clean, clearTextFromDiagnosticMarkup(marked))
        return CodeMetaInfoParser.getCodeMetaInfoFromText(marked).map { Finding(it.start, it.tag, "message") }
    }

    private val fFails = """
        fun f() { <!VIPER_VERIFICATION_ERROR!>verify(false)<!> }
        fun g() { verify(true) }
    """.trimIndent()

    private val fSkipped = """
        fun <!VERIFICATION_SKIPPED!>f<!>() { verify(false) }
        fun g() { verify(true) }
    """.trimIndent()

    private fun changes(expected: String, actual: String, verified: Boolean = true) =
        outcomeChanges(functions, findings(expected), findings(actual), verified)

    @Test
    fun `update mode refuses a new failure and record mode accepts it`() {
        val changes = changes(clean, fFails)
        assertEquals(
            listOf(
                OutcomeChange(
                    "f", VerificationOutcome.CLEAN, VerificationOutcome.FAILED, listOf("VIPER_VERIFICATION_ERROR: message")
                )
            ),
            changes,
        )
        assertFailsWith<OutcomeChangeRefused> { checkOutcomeChanges(changes, updating = true, recordOutcomes = false) }
        checkOutcomeChanges(changes, updating = true, recordOutcomes = true)
    }

    @Test
    fun `a failure that starts verifying is a change`() {
        assertEquals(
            listOf(OutcomeChange("f", VerificationOutcome.FAILED, VerificationOutcome.CLEAN, emptyList())),
            changes(fFails, clean),
        )
    }

    @Test
    fun `a failure that moves within its function is not a change`() {
        val moved = """
            fun <!VIPER_VERIFICATION_ERROR!>f<!>() { verify(false) }
            fun g() { verify(true) }
        """.trimIndent()
        assertEquals(emptyList(), changes(fFails, moved))
    }

    @Test
    fun `a failure that moves to another function is a change for both`() {
        val inG = """
            fun f() { verify(false) }
            fun g() { <!VIPER_VERIFICATION_ERROR!>verify(true)<!> }
        """.trimIndent()
        assertEquals(listOf("f", "g"), changes(fFails, inG).map { it.function })
    }

    @Test
    fun `a run without verification compares skips only`() {
        assertEquals(emptyList(), changes(clean, fFails, verified = false))
        assertEquals(
            listOf(
                OutcomeChange(
                    "f", VerificationOutcome.CLEAN, VerificationOutcome.SKIPPED, listOf("VERIFICATION_SKIPPED: message")
                )
            ),
            changes(clean, fSkipped, verified = false),
        )
    }
}
