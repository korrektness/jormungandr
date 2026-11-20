/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.argument
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.types.isInt
import org.jetbrains.kotlin.fir.types.isLong
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.And
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.LeIntInt
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.LtIntInt
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.formver.core.purity.isPure
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val rangesPackage = FqName("kotlin.ranges")
private val intClassId = ClassId.topLevel(FqName("kotlin.Int"))
private val longClassId = ClassId.topLevel(FqName("kotlin.Long"))

private fun FirExpression.integerKind(): IntegerKind? = when {
    resolvedType.isInt -> IntegerKind.Int
    resolvedType.isLong -> IntegerKind.Long
    else -> null
}

/** An integer operand of a range operation with its Kotlin width. */
private data class Operand(val expression: FirExpression, val kind: IntegerKind)

private fun FirExpression.operand(): Operand? = integerKind()?.let { Operand(this, it) }

/** `low..high`, `low..<high` or `low until high` on `Int` and `Long` ends; [inclusive] tells whether [high] is in it. */
private data class RangeLiteral(val low: Operand, val high: Operand, val inclusive: Boolean)

private fun FirExpression.rangeLiteral(): RangeLiteral? {
    val call = this as? FirFunctionCall ?: return null
    if (call.arguments.size != 1) return null
    val low = call.explicitReceiver?.operand() ?: return null
    val high = call.argument.operand() ?: return null
    val id = call.toResolvedCallableSymbol()?.callableId ?: return null
    val name = id.callableName.asString()
    val inclusive = when {
        id.classId == intClassId || id.classId == longClassId -> when (name) {
            "rangeTo" -> true
            "rangeUntil" -> false
            else -> return null
        }
        id.classId == null && id.packageName == rangesPackage && name == "until" -> false
        else -> return null
    }
    return RangeLiteral(low, high, inclusive)
}

/** `a < b` or `a <= b` on operands of any integer widths. */
private fun compare(a: ExpEmbedding, aKind: IntegerKind, b: ExpEmbedding, bKind: IntegerKind, strict: Boolean): ExpEmbedding {
    if (aKind == IntegerKind.Int && bKind == IntegerKind.Int) return if (strict) LtIntInt(a, b) else LeIntInt(a, b)
    val comparison = IntegerOperators.binary.getValue(IntegerOperators.Binary(aKind, bKind, "compareTo", IntegerKind.Int))(a, b)
    return if (strict) LtIntInt(comparison, IntLit(0)) else LeIntInt(comparison, IntLit(0))
}

/**
 * Converts [operands] in order and passes their values to [body]. When one of them has side effects, each is stored
 * in a variable first, so each is evaluated once and in order however often [body] reads it.
 */
private fun StmtConversionContext.withOperands(
    operands: List<FirExpression>,
    body: (List<ExpEmbedding>) -> ExpEmbedding,
): ExpEmbedding {
    val converted = operands.map { convert(it) }
    if (converted.all { it.isPure() }) return body(converted)
    val declarations = operands.zip(converted).map { (operand, value) ->
        if (operand is FirLiteralExpression) null else declareAnonVar(value.type, value)
    }
    return Block {
        declarations.forEach { if (it != null) add(it) }
        add(body(converted.zip(declarations).map { (value, declaration) -> declaration?.variable ?: value }))
    }
}

/**
 * Converts `x in r` on a range literal `r` (see [rangeLiteral]) with an `Int` or `Long` element to comparisons with
 * its ends, evaluating the ends and then the element, as Kotlin does. `x !in r` is the negation of such a call.
 */
fun StmtConversionContext.convertRangeMembership(call: FirFunctionCall): ExpEmbedding? {
    val id = call.toResolvedCallableSymbol()?.callableId ?: return null
    if (id.callableName.asString() != "contains" || id.packageName != rangesPackage || call.arguments.size != 1) return null
    val range = call.explicitReceiver?.rangeLiteral() ?: return null
    val element = call.argument.operand() ?: return null
    return withOperands(listOf(range.low.expression, range.high.expression, element.expression)) { (low, high, x) ->
        And(
            compare(low, range.low.kind, x, element.kind, strict = false),
            compare(x, element.kind, high, range.high.kind, strict = !range.inclusive),
        )
    }
}

/**
 * Converts `coerceAtLeast`, `coerceAtMost` and the two-bound `coerceIn` on `Int` and `Long` to their definitions.
 * `coerceIn` throws when the minimum exceeds the maximum.
 */
fun StmtConversionContext.convertCoercion(call: FirFunctionCall): ExpEmbedding? {
    val id = call.toResolvedCallableSymbol()?.callableId ?: return null
    if (id.packageName != rangesPackage || id.classId != null) return null
    val receiver = call.explicitReceiver?.operand() ?: return null
    val bounds = call.arguments.map { it.operand() ?: return null }
    if (bounds.any { it.kind != receiver.kind }) return null
    val kind = receiver.kind
    val type = buildType { existing(kind.pretype) }
    fun less(a: ExpEmbedding, b: ExpEmbedding) = compare(a, kind, b, kind, strict = true)
    fun atLeast(value: ExpEmbedding, min: ExpEmbedding) = If(less(value, min), min, value, type)
    fun atMost(value: ExpEmbedding, max: ExpEmbedding) = If(less(max, value), max, value, type)
    val operands = listOf(receiver.expression) + bounds.map { it.expression }
    return when (id.callableName.asString()) {
        "coerceAtLeast" -> if (bounds.size != 1) null else withOperands(operands) { (value, min) -> atLeast(value, min) }
        "coerceAtMost" -> if (bounds.size != 1) null else withOperands(operands) { (value, max) -> atMost(value, max) }
        "coerceIn" -> if (bounds.size != 2) null else withOperands(operands) { (value, min, max) ->
            val clamped = atMost(atLeast(value, min), max)
            if (bounds.literalValues()?.let { (min, max) -> min <= max } == true) clamped
            else Block {
                add(If(less(max, min), exceptionalExit(), UnitLit, buildType { unit() }))
                add(clamped)
            }
        }
        else -> null
    }
}

private fun List<Operand>.literalValues(): List<Long>? = map { ((it.expression as? FirLiteralExpression)?.value as? Long) ?: return null }
