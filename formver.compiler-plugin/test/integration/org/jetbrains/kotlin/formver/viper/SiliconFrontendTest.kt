package org.jetbrains.kotlin.formver.viper

import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import viper.silver.ast.*
import viper.silver.plugin.standard.refute.Refute
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SiliconFrontendTest {
    private val pos = `NoPosition$`.`MODULE$`
    private val info = `NoInfo$`.`MODULE$`
    private val trafos = `NoTrafos$`.`MODULE$`
    private val int = `Int$`.`MODULE$`
    private val ref = `Ref$`.`MODULE$`
    private val bool = `Bool$`.`MODULE$`
    private val emptyProgram = Program(emptySeq(), emptySeq(), emptySeq(), emptySeq(), emptySeq(), emptySeq(), pos, info, trafos)

    /**
     * `function f(x: Int): Int ensures ret_1 > 0 { x }`, where `ret_1` is undeclared, and `method m() { assert false }`.
     * Silicon aborts on the undeclared variable and reports no verification error, so `m`'s failure would vanish too.
     */
    private val undeclaredVariableProgram: Program = run {
        val post = GtCmp(LocalVar("ret_1", int, pos, info, trafos), IntLit(0.toScalaBigInt(), pos, info, trafos), pos, info, trafos)
        val function = Function(
            "f", seqOf(LocalVarDecl("x", int, pos, info, trafos)), int, emptySeq(), seqOf<Exp>(post),
            LocalVar("x", int, pos, info, trafos).toScalaOption<Exp>(), pos, info, trafos,
        )
        val body = Seqn(seqOf<Stmt>(Assert(FalseLit(pos, info, trafos), pos, info, trafos)), emptySeq(), pos, info, trafos)
        val method = Method("m", emptySeq(), emptySeq(), emptySeq(), emptySeq(), body.toScalaOption(), pos, info, trafos)
        Program(emptySeq(), emptySeq(), seqOf(function), emptySeq(), seqOf(method), emptySeq(), pos, info, trafos)
    }

    @Test
    fun `a program with an undeclared variable fails the consistency check`() {
        assertFailsWith<InvalidViperProgramException> { SiliconFrontend.checkConsistency(undeclaredVariableProgram) }
    }

    @Test
    fun `verifying a program with an undeclared variable throws instead of reporting nothing`() {
        val failures = mutableListOf<VerifierError>()
        SiliconFrontend(SiliconFrontend.locateZ3()).use { verifier ->
            assertFailsWith<InvalidViperProgramException> { verifier.verify(undeclaredVariableProgram) { failures.add(it) } }
        }
        assertEquals(emptyList(), failures)
    }

    /**
     * `function f(x: Ref): Bool requires false` and
     * `method m() { inhale forall x: Ref :: { f(x) } true; refute false; assert false }`.
     * Evaluating the trigger `f(x)` fails a precondition, which Silicon counts as an error without reporting it.
     */
    private val hiddenTriggerFailureProgram: Program = run {
        val f = Function(
            "f", seqOf(LocalVarDecl("x", ref, pos, info, trafos)), bool, seqOf<Exp>(FalseLit(pos, info, trafos)),
            emptySeq(), scala.Option.empty(), pos, info, trafos,
        )
        val x = LocalVar("x", ref, pos, info, trafos)
        val trigger = Trigger(seqOf<Exp>(FuncApp("f", seqOf<Exp>(x), pos, info, bool, trafos)), pos, info, trafos)
        val quantifier = Forall(
            seqOf(LocalVarDecl("x", ref, pos, info, trafos)), seqOf(trigger), TrueLit(pos, info, trafos), pos, info, trafos,
        )
        val body = Seqn(
            seqOf<Stmt>(
                Inhale(quantifier, pos, info, trafos),
                Refute(FalseLit(pos, info, trafos), pos, info, trafos),
                Assert(FalseLit(pos, info, trafos), pos, info, trafos),
            ),
            emptySeq(), pos, info, trafos,
        )
        val method = Method("m", emptySeq(), emptySeq(), emptySeq(), emptySeq(), body.toScalaOption(), pos, info, trafos)
        Program(emptySeq(), emptySeq(), seqOf(f), emptySeq(), seqOf(method), emptySeq(), pos, info, trafos)
    }

    @Test
    fun `statements after a refute are checked after an unreported failure`() {
        val failures = mutableListOf<VerifierError>()
        SiliconFrontend(SiliconFrontend.locateZ3()).use { verifier ->
            verifier.verify(hiddenTriggerFailureProgram) { failures.add(it) }
        }
        assertTrue(failures.any { (it as? VerificationError)?.result?.offendingNode() is Assert }, "failures: $failures")
    }

    /**
     * `method m(h_0: Int, ..., h_10: Int)` that inhales that each of 11 pigeons sits in one of 10 holes and that no two
     * pigeons share a hole, then asserts `false`. Refuting the pigeonhole principle takes Z3 exponential time in one
     * query: with 9 holes Z3 4.8.7 runs for over two minutes.
     *
     * Silicon produces and evaluates in continuation-passing style, so its stack grows with every conjunct it inhales,
     * however the conjunction is nested. Integer holes keep the conjuncts few.
     */
    private val pigeonholeProgram: Program = run {
        val pigeons = 11
        val holes = pigeons - 1
        fun hole(pigeon: Int): Exp = LocalVar("h_$pigeon", int, pos, info, trafos)
        fun lit(value: Int): Exp = IntLit(value.toScalaBigInt(), pos, info, trafos)
        val inRange = (0 until pigeons).flatMap { pigeon ->
            listOf(LeCmp(lit(0), hole(pigeon), pos, info, trafos), LtCmp(hole(pigeon), lit(holes), pos, info, trafos))
        }
        val apart = (0 until pigeons).flatMap { first ->
            (first + 1 until pigeons).map { second -> NeCmp(hole(first), hole(second), pos, info, trafos) }
        }
        val constraints = (inRange + apart).reduce<Exp, Exp> { left, right -> And(left, right, pos, info, trafos) }
        val body = Seqn(
            seqOf<Stmt>(Inhale(constraints, pos, info, trafos), Assert(FalseLit(pos, info, trafos), pos, info, trafos)),
            emptySeq(), pos, info, trafos,
        )
        val params = (0 until pigeons).map { LocalVarDecl("h_$it", int, pos, info, trafos) }
        val method = Method("m", params.toScalaSeq(), emptySeq(), emptySeq(), emptySeq(), body.toScalaOption(), pos, info, trafos)
        Program(emptySeq(), emptySeq(), emptySeq(), emptySeq(), seqOf(method), emptySeq(), pos, info, trafos)
    }

    @Test
    fun `a program that exceeds the timeout throws and the frontend verifies the next program`() {
        val failures = mutableListOf<VerifierError>()
        // The limit must also cover verifying the second program after the backend restarts, on a loaded host.
        SiliconFrontend(SiliconFrontend.locateZ3(), timeoutSeconds = 10).use { verifier ->
            val timeout = assertFailsWith<VerificationTimeoutException> { verifier.verify(pigeonholeProgram) { failures.add(it) } }
            assertEquals(10, timeout.seconds)
            verifier.verify(hiddenTriggerFailureProgram) { failures.add(it) }
        }
        assertTrue(failures.any { (it as? VerificationError)?.result?.offendingNode() is Assert }, "failures: $failures")
    }

    @Test
    fun `cache status distinguishes cold verification misses and hits without caching failures`() {
        val directory = Files.createTempDirectory("silicon-status-")
        try {
            val statuses = mutableListOf<String>()
            val failures = mutableListOf<VerifierError>()
            SiliconFrontend(SiliconFrontend.locateZ3()).use {
                it.verify(emptyProgram, statuses::add) { failure -> failures.add(failure) }
            }
            SiliconFrontend(SiliconFrontend.locateZ3(), cacheDirectory = directory).use {
                it.verify(emptyProgram, statuses::add) { failure -> failures.add(failure) }
                it.verify(emptyProgram, statuses::add) { failure -> failures.add(failure) }
                repeat(2) { _ ->
                    it.verify(hiddenTriggerFailureProgram, statuses::add) { failure -> failures.add(failure) }
                }
            }
            assertEquals(listOf("off", "miss", "hit", "miss", "miss"), statuses)
            assertEquals(2, failures.count { (it as? VerificationError)?.result?.offendingNode() is Assert })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `cache status is reported when verification exits exceptionally`() {
        val directory = Files.createTempDirectory("silicon-status-")
        try {
            val z3 = SiliconFrontend.locateZ3()
            val cancellation = CancellationException()
            val statuses = mutableListOf<String>()
            SiliconFrontend(z3, cacheDirectory = directory).use { verifier ->
                verifier.verify(emptyProgram) { throw AssertionError(it) }
                val thrown = assertFailsWith<CancellationException> {
                    verifier.verify(hiddenTriggerFailureProgram, statuses::add) { throw cancellation }
                }
                assertSame(cancellation, thrown)
            }
            val reportFile = Files.createTempFile(directory, "report", null)
            SiliconFrontend(z3, cacheDirectory = directory, cacheReportDirectory = reportFile).use { verifier ->
                val failure = assertFailsWith<FileAlreadyExistsException> {
                    verifier.verify(emptyProgram, statuses::add) { throw AssertionError(it) }
                }
                assertEquals(reportFile, java.nio.file.Path.of(failure.file))
            }
            assertEquals(listOf("miss", "unavailable"), statuses)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
