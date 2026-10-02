/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.intrinsics.plugin

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.isMarkedOrFlexiblyNullable
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * The class ids that name `StringBuilder`: the JVM class, and the class on the other platforms.
 */
val stringBuilderClassIds = setOf(
    ClassId.topLevel(FqName("java.lang.StringBuilder")),
    ClassId.topLevel(FqName("kotlin.text.StringBuilder")),
)

private val stringBuilderClearId = CallableId(FqName("kotlin.text"), Name.identifier("clear"))

/**
 * An operation on a `StringBuilder` that is lowered directly instead of as a call. It moves neither its receiver nor
 * its argument. When [aliasesReceiver], it returns its receiver, and its result denotes the receiver's path.
 */
enum class StringBuilderIntrinsic(val aliasesReceiver: Boolean) {
    LENGTH(false),
    APPEND_CHAR(true),
    APPEND_STRING(true),
    TO_STRING(false),
    CLEAR(true),
}

fun ConeKotlinType.isStringBuilder(session: FirSession): Boolean =
    fullyExpandedType(session).lowerBoundIfFlexible().classId in stringBuilderClassIds

/**
 * The `StringBuilder` intrinsic [this] access performs, or `null` when it is not one.
 */
fun FirQualifiedAccessExpression.stringBuilderIntrinsic(session: FirSession): StringBuilderIntrinsic? {
    val receiver = stringBuilderReceiver ?: return null
    if (!receiver.resolvedType.isStringBuilder(session)) return null
    val name = (calleeReference as? FirNamedReference)?.name?.asString() ?: return null
    return when (this) {
        is FirPropertyAccessExpression -> StringBuilderIntrinsic.LENGTH.takeIf { name == "length" }
        is FirFunctionCall -> when {
            name == "toString" && arguments.isEmpty() && receiver == dispatchReceiver -> StringBuilderIntrinsic.TO_STRING
            toResolvedCallableSymbol()?.callableId == stringBuilderClearId -> StringBuilderIntrinsic.CLEAR
            name == "append" && receiver == dispatchReceiver -> arguments.singleOrNull()?.appendIntrinsic(session)
            else -> null
        }
        else -> null
    }
}

/**
 * The `append` intrinsic for this argument: a `Char`, or a `String` that is not nullable. Java appends `"null"` for a
 * null string, which the intrinsic does not model.
 */
private fun FirExpression.appendIntrinsic(session: FirSession): StringBuilderIntrinsic? {
    val type = resolvedType.fullyExpandedType(session)
    if (type.isMarkedOrFlexiblyNullable) return null
    return when (type.lowerBoundIfFlexible().classId) {
        StandardClassIds.Char -> StringBuilderIntrinsic.APPEND_CHAR
        StandardClassIds.String -> StringBuilderIntrinsic.APPEND_STRING
        else -> null
    }
}

/**
 * The `StringBuilder` that [this] access would operate on as an intrinsic: the dispatch receiver of a member, the
 * extension receiver of `clear`.
 */
val FirQualifiedAccessExpression.stringBuilderReceiver: FirExpression?
    get() = dispatchReceiver ?: extensionReceiver

/**
 * The receiver that the result of [this] expression is, when it is a `StringBuilder` intrinsic that returns its
 * receiver; `null` otherwise.
 */
fun FirExpression.aliasedStringBuilderReceiver(session: FirSession): FirExpression? {
    if (this !is FirFunctionCall) return null
    return stringBuilderReceiver.takeIf { stringBuilderIntrinsic(session)?.aliasesReceiver == true }
}

/**
 * Whether [this] is the receiver of `StringBuilder.clear`. The intrinsic does not keep its receiver, so the receiver is
 * borrowed.
 */
val FirReceiverParameterSymbol.isStringBuilderClearReceiver: Boolean
    get() = (containingDeclarationSymbol as? FirCallableSymbol<*>)?.callableId == stringBuilderClearId
