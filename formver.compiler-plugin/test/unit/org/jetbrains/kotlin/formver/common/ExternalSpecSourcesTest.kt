package org.jetbrains.kotlin.formver.common

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ExternalSpecSourcesTest {
    @TempDir
    lateinit var temp: File

    private fun resourceRoot(name: String, index: String, sources: Map<String, String>): File {
        val root = temp.resolve(name)
        root.resolve(ExternalSpecSources.BUNDLED_INDEX_RESOURCE).apply { parentFile.mkdirs() }.writeText(index)
        for ((path, text) in sources) root.resolve(path).apply { parentFile.mkdirs() }.writeText(text)
        return root
    }

    private fun jar(name: String, entries: Map<String, String>): File {
        val jar = temp.resolve("$name.jar")
        JarOutputStream(jar.outputStream()).use { out ->
            for ((path, text) in entries) {
                out.putNextEntry(JarEntry(path))
                out.write(text.toByteArray())
                out.closeEntry()
            }
        }
        return jar
    }

    @Test
    fun `bundled specs are extracted with their contents`() {
        val root = resourceRoot("root", "# comment\nspecs/a.kt\n\nspecs/b.kt", mapOf("specs/a.kt" to "fun a() {}", "specs/b.kt" to "fun b() {}"))

        val files = ExternalSpecSources.extractBundled(root, temp.resolve("cache"))

        assertEquals(listOf("fun a() {}", "fun b() {}"), files.map { it.readText() })
        assertEquals(listOf("a.kt", "b.kt"), files.map { it.name })
    }

    @Test
    fun `bundled specs are read from a jar`() {
        val jar = jar("specs", mapOf(ExternalSpecSources.BUNDLED_INDEX_RESOURCE to "specs/a.kt", "specs/a.kt" to "fun a() {}"))

        val file = ExternalSpecSources.extractBundled(jar, temp.resolve("cache")).single()

        assertEquals("fun a() {}", file.readText())
    }

    @Test
    fun `extraction reuses unchanged specs and separates changed ones`() {
        val cache = temp.resolve("cache")
        val original = resourceRoot("original", "specs/a.kt", mapOf("specs/a.kt" to "fun a() {}"))
        val changed = resourceRoot("changed", "specs/a.kt", mapOf("specs/a.kt" to "fun a(): Int = 0"))

        val first = ExternalSpecSources.extractBundled(original, cache).single()
        val again = ExternalSpecSources.extractBundled(original, cache).single()
        val other = ExternalSpecSources.extractBundled(changed, cache).single()

        assertEquals(first, again)
        assertNotEquals(first, other)
        assertEquals("fun a() {}", first.readText())
        assertEquals("fun a(): Int = 0", other.readText())
    }

    @Test
    fun `a tampered extraction is restored from the bundled source`() {
        val cache = temp.resolve("cache")
        val root = resourceRoot("root", "specs/a.kt", mapOf("specs/a.kt" to "fun a() {}"))
        val extracted = ExternalSpecSources.extractBundled(root, cache).single()
        extracted.writeText("fun a(): Int = 1")

        val again = ExternalSpecSources.extractBundled(root, cache).single()

        assertEquals(extracted, again)
        assertEquals("fun a() {}", again.readText())
    }

    @Test
    fun `an empty index means no bundled specs`() {
        val root = resourceRoot("empty", "# none\n", emptyMap())

        assertEquals(emptyList(), ExternalSpecSources.extractBundled(root, temp.resolve("cache")))
    }

    @Test
    fun `a location without an index fails`() {
        assertFailsWith<IllegalStateException> {
            ExternalSpecSources.extractBundled(temp.resolve("bare").apply { mkdirs() }, temp.resolve("cache"))
        }
    }

    @Test
    fun `a listed spec that is missing fails`() {
        val root = resourceRoot("broken", "specs/missing.kt", emptyMap())

        assertFailsWith<IllegalStateException> { ExternalSpecSources.extractBundled(root, temp.resolve("cache")) }
    }

    @Test
    fun `a listed resource that is not Kotlin fails`() {
        val root = resourceRoot("notKotlin", "specs/a.txt", mapOf("specs/a.txt" to ""))

        assertFailsWith<IllegalArgumentException> { ExternalSpecSources.extractBundled(root, temp.resolve("cache")) }
    }

    @Test
    fun `a listed path that leaves the extraction directory fails`() {
        val root = resourceRoot("escaping", "specs/../../a.kt\n/a.kt", mapOf("a.kt" to "fun a() {}"))

        assertFailsWith<IllegalArgumentException> { ExternalSpecSources.extractBundled(root, temp.resolve("cache")) }
        assertFalse(temp.resolve("a.kt").exists())
    }

    @Test
    fun `membership matches every spelling of a spec path`() {
        val spec = temp.resolve("dir/spec.kt").apply { parentFile.mkdirs() }.apply { writeText("") }
        val other = temp.resolve("dir/other.kt").apply { writeText("") }
        val specs = ExternalSpecSources(listOf(spec, File(temp, "dir/../dir/spec.kt")))

        assertEquals(listOf(spec.canonicalFile), specs.files)
        assertTrue(specs.isExternalSpec(File(temp, "dir/./spec.kt").path))
        assertFalse(specs.isExternalSpec(other.path))
        assertFalse(specs.isExternalSpec(null as String?))
    }
}
