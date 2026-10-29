package org.jetbrains.kotlin.formver.common

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.security.MessageDigest
import kotlin.test.*

class PortCorrespondenceTest {
    private val data = File("formver.compiler-plugin/testData")
    private fun checker(action: (PortDeviationChecker) -> Unit) = PortDeviationChecker().use(action)

    @Test
    fun `vendored assignment sites and source hash are pinned`() = checker { checker ->
        val upstream = data.resolve("upstream/kotlin-v2.3.0/ArrayDeque.kt").readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(upstream).joinToString("") { "%02x".format(it) }
        assertEquals("1369874f80ec51c6c484276aacc53fe4a401870b1de0e907cc1217278d6c40ae", hash)
        val sites = checker.assignmentSites(upstream.toString(Charsets.UTF_8))
        assertEquals(
            listOf("constructor()", "constructor(Collection<E>)", "constructor(Collection<E>)", "constructor(Int)", "fun copyElements(Int)", "fun ensureCapacity(Int)")
                .map { "class(ArrayDeque) / $it" },
            sites.map { it.owner },
        )
    }

    @Test
    fun `search mutants change only their wrapped guards and fail the intended clauses`() = checker { checker ->
        val directory = data.resolve("programs/ports/arraydeque")
        val port = directory.resolve("ArrayDeque.kt").readText()
        val cases = listOf(
            Triple("indexOf", "indexOfPre80390", "head >= tail"),
            Triple("lastIndexOf", "lastIndexOfPre80661", "head > tail"),
        )
        for ((original, mutant, guard) in cases) {
            val source = directory.resolve("$mutant.kt").readText()
            val function = checker.normalizedFunction(source, mutant)
            assertTrue(function.contains("@TrustedElementEquality"), mutant)
            val changes = checker.strippedDiff(checker.normalizedFunction(port, original), function)
                .lines().filter { it.startsWith("-") || it.startsWith("+") }
            assertEquals(
                listOf("-        } else if (isNotEmpty() && head >= tail) {", "+        } else if ($guard) {"),
                changes, mutant,
            )
            val diagnostics = directory.resolve("$mutant.viper.diag.txt").readText()
            val clauses = Regex("(?m)^Failing clause: `([^`]+)`").findAll(diagnostics).map { it.groupValues[1] }.toSet()
            val intended = if (original == "indexOf") "result < size" else
                "(result == -1) == !exists<Int> { i -> (0 <= i && i < size) && element == element..."
            assertEquals(setOf(intended), clauses, mutant)
            val errors = diagnostics.lineSequence().count { it.contains(": error:") }
            val failures = Regex("(?m)^Failing clause:").findAll(diagnostics).count()
            assertEquals(failures, errors, mutant)
        }
        val untrusted = checker.normalizedFunction(port, "indexOfUntrusted")
        assertFalse(untrusted.contains("@TrustedElementEquality"))
        assertEquals("", checker.strippedDiff(checker.normalizedFunction(port, "indexOf"), untrusted))
        val untrustedLines = checker.functionLines(port, "indexOfUntrusted")
        val clauses = Regex("(?m)^Failing clause: `([^`]+)` \\((\\d+):\\d+\\)")
            .findAll(directory.resolve("ArrayDeque.viper.diag.txt").readText())
            .filter { it.groupValues[2].toInt() in untrustedLines }.map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf(
                "(result == -1) == !exists<Int> { i -> (0 <= i && i < size) && element == element...",
                "(result >= 0) implies (element == elementAt(result))",
                "forAll<Int> { i -> (0 <= i && i < result) implies (element != elementAt(i)) }",
            ), clauses,
        )
    }

    @Test
    fun `real port matches labelled deviations and retained assignment sites`() {
        val port = data.resolve("programs/ports/arraydeque/ArrayDeque.kt")
        assumeTrue(port.exists(), "ArrayDeque port is absent")
        checker { checker ->
            val upstream = data.resolve("upstream/kotlin-v2.3.0/ArrayDeque.kt").readText()
            val source = checker.portSource(port.readText())
            assertEquals(setOf("class(ArrayDeque) / constructor()", "class(ArrayDeque) / constructor(Int)"),
                checker.assignmentSites(source).map { it.owner }.filter { "constructor(" in it }.toSet())
            checker.checkPort(upstream, source, port.resolveSibling("ArrayDeque.port.diff").readText())
        }
    }
}
