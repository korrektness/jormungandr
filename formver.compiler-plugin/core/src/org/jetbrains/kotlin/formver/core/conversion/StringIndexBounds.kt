/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.contracts.description.LogicOperationKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirBooleanOperatorExpression
import org.jetbrains.kotlin.fir.expressions.FirComparisonExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirOperation
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.references.toResolvedValueParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.isString
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.common.externalFunctionId
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.WithPosition
import org.jetbrains.kotlin.formver.core.functionCallArguments
import org.jetbrains.kotlin.name.Name

private val indexedStringMembers = setOf("substring", "subSequence")

/**
 * The name of the `kotlin.text` member with a `String` receiver and index parameters that this external function model
 * stands for, or null when it models no such member. The precondition conjuncts of such a model that compare an index parameter state the
 * member's index bounds (see [stringIndexBound]).
 */
private fun FirFunctionSymbol<*>.modelledStringMember(session: FirSession): String? {
    val target = getAnnotationByClassId(externalFunctionId, session)
        ?.getStringArgument(Name.identifier("target"), session) ?: return null
    if (!target.startsWith("kotlin.text.") || receiverParameterSymbol?.resolvedType?.isString != true) return null
    return target.substringAfterLast('.').takeIf { it in indexedStringMembers }
}

/**
 * The preconditions [statements] of [block] state, converted as [collectInvariants] converts them. When [model] stands
 * for a `String` member and the statements declare no locals, each conjunct is a precondition of its own, and one that
 * states an index bound of the member carries that bound as its [SourceRole.StringIndexCheck].
 */
fun StmtConversionContext.collectPreconditions(
    model: FirFunctionSymbol<*>, block: FirBlock, statements: List<FirStatement>,
): List<ExpEmbedding> {
    val member = model.modelledStringMember(session)
    if (member == null || statements.any { it is FirProperty }) return collectInvariants(block, statements)
    return statements.flatMap { it.conjuncts() }.map { conjunct ->
        val precondition = collectInvariants(block, listOf(conjunct)).single()
        val bound = stringIndexBound(member, conjunct) ?: return@map precondition
        val source = conjunct.source ?: throw SnaktInternalException(null, "A precondition conjunct of $member has no Kotlin source.")
        WithPosition(precondition, source, SourceRole.StringIndexCheck(bound))
    }
}

/** The operands of the `&&` chain [this] is, in order; [this] alone when it is no conjunction. */
private fun FirStatement.conjuncts(): List<FirStatement> =
    (this as? FirBooleanOperatorExpression)?.takeIf { it.kind == LogicOperationKind.AND }
        ?.let { it.leftOperand.conjuncts() + it.rightOperand.conjuncts() }
        ?: listOf(this)

/**
 * The bound on an index parameter of the `String` member [member] that [conjunct] states: `0 <= i` bounds `i` below
 * zero, `i < length` and `i <= length` bound it by the receiver's length, and `i <= j` bounds the start index `i` by
 * the end index `j`. Null when [conjunct] has none of these shapes.
 */
private fun stringIndexBound(member: String, conjunct: FirStatement): KotlinCondition.StringIndex? {
    val comparison = conjunct as? FirComparisonExpression ?: return null
    val operands = comparison.compareToCall.functionCallArguments
    if (operands.size != 2) return null
    val (left, right) = operands
    val (low, high, strict) = when (comparison.operation) {
        FirOperation.LT -> Triple(left, right, true)
        FirOperation.LT_EQ -> Triple(left, right, false)
        FirOperation.GT -> Triple(right, left, true)
        FirOperation.GT_EQ -> Triple(right, left, false)
        else -> return null
    }
    val highParameter = high.parameterName()
    if (!strict && low.isZero() && highParameter != null) {
        return KotlinCondition.StringIndex(member, highParameter, KotlinCondition.StringBound.NEGATIVE)
    }
    val lowParameter = low.parameterName() ?: return null
    val bound = when {
        high.isReceiverLength() ->
            if (strict) KotlinCondition.StringBound.NOT_BELOW_LENGTH else KotlinCondition.StringBound.ABOVE_LENGTH
        !strict && highParameter != null -> KotlinCondition.StringBound.START_AFTER_END
        else -> return null
    }
    return KotlinCondition.StringIndex(member, lowParameter, bound)
}

private fun FirExpression.parameterName(): String? =
    (this as? FirQualifiedAccessExpression)?.calleeReference?.toResolvedValueParameterSymbol()?.name?.asString()

private fun FirExpression.isZero(): Boolean = ((this as? FirLiteralExpression)?.value as? Number)?.toLong() == 0L

private fun FirExpression.isReceiverLength(): Boolean {
    val access = this as? FirPropertyAccessExpression ?: return false
    return access.calleeReference.name.asString() == "length" &&
        (access.dispatchReceiver ?: access.extensionReceiver) is FirThisReceiverExpression
}
