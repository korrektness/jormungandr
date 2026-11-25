package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.formver.common.DeclarationReport
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Paths
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DeclarationReportTest {
    @TempDir
    lateinit var temp: File

    private val source = """
        import org.jetbrains.kotlin.formver.plugin.NeverConvert
        import org.jetbrains.kotlin.formver.plugin.Pure

        fun one(): Int = 1
        fun increment(x: Int): Int = x + 1
        fun supplier(): () -> Int = { 1 }
        fun half(): Double = 0.5
        @NeverConvert fun ignored(): Int = 2
        @Pure fun impure(): Int = increment(1)
        class Counter(val start: Int)
    """.trimIndent()

    private fun location(type: Class<*>) = Paths.get(type.protectionDomain.codeSource.location.toURI()).toString()

    private fun compile(checkout: File, files: List<File>, vararg options: String): Pair<Int, String> {
        val classpath = listOf(location(KotlinVersion::class.java), System.getProperty("annotationsRuntime.classpath"))
        val repository = File(System.getProperty("idea.home.path")).canonicalFile
        val compilerClasspath = System.getProperty("java.class.path").split(File.pathSeparator)
            .filterNot { File(it).canonicalFile.startsWith(repository) }
        val builder = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/java").path, "-Xss30m",
            "-cp", compilerClasspath.joinToString(File.pathSeparator), K2JVMCompiler::class.java.name,
            "-no-stdlib", "-no-reflect", "-Xreport-all-warnings", "-Xuse-fir-lt=false",
            "-classpath", classpath.joinToString(File.pathSeparator), "-d", checkout.resolve("classes").path,
            "-Xplugin=" + System.getProperty("formver.pluginJar"),
            *options.flatMap { listOf("-P", "plugin:org.jetbrains.kotlin.formver:$it") }.toTypedArray(),
            *files.map { it.path }.toTypedArray(),
        ).redirectErrorStream(true)
        builder.environment()["XDG_CACHE_HOME"] = temp.resolve("compiler-cache").absolutePath
        builder.environment()["Z3_EXE"] = temp.resolve("missing-z3").absolutePath
        val process = builder.start()
        val output = process.inputStream.bufferedReader().readText()
        return process.waitFor() to output
    }

    private data class Report(val outcomes: Map<String, List<String>>, val ids: Map<String, List<String>>)

    private fun report(
        source: String = this.source,
        verification: String = "no_targets",
        checkout: File = temp,
    ): Report {
        checkout.mkdirs()
        val file = checkout.resolve("src/Census.kt").apply {
            parentFile.mkdirs()
            writeText(source)
        }
        val generated = checkout.resolve("generated/Census.kt").apply {
            parentFile.mkdirs()
            writeText("\uFEFF// 🐍\r\n")
        }
        val inputManifest = checkout.resolve("compiler.tsv")
        val report = checkout.resolve("report.tsv")
        val (exit, output) = compile(checkout, listOf(file, generated),
            "verification_targets_selection=$verification", "declaration_report=$report", "input_manifest=$inputManifest",
            "proof_checkout=$checkout", "proof_compilation=:test:compileKotlin",
        )
        assertEquals(ExitCode.COMPILATION_ERROR.code, exit, output)
        val sources = inputManifest.readLines().drop(1).map { it.split('\t') }.filter { it[0] == "source" }
        val normalized = source.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val expectedInputs = mapOf(
            file.canonicalPath to listOf(digest(file.readBytes()), digest(normalized.toByteArray())),
            generated.canonicalPath to listOf(digest(generated.readBytes()), digest("// 🐍\n".toByteArray())),
        )
        assertEquals(expectedInputs.size, sources.size)
        assertEquals(expectedInputs, sources.associate { it[1] to it.drop(2) })
        val lines = report.readLines()
        assertEquals(DeclarationReport.COLUMNS.joinToString("\t"), lines.first())
        val rows = lines.drop(1).map { it.split("\t") }
        val keys = rows.map { "${it[2]}.${it[3]}" }
        assertEquals(keys.distinct(), keys)
        val outcomes = rows.zip(keys).associate { (row, key) ->
            assertEquals(file.canonicalFile, File(row[0]).canonicalFile)
            key to listOf(row[1]) + row.subList(4, 10).filterIndexed { i, _ -> i != 3 }
        }
        return Report(outcomes, keys.zip(rows.map { it.drop(12) }).toMap())
    }

    @Test
    fun `input-only recording rejects proof options before creating output`() {
        val file = temp.resolve("Census.kt").apply { writeText("fun one() = 1") }
        val manifest = temp.resolve("compiler.tsv")
        val report = temp.resolve("report.tsv")
        val (failed, diagnostics) = compile(temp, listOf(file), "inputs_only=true", "input_manifest=$manifest",
            "declaration_report=$report", "proof_checkout=$temp", "proof_compilation=:test:compileKotlin",
        )
        assertNotEquals(ExitCode.OK.code, failed, diagnostics)
        assertFalse(manifest.exists())
        val (recorded, output) = compile(temp, listOf(file), "inputs_only=true", "input_manifest=$manifest")
        assertEquals(ExitCode.OK.code, recorded, output)
        assertTrue(manifest.isFile)
    }

    @Test
    fun `callable program identities survive checkout relocation but bind exact bytes and normalized spans`() {
        val plain = "// 🐍\n$source\n"
        val formatted = "\uFEFF" + plain.replace("\n", "\r\n")
        val original = report(formatted, checkout = temp.resolve("original")).ids
        assertEquals(original, report(formatted, checkout = temp.resolve("relocated")).ids)
        assertNotEquals(original.getValue("supplier.body")[0], original.getValue("supplier.lambda0")[0])
        val plainIds = report(plain, checkout = temp.resolve("plain")).ids
        assertNotEquals(plainIds.getValue("supplier.body")[0], original.getValue("supplier.body")[0])
        for ((label, kind) in listOf("supplier.body" to "FUNCTION", "supplier.lambda0" to "LAMBDA")) {
            val anchor = "{ 1 }"
            val start = plain.indexOf(anchor)
            val raw = digest(formatted.toByteArray())
            val reference = "census-region-v1\t:test:compileKotlin\tcheckout\tsrc/Census.kt\t$raw\t$kind:$start:${start + anchor.length}:0"
            assertEquals(digest(reference.toByteArray()), original.getValue(label)[1])
        }
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()

    @Test
    fun `the report distinguishes conversion from verification for functions and classes`() {
        assertEquals(
            mapOf(
                "one.body" to listOf("4", "converted", "", "", "not_selected", ""),
                "increment.body" to listOf("5", "converted", "", "", "not_selected", ""),
                "supplier.body" to listOf("6", "converted", "", "", "not_selected", ""),
                "supplier.lambda0" to listOf("6", "converted", "", "", "not_selected", ""),
                "half.body" to listOf("7", "stopped", "UNSUPPORTED_FEATURE", "KOTLIN_CONSTRUCT", "", ""),
                "ignored.body" to listOf("8", "excluded", "", "NEVER_CONVERT", "", ""),
                "impure.body" to listOf("9", "stopped", "PURITY_VIOLATION", "FUNCTION_BODY", "", ""),
                "Counter.class" to listOf("10", "converted", "", "", "not_selected", ""),
            ),
            report().outcomes,
        )
    }

    @Test
    fun `every attempted program records solver unavailability`() {
        val rows = report(
            """
            class First(val n: Int)
            class Second(val n: Int)
            fun one(): Int = 1
            """.trimIndent(),
            verification = "all_targets",
        )
        val unavailable = listOf("error", "SOLVER_UNAVAILABLE")
        assertEquals(
            mapOf("First.class" to unavailable, "Second.class" to unavailable, "one.body" to unavailable),
            rows.outcomes.mapValues { (_, row) -> listOf(row[4], row[5].substringBefore(':')) },
        )
    }
}
