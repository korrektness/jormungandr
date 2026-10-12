package org.jetbrains.kotlin.formver.viper

import org.jetbrains.kotlin.formver.viper.errors.VerifierError
import viper.silver.ast.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SiliconFrontendTest {
    private val pos = `NoPosition$`.`MODULE$`
    private val info = `NoInfo$`.`MODULE$`
    private val trafos = `NoTrafos$`.`MODULE$`
    private val int = `Int$`.`MODULE$`

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
}
