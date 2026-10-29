package org.jetbrains.kotlin.formver.viper

import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SiliconLocatorTest {
    @TempDir
    lateinit var directory: Path

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `absolute and searched solver paths require executable files`() {
        val file = Files.writeString(directory.resolve("solver"), "fixture; never executed")
        Files.setPosixFilePermissions(file, setOf(OWNER_READ, OWNER_WRITE))
        assertFailsWith<VerifierUnavailableException> { SiliconFrontend.locateZ3(file.toString(), "") }
        assertFailsWith<VerifierUnavailableException> { SiliconFrontend.locateZ3("solver", directory.toString()) }

        Files.setPosixFilePermissions(file, setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE))
        assertEquals(file.toString(), SiliconFrontend.locateZ3(file.toString(), ""))
        assertEquals(file.toString(), SiliconFrontend.locateZ3("solver", directory.toString()))
    }
}
