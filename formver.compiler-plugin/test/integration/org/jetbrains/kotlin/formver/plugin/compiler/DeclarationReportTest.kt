package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.formver.common.DeclarationReport
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

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
    """.trimIndent()

    private fun location(type: Class<*>) = Paths.get(type.protectionDomain.codeSource.location.toURI()).toString()

    /**
     * Compiles [source] in a separate JVM with the bundled plugin jar, outside the test facade, and returns the report
     * rows by declaration and program.
     */
    private fun report(): Map<String, List<String>> {
        val file = temp.resolve("Census.kt").apply { writeText(source) }
        val report = temp.resolve("report.tsv")
        val classpath = listOf(location(KotlinVersion::class.java), System.getProperty("annotationsRuntime.classpath"))
        val repository = File(System.getProperty("idea.home.path")).canonicalFile
        val compilerClasspath = System.getProperty("java.class.path").split(File.pathSeparator)
            .filterNot { File(it).canonicalFile.startsWith(repository) }
        val process = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/java").path, "-Xss30m",
            "-cp", compilerClasspath.joinToString(File.pathSeparator), K2JVMCompiler::class.java.name,
            "-no-stdlib", "-no-reflect", "-Xreport-all-warnings",
            "-classpath", classpath.joinToString(File.pathSeparator), "-d", temp.resolve("classes").path,
            "-Xplugin=" + System.getProperty("formver.pluginJar"),
            "-P", "plugin:org.jetbrains.kotlin.formver:verification_targets_selection=no_targets",
            "-P", "plugin:org.jetbrains.kotlin.formver:declaration_report=$report",
            file.path,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        assertEquals(ExitCode.COMPILATION_ERROR.code, exit, output)
        val lines = report.readLines()
        assertEquals(DeclarationReport.COLUMNS.joinToString("\t"), lines.first())
        val rows = lines.drop(1).map { it.split("\t") }
        val keys = rows.map { "${it[2]}.${it[3]}" }
        assertEquals(keys.distinct(), keys)
        return rows.zip(keys).associate { (row, key) ->
            assertEquals(file.path, row[0])
            key to listOf(row[1]) + row.subList(4, 10).filterIndexed { i, _ -> i != 3 }
        }
    }

    @Test
    fun `the report gives each function its conversion outcome`() {
        assertEquals(
            mapOf(
                "one.body" to listOf("4", "converted", "", "", "not_selected", ""),
                "increment.body" to listOf("5", "converted", "", "", "not_selected", ""),
                "supplier.body" to listOf("6", "converted", "", "", "not_selected", ""),
                "supplier.lambda0" to listOf("6", "converted", "", "", "not_selected", ""),
                "half.body" to listOf("7", "stopped", "UNSUPPORTED_FEATURE", "KOTLIN_CONSTRUCT", "", ""),
                "ignored.body" to listOf("8", "excluded", "", "NEVER_CONVERT", "", ""),
                "impure.body" to listOf("9", "stopped", "PURITY_VIOLATION", "FUNCTION_BODY", "", ""),
            ),
            report(),
        )
    }
}
