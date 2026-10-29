package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.math.BigInteger
import java.net.URLClassLoader
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Random
import kotlin.test.*

class DatetimeArithmeticTest {
    private val data = File("formver.compiler-plugin/testData")
    private val upstream = data.resolve("upstream/kotlinx-datetime-d79ac8a/math.kt")
    private val target = data.resolve("diagnostics/targets/datetime")
    private val archive = data.resolve("experiments/datetime")
    private val roots = listOf("clampToInt", "safeMultiplyOrClamp")
    private fun hash(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun selected(checker: PortDeviationChecker, source: String, names: List<String>) =
        names.joinToString("\n\n") { name -> checker.declarations(source).single { it.first == name }.second }

    private fun correspondence(checker: PortDeviationChecker, saturation: String, clamp: String) {
        assertEquals(listOf("productFitsLong") + roots, checker.declarations(saturation).map { it.first })
        assertEquals(listOf("productFitsLong", "clampToInt", "clampBoundaryClients", "wrongPositiveClamp"),
            checker.declarations(clamp).map { it.first })
        val original = selected(checker, upstream.readText(), roots)
        val port = selected(checker, saturation, roots)
        checker.check(original, port, archive.resolve("LongSaturation.port.diff").readText())
        assertEquals(checker.strip(selected(checker, saturation, listOf("clampToInt", "productFitsLong"))),
            checker.strip(selected(checker, clamp, listOf("clampToInt", "productFitsLong"))))
        assertEquals("25e7cbbf6930a4de26a8bbdc36e85b8a0c22c049a1c4d4134f7b2fb5a66391b5", hash(checker.strip(selected(checker, saturation, listOf("productFitsLong")))))
    }

    @Test
    fun `frozen roots match approved shell and specification additions`() = PortDeviationChecker().use { checker ->
        assertEquals("539156a1d1dc4a71db0fd2eb749aaeac4a34f2ec1bddf0a4cb7cb07cfb661ce5", hash(upstream.readText()))
        val saturation = archive.resolve("LongSaturation.kt").readText()
        val clamp = target.resolve("LongClamp.kt").readText()
        correspondence(checker, saturation, clamp)
        for (changed in listOf(
            saturation.replace("return total", "return 0L"),
            saturation + "\ninternal fun replacement() = 0L\n",
            saturation.replace("a <= Long.MAX_VALUE / b", "a < Long.MAX_VALUE / b"),
        )) assertFails { correspondence(checker, changed, clamp) }
    }

    @Test
    fun `compiled frozen and adapted bodies agree with independent BigInteger oracle`() = PortDeviationChecker().use { checker ->
        val directory = Files.createTempDirectory("datetime-oracle-").toFile()
        try {
            val frozen = directory.resolve("Frozen.kt").apply {
                writeText("package oracle.frozen\n" + selected(checker, upstream.readText(), roots))
            }
            val port = directory.resolve("Port.kt").apply {
                writeText("package oracle.port\n" + checker.strip(selected(checker,
                    archive.resolve("LongSaturation.kt").readText(), roots + "productFitsLong")))
            }
            val output = ByteArrayOutputStream()
            val stdlib = File(Unit::class.java.protectionDomain.codeSource.location.toURI())
            val result = PrintStream(output).use { stream -> K2JVMCompiler().exec(stream,
                "-no-stdlib", "-no-reflect", "-classpath", stdlib.path, "-d", directory.path, frozen.path, port.path) }
            assertEquals(ExitCode.OK, result, output.toString())
            URLClassLoader(arrayOf(directory.toURI().toURL()), javaClass.classLoader).use(::checkOracle)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun checkOracle(loader: ClassLoader) {
        val types = listOf("oracle.frozen.FrozenKt", "oracle.port.PortKt").map(loader::loadClass)
        val products = types.map { it.getDeclaredMethod("safeMultiplyOrClamp", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType) }
        val clamps = types.map { it.getDeclaredMethod("clampToInt", Long::class.javaPrimitiveType) }
        val fits = types.last().getDeclaredMethod("productFitsLong", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
        val low = BigInteger.valueOf(Long.MIN_VALUE)
        val high = BigInteger.valueOf(Long.MAX_VALUE)
        val endpoints = listOf(Long.MIN_VALUE, Long.MIN_VALUE + 1, Long.MIN_VALUE / 2 - 1,
            Long.MIN_VALUE / 2, Long.MIN_VALUE / 2 + 1, -2147483649L, -2147483648L, -2147483647L, -2L, -1L, 0L,
            1L, 2L, 2147483646L, 2147483647L, 2147483648L, Long.MAX_VALUE / 2 - 1, Long.MAX_VALUE / 2,
            Long.MAX_VALUE / 2 + 1, Long.MAX_VALUE - 1, Long.MAX_VALUE)
        val random = Random(0)
        val pairs = endpoints.flatMap { a -> endpoints.filter { it != 0L }.map { b -> a to b } } +
            List(512) { random.nextLong() to random.nextLong().let { if (it == 0L) 1L else it } }
        for ((a, b) in pairs) {
            val product = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b))
            for (method in products) assertEquals(product.max(low).min(high).toLong(), method.invoke(null, a, b), "$a * $b")
            assertEquals(product >= low && product <= high, fits.invoke(null, a, b), "fits($a, $b)")
            val expected = BigInteger.valueOf(a).max(BigInteger.valueOf(Int.MIN_VALUE.toLong()))
                .min(BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt()
            for (method in clamps) assertEquals(expected, method.invoke(null, a), "clamp($a)")
        }
    }
}
