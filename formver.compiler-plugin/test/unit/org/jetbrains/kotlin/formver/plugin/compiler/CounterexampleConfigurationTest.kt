package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.formver.cli.FormalVerificationCommandLineProcessor
import org.jetbrains.kotlin.formver.cli.FormalVerificationConfigurationKeys
import kotlin.test.*

class CounterexampleConfigurationTest {
    @Test
    fun `counterexample option preserves explicit booleans and rejects invalid values`() {
        val processor = FormalVerificationCommandLineProcessor()
        val option: org.jetbrains.kotlin.compiler.plugin.AbstractCliOption = FormalVerificationCommandLineProcessor.COUNTEREXAMPLES_OPTION
        val configuration = CompilerConfiguration()
        assertNull(configuration.get(FormalVerificationConfigurationKeys.COUNTEREXAMPLES))
        listOf(true, false).forEach { enabled ->
            processor.processOption(option, enabled.toString(), configuration)
            assertEquals(enabled, configuration.get(FormalVerificationConfigurationKeys.COUNTEREXAMPLES))
        }
        assertFailsWith<CliOptionProcessingException> { processor.processOption(option, "yes", configuration) }
        assertEquals(false, configuration.get(FormalVerificationConfigurationKeys.COUNTEREXAMPLES))
    }
}
