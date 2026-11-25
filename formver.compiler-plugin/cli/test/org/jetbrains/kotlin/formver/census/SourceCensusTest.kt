package org.jetbrains.kotlin.formver.census

import org.jetbrains.kotlin.formver.common.source.ProofSourceIndex
import org.jetbrains.kotlin.formver.common.source.SourceIdentity
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SourceCensusTest {
    @TempDir
    lateinit var work: Path

    @Test
    fun `proof index rejects an invalid compilation ID before indexing any input`() {
        assertFailsWith<IllegalArgumentException> { ProofSourceIndex(":project:unrelatedTask", work) }
    }

    @Test
    fun `invalid UTF-8 retains the input path and decoding cause`() {
        val source = work.resolve("Malformed.kt")
        Files.write(source, byteArrayOf(0xc3.toByte()))
        val failure = assertFailsWith<IllegalArgumentException> {
            SourceIdentity.fromParsedFile(":compileKotlin", work, source, "")
        }
        assertIs<CharacterCodingException>(failure.cause)
        assertTrue(source.toRealPath().toString() in assertNotNull(failure.message))
    }

    @Test
    fun `keys survive relocation and manifest order with reused files and basenames`() {
        val source = "fun f() = 1\nfun f(x: Int) = x"
        val inputs = listOf(
            Input(":app:compileKotlin", "app/nonstandard/Input.kt", source),
            Input(":app:compileKotlin", "app/build/generated/Input.kt", source),
            Input(":other:compileKotlin", "app/nonstandard/Input.kt", source),
            Input(":app:compileKotlin", "app/Empty.kt", "typealias Name = String"),
        )
        val rows = census(work.resolve("original"), inputs)
        assertEquals(rows, census(work.resolve("relocated"), inputs.reversed()))
        assertEquals(rows.size, rows.map { it.take(8) }.toSet().size)
        val files = rows.filter { it[4] == "FILE" }
        assertEquals(inputs.map { it.identity() }.toSet(), files.map { it.take(4) }.toSet())
        val bodies = rows.filter { it[4] != "FILE" }.groupBy { it.take(4) }
        assertEquals(inputs.take(3).associate { it.identity() to listOf("1", "x") },
            bodies.mapValues { (_, regions) -> regions.map { it.anchor(source) } })
    }

    @Test
    fun `BOM and mixed line endings change digest but not UTF-16 anchors or parents`() {
        val text = "// 🐍\nfun outer() = { val local = { \"🦉\" }; local() }\n"
        val plain = Input(":compileKotlin", "Input.kt", text)
        val formatted = plain.copy(text = "\uFEFF" + text.replaceFirst("\n", "\r\n").trimEnd('\n') + "\r")
        val canonical = census(work.resolve("lf"), listOf(plain))
        val normalized = census(work.resolve("bom"), listOf(formatted))
        assertEquals(setOf(formatted.digest()), normalized.map { it[3] }.toSet())
        assertNotEquals(canonical.first()[3], normalized.first()[3])
        assertEquals(canonical.map { it.drop(4) }, normalized.map { it.drop(4) })

        val file = normalized.single { it[4] == "FILE" }
        val function = normalized.single { it[4] == "FUNCTION" }
        val initializer = normalized.single { it[4] == "PROPERTY_INITIALIZER" }
        val (outer, inner) = normalized.filter { it[4] == "LAMBDA" }.sortedBy { it[5].toInt() }
        assertEquals(text, file.anchor(text))
        assertEquals("{ val local = { \"🦉\" }; local() }", function.anchor(text))
        assertEquals("{ \"🦉\" }", initializer.anchor(text))
        assertEquals(function.subList(5, 7), outer.subList(5, 7))
        assertEquals(initializer.subList(5, 7), inner.subList(5, 7))
        assertEquals(file.reference(), function[8])
        assertEquals(function.reference(), outer[8])
        assertEquals(outer.reference(), initializer[8])
        assertEquals(initializer.reference(), inner[8])
    }

    @Test
    fun `construction and callable regions do not require proof targets`() {
        val text = """
            interface Contract { fun absent(): Int }
            object Singleton
            enum class Choice { FIRST }
            expect enum class PlatformChoice { PLATFORM }
            expect class Platform(valPlaceholder: Int) {
                constructor()
                class Nested { constructor(flag: Boolean) }
                fun f(x: Any = run { class Local(val n: Int); Local(1) })
            }
            class Plain { constructor() }
            open class Parent(x: Int)
            class Child(val x: Int = 5) : Parent(x), Contract by delegate {
                constructor() : this(1) { println(x) }
                init { require(x > 0) }
                val lazyValue by lazy { x }
                var prop = 0
                    get() = field + 1
                    set(value) { field = value }
                fun outer() { fun local() = 1 }
            }
        """.trimIndent()
        val regions = census(work, listOf(Input(":app:compileKotlin", "Input.kt", text))).filter { it[4] != "FILE" }
        assertEquals(
            mapOf(
                "PRIMARY_CONSTRUCTOR" to listOf("Singleton", "Choice", "(val n: Int)", "(x: Int)", "(val x: Int = 5)"),
                "ENUM_ENTRY" to listOf("FIRST"),
                "SUPERTYPE_CALL" to listOf("Parent(x)"),
                "SUPERTYPE_DELEGATE" to listOf("delegate"),
                "SECONDARY_CONSTRUCTOR" to listOf("constructor", "{ println(x) }"),
                "CONSTRUCTOR_DELEGATION" to listOf("this(1)"),
                "DEFAULT_ARGUMENT" to listOf("run { class Local(val n: Int); Local(1) }", "5"),
                "INIT_BLOCK" to listOf("{ require(x > 0) }"),
                "PROPERTY_DELEGATE" to listOf("lazy { x }"),
                "LAMBDA" to listOf("{ class Local(val n: Int); Local(1) }", "{ x }"),
                "PROPERTY_INITIALIZER" to listOf("0"),
                "GETTER" to listOf("field + 1"),
                "SETTER" to listOf("{ field = value }"),
                "FUNCTION" to listOf("{ fun local() = 1 }", "1"),
            ),
            regions.groupBy({ it[4] }, { it.anchor(text) }),
        )
        val (outer, local) = regions.filter { it[4] == "FUNCTION" }.sortedBy { it[5].toInt() }
        assertEquals(outer.reference(), local[8])
    }

    @Test
    fun `compile-time values do not add runtime regions`() {
        val text = """
            const val CONSTANT = 10
            val ordinary = 20
            annotation class Metadata(val value: Int = 30)
            fun withDefault(value: Int = 40) = value
        """.trimIndent()
        val regions = census(work, listOf(Input(":app:compileKotlin", "Input.kt", text))).filter { it[4] != "FILE" }
        assertEquals(
            mapOf("PROPERTY_INITIALIZER" to listOf("20"), "DEFAULT_ARGUMENT" to listOf("40"), "FUNCTION" to listOf("value")),
            regions.groupBy({ it[4] }, { it.anchor(text) }),
        )
    }

    @Test
    fun `invalid input identity retains the manifest and the offending row`() {
        val input = Input(":app:unrelatedTask", "generated/Example.kt", "fun example() = 1")
        val row = input.identity().joinToString("\t")
        val manifest = work.resolve("inputs.tsv").apply { writeText("census-inputs-v1\n$row\n") }
        val failure = assertFailsWith<IllegalArgumentException> {
            writeSourceCensus(work, manifest, work.resolve("census.tsv"))
        }
        val message = assertNotNull(failure.message)
        assertTrue(manifest.toString() in message)
        assertTrue(row in message)
    }

    @Test
    fun `source changed after reconciliation cannot replace a complete census`() {
        val input = Input(":compileKotlin", "Input.kt", "fun f() = 1")
        val complete = census(work, listOf(input))
        work.resolve(input.path).writeText("fun f() = 2")
        assertFailsWith<IllegalArgumentException> {
            writeSourceCensus(work, work.resolve("manifest.tsv"), work.resolve("census.tsv"))
        }
        assertEquals(complete, work.resolve("census.tsv").readLines().drop(1).map { it.split('\t') })
    }

    private data class Input(val compilation: String, val path: String, val text: String) {
        fun digest(): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).toHexString()
        fun identity(): List<String> = listOf(compilation, "checkout", path, digest())
    }

    private fun census(checkout: Path, inputs: List<Input>): List<List<String>> {
        for (input in inputs) {
            val source = checkout.resolve(input.path)
            source.parent.createDirectories()
            source.writeText(input.text)
        }
        val manifest = checkout.resolve("manifest.tsv")
        manifest.writeText("census-inputs-v1\n" + inputs.joinToString("") { it.identity().joinToString("\t") + "\n" })
        val output = checkout.resolve("census.tsv")
        writeSourceCensus(checkout, manifest, output)
        return output.readLines().drop(1).map { it.split('\t') }
    }

    private fun List<String>.anchor(text: String): String = text.substring(this[5].toInt(), this[6].toInt())
    private fun List<String>.reference(): String = subList(4, 8).joinToString(":")
}
