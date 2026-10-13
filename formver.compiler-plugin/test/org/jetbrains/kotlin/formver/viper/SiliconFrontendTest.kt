package org.jetbrains.kotlin.formver.viper

import org.jetbrains.kotlin.formver.viper.errors.VerificationError
import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import viper.silver.ast.*
import viper.silver.plugin.standard.refute.Refute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SiliconFrontendTest {
    private val pos = `NoPosition$`.`MODULE$`
    private val info = `NoInfo$`.`MODULE$`
    private val trafos = `NoTrafos$`.`MODULE$`
    private val int = `Int$`.`MODULE$`
    private val ref = `Ref$`.`MODULE$`
    private val bool = `Bool$`.`MODULE$`

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
}
