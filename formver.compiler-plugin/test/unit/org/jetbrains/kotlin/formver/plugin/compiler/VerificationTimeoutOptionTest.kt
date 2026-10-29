package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.formver.cli.FormalVerificationCommandLineProcessor
import org.jetbrains.kotlin.formver.cli.FormalVerificationConfigurationKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
class VerificationTimeoutOptionTest {
    private val option: AbstractCliOption = FormalVerificationCommandLineProcessor.VERIFICATION_TIMEOUT_OPTION

    @Test
    fun `timeout option is registered and forwarded in seconds`() {
        val configuration = CompilerConfiguration()
        val processor = FormalVerificationCommandLineProcessor()
        assertTrue(option in processor.pluginOptions)
        processor.processOption(option, "0", configuration)
        assertEquals(0, configuration.get(FormalVerificationConfigurationKeys.VERIFICATION_TIMEOUT))
        processor.processOption(option, "45", configuration)
        assertEquals(45, configuration.get(FormalVerificationConfigurationKeys.VERIFICATION_TIMEOUT))
    }

    @Test
    fun `negative and non-numeric timeouts are rejected`() {
        for (value in listOf("-1", "5s", "")) {
            assertFailsWith<CliOptionProcessingException>(value) {
                FormalVerificationCommandLineProcessor().processOption(option, value, CompilerConfiguration())
            }
        }
    }
}
