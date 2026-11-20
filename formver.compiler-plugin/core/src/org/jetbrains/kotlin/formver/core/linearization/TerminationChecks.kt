/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.core.asPosition
import org.jetbrains.kotlin.formver.core.conversion.lexicographicDecrease
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.core.embeddings.asInfo
import org.jetbrains.kotlin.formver.core.embeddings.expression.LoopVariant
import org.jetbrains.kotlin.formver.viper.ast.Declaration
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Method
import org.jetbrains.kotlin.formver.viper.ast.Position
import org.jetbrains.kotlin.formver.viper.ast.Stmt
import org.jetbrains.kotlin.formver.viper.SymbolicName

/** A method without a body whose precondition, [decreases], each call checks. */
class TerminationCheckMethod(
    name: SymbolicName,
    override val formalArgs: List<Declaration.LocalVarDecl>,
    decreases: Exp,
) : Method(name) {
    override val formalReturns: List<Declaration.LocalVarDecl> = emptyList()
    override val pres: List<Exp> = listOf(decreases)
}

/**
 * The checks that a termination-checked method makes at the calls of its recursion cycle. [measure] is its measure.
 * [checks] has, for each function of the cycle, a [TerminationCheckMethod] that takes the callee's arguments and then
 * the caller's measure, and requires the callee's measure to be smaller.
 */
class MethodTermination(private val measure: List<Exp>, private val checks: Map<FirFunctionSymbol<*>, Method>) {
    /** The check that a call of [callee] with [args] decreases the measure, or `null` when [callee] is outside the cycle. */
    fun check(callee: FirFunctionSymbol<*>?, args: List<Exp>, pos: Position): Stmt? {
        val method = callee?.let { checks[it.unwrapFakeOverrides()] } ?: return null
        return method.toMethodCall(args + measure.map { Exp.Old(it) }, emptyList(), pos,
            SourceRole.Termination(KotlinCondition.Termination).asInfo)
    }
}

/** Saves the values of the variant at the start of an iteration. */
fun LoopVariant.save(ctx: LinearizationContext) {
    saved.zip(measures).forEach { (variable, measure) ->
        ctx.addDeclaration(variable.toLocalVarDecl())
        ctx.addStatement {
            Stmt.assign(variable.toLocalVarUse(), measure.pureToViper(toBuiltin = true, ctx.typeResolver), ctx.source.asPosition)
        }
    }
}

/**
 * The check, at a jump back to the loop head, that the variant is smaller than at the start of the iteration. A failure
 * is reported at the loop.
 */
fun LoopVariant.check(ctx: LinearizationContext): Stmt = Stmt.Assert(
    lexicographicDecrease(measures.map { it.pureToViper(toBuiltin = true, ctx.typeResolver) }, saved.map { it.toLocalVarUse() }),
    source.asPosition, SourceRole.Termination(KotlinCondition.LoopTermination, source.asPosition).asInfo,
)
