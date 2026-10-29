package org.jetbrains.kotlin.formver.gradle

import org.jetbrains.kotlin.formver.common.FormalVerificationPluginNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VerificationWorkersOptionTest {
    @Test
    fun `DSL forwards explicit worker counts and rejects negative counts`() {
        val extension = FormVerExtension()
        for (workers in listOf(2, 0, 7)) {
            extension.verificationWorkers(workers)
            val option = extension.verificationWorkersOption()
            assertEquals(FormalVerificationPluginNames.VERIFICATION_WORKERS_OPTION_NAME, option.key)
            assertEquals(workers.toString(), option.value)
        }
        assertFailsWith<IllegalArgumentException> { extension.verificationWorkers(-1) }
        assertEquals("7", extension.verificationWorkersOption().value)
    }
}
