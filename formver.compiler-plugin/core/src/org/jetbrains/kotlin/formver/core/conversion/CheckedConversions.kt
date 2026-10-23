/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.formver.core.embeddings.types.GenericArrayEmbedding
import org.jetbrains.kotlin.types.AbstractTypeChecker
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

/** The JVM's representative bound; generic arguments supply no runtime evidence. */
internal fun StmtConversionContext.erasedCastTarget(type: ConeKotlinType): ConeKotlinType {
    fun erase(t: ConeKotlinType): ConeKotlinType = when (val expanded = t.fullyExpandedType(session).lowerBoundIfFlexible()) {
        is ConeDefinitelyNotNullType -> erase(expanded.original)
        is ConeTypeParameterType -> {
            val bounds = expanded.lookupTag.typeParameterSymbol.resolvedBounds.map { it.coneType }
            val erased = bounds.map(::erase)
            erased.firstOrNull {
                (it.toClassSymbol(session) as? FirRegularClassSymbol)?.classKind != ClassKind.INTERFACE
            } ?: erased.first()
        }
        else -> expanded
    }
    return erase(type).withNullability(type.canBeNull(session), session.typeContext)
}

internal fun StmtConversionContext.componentSensitiveCast(from: ConeKotlinType, target: ConeKotlinType): Boolean {
    val erased = erasedCastTarget(target)
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
): ExpEmbedding {
    val type = expression.resolvedType
    if (type !is ConeFlexibleType || type.lowerBound.isMarkedNullable || expected.canBeNull(session)) return value
    return checkedCast(value, embedType(expected))
}

/** [read], converted from [access], with the throw of an unassigned `lateinit` property visible to an enclosing catch. */
fun StmtConversionContext.lateinitReadMayThrow(access: FirPropertyAccessExpression, read: ExpEmbedding): ExpEmbedding {
    val symbol = access.calleeReference.symbol as? FirPropertySymbol
    if (symbol?.resolvedStatus?.isLateInit != true || activeCatchLabels.isEmpty()) return read
    // A read of an unassigned `lateinit` property throws once its receiver is evaluated; a read that returns gives a
    // value of the property's type.
    val value = declareAnonVar(read.type, read)
    val typed = value.variable.withInvariants(typeResolver) { proven = true }
    return blockOf(value, NonDeterministically(exceptionalExit()), typed)
}
