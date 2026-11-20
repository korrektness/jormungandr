/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirContextArgumentListOwner
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.formver.common.erasedCastTarget
import org.jetbrains.kotlin.formver.core.boundDispatchReceiver
import org.jetbrains.kotlin.formver.core.embeddings.types.GenericArrayEmbedding
import org.jetbrains.kotlin.types.AbstractTypeChecker
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding

/** [argument] cast to [targetType], with the exceptional path retained for statement lowering. */
fun StmtConversionContext.checkedCast(
    argument: ExpEmbedding,
    targetType: TypeEmbedding,
    checkedType: TypeEmbedding = targetType,
    nonNull: Boolean = false,
    targetName: String = targetType.toString(),
    componentSensitive: Boolean = false,
): ExpEmbedding = CheckedCast(
    argument, targetType, checkedType, nonNull, targetName,
    imperativeCheckedCast(argument, targetType), componentSensitive,
)

private fun StmtConversionContext.imperativeCheckedCast(argument: ExpEmbedding, targetType: TypeEmbedding): ExpEmbedding {
    val from = argument.type.pretype as? ClassTypeEmbedding
    val to = targetType.pretype as? ClassTypeEmbedding
    val safeType = argument.type.pretype == targetType.pretype ||
            from != null && to != null && typeResolver.isSubtype(from, to)
    if (safeType && (targetType.isNullable || !argument.type.isNullable)) {
        return Cast(argument, targetType).withInvariants(typeResolver) { proven = true; access = true }
    }
    val saved = declareAnonVar(argument.type, argument)
    val cast = Cast(saved.variable, targetType).withInvariants(typeResolver) {
        proven = true
        access = true
    }
    return blockOf(saved, If(Is(saved.variable, targetType), cast, exceptionalExit(), targetType))
}

internal fun StmtConversionContext.componentSensitiveCast(from: ConeKotlinType, target: ConeKotlinType): Boolean {
    val erased = target.erasedCastTarget(session)
    if (erased.classId != GenericArrayEmbedding.classId) return false
    val element = (erased as ConeClassLikeType).typeArguments.single()
    val componentChecked = element !is ConeStarProjection &&
            !(element.type?.isAny == true && element.type!!.canBeNull(session))
    return componentChecked && !AbstractTypeChecker.isSubtypeOf(
        session.typeContext,
        from.withNullability(false, session.typeContext),
        target.withNullability(false, session.typeContext),
    )
}

/**
 * [value], converted from [expression], where Kotlin expects [expected]: a platform-typed value reaching a type that
 * excludes null is null-checked there.
 */
fun StmtConversionContext.platformNullCheck(
    expression: FirExpression,
    value: ExpEmbedding,
    expected: ConeKotlinType,
): ExpEmbedding = if (needsPlatformNullCheck(expression, expected)) checkedCast(value, embedType(expected)) else value

/** Whether [expression] is platform-typed, may be null, and reaches [expected], which excludes null. */
fun StmtConversionContext.needsPlatformNullCheck(expression: FirExpression, expected: ConeKotlinType): Boolean {
    val type = expression.resolvedType
    return type is ConeFlexibleType && !type.lowerBound.isMarkedNullable && !expected.canBeNull(session)
}

/** [expression] converted where Kotlin expects [expected], or where it expects any value when [expected] is null. */
fun StmtConversionContext.convertExpecting(expression: FirExpression, expected: ConeKotlinType?): ExpEmbedding {
    val value = convert(expression)
    return if (expected == null) value else platformNullCheck(expression, value, expected)
}

/** The type [symbol] declares for each receiver and argument that this call or access passes it. */
fun FirQualifiedAccessExpression.declaredArgumentTypes(symbol: FirCallableSymbol<*>): Map<FirExpression, ConeKotlinType> =
    buildMap {
        boundDispatchReceiver?.let { receiver -> symbol.dispatchReceiverType?.let { put(receiver, it) } }
        extensionReceiver?.let { receiver -> symbol.resolvedReceiverType?.let { put(receiver, it) } }
        (this@declaredArgumentTypes as? FirContextArgumentListOwner)?.contextArguments.orEmpty()
            .zip(symbol.contextParameterSymbols).forEach { (argument, parameter) -> put(argument, parameter.resolvedReturnType) }
        (this@declaredArgumentTypes as? FirCall)?.resolvedArgumentMapping?.forEach { (argument, parameter) ->
            if (argument !is FirVarargArgumentsExpression) put(argument, parameter.returnTypeRef.coneType)
        }
    }

/** [argument], a receiver or argument of [access], converted where the callable [access] resolves to declares its type. */
fun StmtConversionContext.convertArgumentOf(access: FirElement, argument: FirExpression): ExpEmbedding {
    val qualified = access as? FirQualifiedAccessExpression
    val callable = qualified?.toResolvedCallableSymbol()
    return convertExpecting(argument, callable?.let { qualified.declaredArgumentTypes(it)[argument] })
}

/**
 * An operand of one call or write: [value], converted from [expression], whose null check against [deferredCheck] waits
 * until every operand is evaluated. A null check against a primitive type is the unboxing of [expression], which
 * happens as it is evaluated, so it is part of [value].
 */
data class CallOperand(val expression: FirExpression?, val value: ExpEmbedding, val deferredCheck: ConeKotlinType? = null)

/** [expression] as an operand where Kotlin expects [expected], or any value when [expected] is null. */
fun StmtConversionContext.operand(expression: FirExpression, expected: ConeKotlinType?): CallOperand {
    val value = convert(expression)
    return when {
        expected == null || !needsPlatformNullCheck(expression, expected) -> CallOperand(expression, value)
        expected.isPrimitive -> CallOperand(expression, checkedCast(value, embedType(expected)))
        else -> CallOperand(expression, value, expected)
    }
}

/** [expression], a receiver or argument of [access], as an operand where the callable [access] resolves to declares its type. */
fun StmtConversionContext.operandOf(access: FirElement, expression: FirExpression): CallOperand {
    val qualified = access as? FirQualifiedAccessExpression
    val callable = qualified?.toResolvedCallableSymbol()
    return operand(expression, callable?.let { qualified.declaredArgumentTypes(it)[expression] })
}

/** The declarations that evaluate the operands of one call or write, and the values that pass the operands on. */
class EvaluatedOperands(val declarations: List<Declare>, val values: List<ExpEmbedding>) {
    /** [use] of [values], after [declarations]. */
    fun around(use: (List<ExpEmbedding>) -> ExpEmbedding): ExpEmbedding =
        if (declarations.isEmpty()) use(values) else Block {
            addAll(declarations)
            add(use(values))
        }
}

/**
 * [operands], the operands of one call or write in the order Kotlin evaluates them. Kotlin null-checks a non-primitive
 * platform-typed operand when the callee receives it or the write dereferences it, after evaluating every operand. When
 * an operand defers such a check, each operand is evaluated first, as [argumentDeclaration] does, and its value performs
 * the check.
 */
fun StmtConversionContext.evaluateOperands(operands: List<CallOperand>): EvaluatedOperands {
    if (operands.all { it.deferredCheck == null }) return EvaluatedOperands(emptyList(), operands.map { it.value })
    val declarations = mutableListOf<Declare>()
    val values = operands.map { operand ->
        val (declaration, usage) = argumentDeclaration(operand.value, operand.value.type)
        declarations.addIfNotNull(declaration)
        val expected = operand.deferredCheck
        if (expected == null) usage else checkedCast(usage, embedType(expected))
    }
    return EvaluatedOperands(declarations, values)
}

/** [read], converted from [access], with the throw of an unassigned `lateinit` property visible to an enclosing catch. */
fun StmtConversionContext.lateinitReadMayThrow(access: FirPropertyAccessExpression, read: ExpEmbedding): ExpEmbedding {
    val symbol = access.calleeReference.symbol as? FirPropertySymbol
    if (symbol?.resolvedStatus?.isLateInit != true || !mayThrow()) return read
    // A read of an unassigned `lateinit` property throws once its receiver is evaluated; a read that returns gives a
    // value of the property's type.
    val value = declareAnonVar(read.type, read)
    val typed = value.variable.withInvariants(typeResolver) { proven = true }
    return blockOf(value, NonDeterministically(exceptionalExit()), typed)
}
