package org.jetbrains.kotlin.formver.plugin.services

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeclarationVerificationReportTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `reports completed and failed attempts using explicit ticks`() {
        val ticks = listOf(0L, 2_500_000_000L, 3_000_000_000L, 7_000_000_000L).iterator()
        val report = DeclarationVerificationReport(directory, ticks::next)
        report.run("port.kt", "Deque.add", 12, 300) { cache -> cache("off"); "verified" }
        report.run("mutant.kt", "Deque.indexOf", 27, 300) { cache -> cache("miss"); "failed" }
        val rows = rows().associateBy { it.getValue("file") }
        assertEquals("verified", rows.getValue("port.kt").getValue("outcome"))
        assertEquals("2.5", rows.getValue("port.kt").getValue("seconds"))
        assertEquals("Deque.add", rows.getValue("port.kt").getValue("declaration"))
        assertEquals("12", rows.getValue("port.kt").getValue("offset"))
        assertEquals("300", rows.getValue("port.kt").getValue("timeout_seconds"))
        assertEquals("off", rows.getValue("port.kt").getValue("cache"))
        assertEquals("failed", rows.getValue("mutant.kt").getValue("outcome"))
        assertEquals("4.0", rows.getValue("mutant.kt").getValue("seconds"))
        assertEquals("miss", rows.getValue("mutant.kt").getValue("cache"))
    }

    @Test
    fun `an exceptional attempt is reported and still propagates`() {
        val ticks = listOf(0L, 1_000_000_000L).iterator()
        val report = DeclarationVerificationReport(directory, ticks::next)
        assertFailsWith<IllegalStateException> {
            report.run("input.kt", "owner", null, 10) { cache ->
                cache("hit")
                cache("miss")
                error("backend failure")
            }
        }
        val row = rows().single()
        assertEquals("error", row.getValue("outcome"))
        assertEquals("hit,miss", row.getValue("cache"))
        assertEquals("1.0", row.getValue("seconds"))
    }

    private fun rows(): List<Map<String, String>> = Files.list(directory).use { files ->
        files.map { path ->
            val lines = Files.readAllLines(path)
            lines[0].split('\t').zip(lines[1].split('\t')).toMap()
        }.toList()
    }
}
