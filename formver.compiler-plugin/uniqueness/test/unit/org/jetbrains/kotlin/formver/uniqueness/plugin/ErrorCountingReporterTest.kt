package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.KtOffsetsOnlySourceElement
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticReporterWithContext
import org.jetbrains.kotlin.diagnostics.KtOffsetsOnlySimpleDiagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorCountingReporterTest {
    private val reporterClass = Class.forName("org.jetbrains.kotlin.formver.uniqueness.plugin.ErrorCountingReporter")
    private val reporter = reporterClass.getDeclaredConstructor().apply { isAccessible = true }
        .newInstance() as DiagnosticReporter
    private val count = reporterClass.getDeclaredField("errorCount").apply { isAccessible = true }
    private val source = KtOffsetsOnlySourceElement(0, 1)
    private val context = KtDiagnosticReporterWithContext(reporter, LanguageVersionSettingsImpl.DEFAULT).at(source, "test.kt")

    private fun diagnostic(severity: Severity): KtOffsetsOnlySimpleDiagnostic {
        val factory = FirErrors.NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY
        return KtOffsetsOnlySimpleDiagnostic(source, severity, factory, factory.defaultPositioningStrategy)
    }

    @Test
    fun errorsWrapAtTheIntBoundary() {
        count.setInt(reporter, Int.MAX_VALUE - 1)
        val error = diagnostic(Severity.ERROR)
        reporter.report(error, context)
        assertEquals(Int.MAX_VALUE, count.getInt(reporter))
        reporter.report(error, context)
        assertEquals(Int.MIN_VALUE, count.getInt(reporter))
        reporter.report(error, context)
        assertEquals(Int.MIN_VALUE + 1, count.getInt(reporter))
    }

    @Test
    fun onlyErrorsAdvanceTheCountThroughZero() {
        count.setInt(reporter, -1)
        for (severity in Severity.entries - Severity.ERROR) {
            reporter.report(diagnostic(severity), context)
            assertEquals(-1, count.getInt(reporter), severity.name)
        }
        reporter.report(null, context)
        assertEquals(-1, count.getInt(reporter))

        val error = diagnostic(Severity.ERROR)
        reporter.report(error, context)
        assertEquals(0, count.getInt(reporter))
        reporter.report(error, context)
        assertEquals(1, count.getInt(reporter))
    }
}
