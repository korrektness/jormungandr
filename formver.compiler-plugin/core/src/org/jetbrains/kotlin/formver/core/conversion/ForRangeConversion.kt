/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirWhileLoop
import org.jetbrains.kotlin.fir.expressions.argument
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.types.isInt
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.core.embeddings.LabelEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.AddIntInt
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.And
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.GeIntInt
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.GtIntInt
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.LeIntInt
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.LtIntInt
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.Not
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.Or
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.SubIntInt
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

private val rangesPackage = FqName("kotlin.ranges")
private val intClassId = ClassId.topLevel(FqName("kotlin.Int"))

private val untilId = CallableId(rangesPackage, Name.identifier("until"))
private val downToId = CallableId(rangesPackage, Name.identifier("downTo"))
private val stepId = CallableId(rangesPackage, Name.identifier("step"))
private val rangeToId = CallableId(intClassId, Name.identifier("rangeTo"))
private val rangeUntilId = CallableId(intClassId, Name.identifier("rangeUntil"))

enum class IntProgressionKind { Until, RangeTo, DownTo }

/** An `Int` progression written as `first until bound`, `first ..< bound`, `first .. bound` or `first downTo bound`. */
data class IntProgressionCall(
    val first: FirExpression,
    val bound: FirExpression,
    val kind: IntProgressionKind,
    val step: FirExpression?,
)

/**
 * A `for` loop over an [IntProgressionCall], as FIR desugars it: an `<iterator>` property and a while loop whose body
 * declares [variable] from the iterator and then runs [body].
 */
data class ForRangeLoop(
    val loop: FirWhileLoop,
    val variable: FirProperty,
    val body: List<FirStatement>,
    val progression: IntProgressionCall,
)

fun FirBlock.asForRangeLoop(): ForRangeLoop? {
    val iterator = statements.getOrNull(0) as? FirProperty ?: return null
    if (iterator.name != SpecialNames.ITERATOR) return null
    val loop = statements.getOrNull(1) as? FirWhileLoop ?: return null
    if (statements.size != 2) return null
    val progression = (iterator.initializer as? FirFunctionCall)?.explicitReceiver?.intProgressionCall() ?: return null
    val variable = loop.block.statements.firstOrNull() as? FirProperty ?: return null
    val rest = loop.block.statements.drop(1)
    val body = (rest.singleOrNull() as? FirBlock)?.statements ?: rest
    return ForRangeLoop(loop, variable, body, progression)
}

private fun FirExpression.intProgressionCall(): IntProgressionCall? {
    val call = this as? FirFunctionCall ?: return null
    val receiver = call.explicitReceiver
    if (receiver == null || call.argumentList.arguments.size != 1 || !call.argument.resolvedType.isInt) return null
    val callableId = call.toResolvedCallableSymbol()?.callableId
    if (callableId == stepId) {
        return receiver.intProgressionCall()?.takeIf { it.step == null }?.copy(step = call.argument)
    }
    val kind = when (callableId) {
        untilId, rangeUntilId -> IntProgressionKind.Until
        rangeToId -> IntProgressionKind.RangeTo
        downToId -> IntProgressionKind.DownTo
        else -> null
    }
    if (kind == null || !receiver.resolvedType.isInt) return null
    return IntProgressionCall(receiver, call.argument, kind, null)
}

/**
 * Converts [forLoop] to a loop over its variable, which is declared before the loop and stepped after the body, at
 * the label that `continue` reaches. The loop head knows the variable's bounds, and the user's invariants see its
 * value for the coming iteration, or the value it stops at once the loop exits.
 */
fun StmtConversionContext.convertForRangeLoop(forLoop: ForRangeLoop): ExpEmbedding = withNewScope {
    val progression = forLoop.progression
    val prelude = mutableListOf<ExpEmbedding>()
    // The bounds and the step are evaluated once, before the loop.
    fun evaluatedOnce(exp: FirExpression): ExpEmbedding {
        if (exp is FirLiteralExpression) return convert(exp)
        val declaration = declareAnonVar(buildType { int() }, convert(exp))
        prelude.add(declaration)
        return declaration.variable
    }

    val first = evaluatedOnce(progression.first)
    val bound = evaluatedOnce(progression.bound)
    val step = progression.step?.let { evaluatedOnce(it) }
    // `step` throws on a step that is not positive.
    val positiveLiteralStep = ((progression.step as? FirLiteralExpression)?.value as? Long)?.let { it > 0 } == true
    if (step != null && !positiveLiteralStep) {
        prelude.add(If(Not(GtIntInt(step, IntLit(0))), exceptionalExit(), UnitLit, buildType { unit() }))
    }
    val declaration = declareLocalProperty(forLoop.variable.symbol, first)
    val variable = declaration.variable
    val stepAmount = step ?: IntLit(1)

    val descending = progression.kind == IntProgressionKind.DownTo
    val condition = when (progression.kind) {
        IntProgressionKind.Until -> LtIntInt(variable, bound)
        IntProgressionKind.RangeTo -> LeIntInt(variable, bound)
        IntProgressionKind.DownTo -> GeIntInt(variable, bound)
    }
    // How far the last step may take the variable past the bound.
    val reach = when (progression.kind) {
        IntProgressionKind.Until ->
            if (step == null) LeIntInt(variable, bound) else LtIntInt(variable, AddIntInt(bound, stepAmount))
        IntProgressionKind.RangeTo -> LeIntInt(variable, AddIntInt(bound, stepAmount))
        IntProgressionKind.DownTo -> GeIntInt(variable, SubIntInt(bound, stepAmount))
    }
    val started = if (descending) LeIntInt(variable, first) else LeIntInt(first, variable)
    // A loop that runs no iteration leaves the variable at `first`, which may lie beyond the bound.
    val bounds = And(started, Or(reach, EqCmp(variable, first)))
    val advance = Assign(variable, if (descending) SubIntInt(variable, stepAmount) else AddIntInt(variable, stepAmount))

    val loop = convertLoop(
        forLoop.loop, condition, extractLoopInvariants(forLoop.body), listOf(bounds), { loopHeadLabelName() },
    ) {
        val body = forLoop.body.map { convertReportingUnsupported(it) }.toBlock()
        blockOf(body, LabelExp(LabelEmbedding(continueLabelName())), advance)
    }
    (prelude + declaration + loop).toBlock()
}
