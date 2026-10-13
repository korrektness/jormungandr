package org.jetbrains.kotlin.formver.common

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URLClassLoader
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

    private fun loader(vararg roots: File) = URLClassLoader(roots.map { it.toURI().toURL() }.toTypedArray(), null)

    @Test
    fun `bundled specs from every index are extracted with their contents`() {
        val first = resourceRoot("first", "# comment\nspecs/a.kt\n\n", mapOf("specs/a.kt" to "fun a() {}"))
        val second = resourceRoot("second", "specs/b.kt", mapOf("specs/b.kt" to "fun b() {}"))

        val files = ExternalSpecSources.extractBundled(loader(first, second), temp.resolve("cache"))

        assertEquals(listOf("fun a() {}", "fun b() {}"), files.map { it.readText() })
        assertEquals(listOf("a.kt", "b.kt"), files.map { it.name })
    }

    @Test
    fun `extraction reuses unchanged specs and separates changed ones`() {
        val cache = temp.resolve("cache")
        val original = resourceRoot("original", "specs/a.kt", mapOf("specs/a.kt" to "fun a() {}"))
        val changed = resourceRoot("changed", "specs/a.kt", mapOf("specs/a.kt" to "fun a(): Int = 0"))

        val first = ExternalSpecSources.extractBundled(loader(original), cache).single()
        val again = ExternalSpecSources.extractBundled(loader(original), cache).single()
        val other = ExternalSpecSources.extractBundled(loader(changed), cache).single()

        assertEquals(first, again)
        assertNotEquals(first, other)
        assertEquals("fun a() {}", first.readText())
        assertEquals("fun a(): Int = 0", other.readText())
    }

    @Test
    fun `no index means no bundled specs`() {
        assertEquals(emptyList(), ExternalSpecSources.extractBundled(loader(), temp.resolve("cache")))
    }

    @Test
    fun `a listed spec that is missing fails`() {
        val root = resourceRoot("broken", "specs/missing.kt", emptyMap())

        assertFailsWith<IllegalStateException> { ExternalSpecSources.extractBundled(loader(root), temp.resolve("cache")) }
    }

    @Test
    fun `a listed resource that is not Kotlin fails`() {
        val root = resourceRoot("notKotlin", "specs/a.txt", mapOf("specs/a.txt" to ""))

        assertFailsWith<IllegalArgumentException> { ExternalSpecSources.extractBundled(loader(root), temp.resolve("cache")) }
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
