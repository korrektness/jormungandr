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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
class VerificationCacheOptionTest {
    @Test
    fun `cache is opt in and directory option is registered and forwarded`() {
        val configuration = CompilerConfiguration()
        val processor = FormalVerificationCommandLineProcessor()
        val option: AbstractCliOption = FormalVerificationCommandLineProcessor.VERIFICATION_CACHE_DIRECTORY_OPTION
        assertNull(configuration.get(FormalVerificationConfigurationKeys.VERIFICATION_CACHE_DIRECTORY))
        assertTrue(option in processor.pluginOptions)
        processor.processOption(option, "cache-dir", configuration)
        assertEquals("cache-dir", configuration.get(FormalVerificationConfigurationKeys.VERIFICATION_CACHE_DIRECTORY))
    }

    @Test
    fun `empty directory option is rejected`() {
        assertFailsWith<CliOptionProcessingException> {
            FormalVerificationCommandLineProcessor().processOption(
                FormalVerificationCommandLineProcessor.VERIFICATION_CACHE_DIRECTORY_OPTION as AbstractCliOption,
                " ", CompilerConfiguration(),
            )
        }
    }
}
