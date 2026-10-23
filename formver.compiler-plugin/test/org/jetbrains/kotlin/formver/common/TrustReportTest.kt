package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.formver.common.TrustedAssumption.Kind
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TrustReportTest {
    @TempDir
    lateinit var temp: File

    private val abs = TrustedAssumption(Kind.BUNDLED_SPEC, "kotlin.math.abs(kotlin.Int): kotlin.Int")
    private val helper = TrustedAssumption(Kind.NEVER_VERIFY, "helper(kotlin.Int): kotlin.Int")

    @Test
    fun `a rendered report parses back to the same entries`() {
        val report = TrustReport(mapOf(
            "b.kt" to mapOf("g" to setOf(helper, abs), "clean" to emptySet()),
            "a.kt" to mapOf("f" to setOf(TrustedAssumption(Kind.USER_SPEC, "lib.Counter"))),
        ))
        assertEquals(report.files, TrustReport.parse(report.render()).files)
    }

    @Test
    fun `an update replaces recompiled files and drops files that are gone`() {
        val old = TrustReport(mapOf(
            "kept.kt" to mapOf("k" to setOf(abs)),
            "recompiled.kt" to mapOf("old" to setOf(helper)),
            "emptied.kt" to mapOf("e" to emptySet()),
            "deleted.kt" to mapOf("d" to emptySet()),
        ))
        val newer = TrustReport(mapOf("recompiled.kt" to mapOf("new" to emptySet())))
        val updated = old.updatedWith(newer, setOf("recompiled.kt", "emptied.kt")) { it != "deleted.kt" }
        assertEquals(
            mapOf("kept.kt" to mapOf("k" to listOf(abs)), "recompiled.kt" to mapOf("new" to emptyList())),
            updated.files,
        )
    }

    @Test
    fun `semantic opt-ins survive report updates without counting as trust`() {
        val mode = SemanticOptIn(SemanticOptIn.Kind.WRAPPING_INT, "capacity(Int, Int): Int")
        val longMode = SemanticOptIn(SemanticOptIn.Kind.WRAPPING_LONG, mode.name)
        val ledger = TrustLedger()
        ledger.record("kept.kt", "capacity", { setOf(longMode, mode) }) { emptySet() }
        val clean = ledger.snapshot()
        assertEquals(TrustReport(mapOf("kept.kt" to mapOf("capacity" to emptySet()))).summary, clean.summary)
        val mixed = clean.updatedWith(TrustReport(mapOf("other.kt" to mapOf("caller" to setOf(abs)))), emptySet()) { true }
        val parsed = TrustReport.parse(mixed.renamed { "src/$it" }.render())
        assertEquals(mapOf("src/kept.kt" to mapOf("capacity" to listOf(mode, longMode))), parsed.semanticOptIns)
        assertEquals(
            TrustReport(mapOf("kept.kt" to mapOf("capacity" to emptySet()), "other.kt" to mapOf("caller" to setOf(abs)))).summary,
            parsed.summary,
        )
        val replaced = parsed.updatedWith(
            TrustReport(mapOf("src/kept.kt" to mapOf("capacity" to emptySet()))), setOf("src/kept.kt"),
        ) { true }
        assertEquals(emptyMap(), replaced.semanticOptIns)
        assertEquals(emptyMap(), parsed.updatedWith(TrustReport(emptyMap()), emptySet()) { false }.semanticOptIns)
    }

    @Test
    fun `Int semantic report serialization remains readable`() {
        val text = "SnaKt verified 1 declaration; none relies on trusted assumptions.\n\na.kt\n  f\n    @WrappingIntArithmetic: f(Int): Int\n"
        val parsed = TrustReport.parse(text)
        assertEquals(
            listOf(SemanticOptIn(SemanticOptIn.Kind.WRAPPING_INT, "f(Int): Int")),
            parsed.semanticOptIns.getValue("a.kt").getValue("f"),
        )
    }

    @Test
    fun `a malformed report is rejected`() {
        assertFailsWith<IllegalArgumentException> { TrustReport.parse("summary\n\n  declaration without a file\n") }
        assertFailsWith<IllegalArgumentException> { TrustReport.parse("summary\n\na.kt\n  f\n    unknown kind: x\n") }
        assertFailsWith<IllegalArgumentException> { TrustReport.parse("summary\n\na.kt\n    @NeverVerify: x\n") }
    }

    @Test
    fun `a report whose text render would not write is rejected`() {
        val rendered = TrustReport(mapOf("a.kt" to mapOf("f" to setOf(abs)))).render()
        val malformed = listOf(
            rendered.replaceBefore("\n", "SnaKt verified 2 declarations."),
            rendered.substringAfter("\n"),
            "",
            "SnaKt verified no declarations.\n\nempty.kt\n",
            rendered + "\nb.kt\n  g\n",
            rendered + rendered.substringAfter("\n"),
            rendered.replace("\n", "\r\n"),
        )
        for (text in malformed) assertFailsWith<IllegalArgumentException>(text) { TrustReport.parse(text) }
    }

    @Test
    fun `only a newline ends a line`() {
        val report = TrustReport(mapOf("a.kt" to mapOf("f\u2028g" to setOf(abs))))
        assertEquals(report.files, TrustReport.parse(report.render()).files)
        val rendered = TrustReport(mapOf("a.kt" to mapOf("f" to emptySet(), "g" to emptySet()))).render()
        assertFailsWith<IllegalArgumentException> { TrustReport.parse(rendered.replace("  f\n", "  f\r")) }
    }

    @Test
    fun `a malformed existing report fails the update and stays as it was`() {
        val target = temp.resolve("report.txt").apply { writeText("not a report\n") }
        assertFailsWith<IllegalArgumentException> { writeTrustReport(target, TrustReport(emptyMap()), emptySet()) }
        assertEquals("not a report\n", target.readText())
    }

    @Test
    fun `the written report names files relative to it and keeps files a later compilation skipped`() {
        val sources = temp.resolve("src").apply { mkdirs() }
        val first = sources.resolve("First.kt").apply { writeText("") }
        val second = sources.resolve("Second.kt").apply { writeText("") }
        val target = temp.resolve("build/report.txt")
        writeTrustReport(target, TrustReport(mapOf(first.path to mapOf("f" to setOf(abs)))), setOf(first.path, second.path))
        val updated = writeTrustReport(target, TrustReport(mapOf(second.path to mapOf("s" to setOf(helper)))), setOf(second.path))
        val expected = mapOf("../src/First.kt" to mapOf("f" to listOf(abs)), "../src/Second.kt" to mapOf("s" to listOf(helper)))
        assertEquals(expected, updated.files)
        assertEquals(expected, TrustReport.parse(target.readText()).files)
    }
}
