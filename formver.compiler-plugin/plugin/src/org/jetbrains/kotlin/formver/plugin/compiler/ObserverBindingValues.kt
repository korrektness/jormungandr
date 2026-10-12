/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.impl.FirElseIfTrueCondition
import org.jetbrains.kotlin.fir.references.FirReference
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.FirTypeProjection
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.isNullableString
import org.jetbrains.kotlin.fir.types.isPrimitiveOrNullablePrimitive
import org.jetbrains.kotlin.fir.types.isString
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.core.conversion.isLawValueOperation
import org.jetbrains.kotlin.formver.core.isPure

@OptIn(SymbolInternals::class)
private fun FirQualifiedAccessExpression.isBindingValueAccess(session: FirSession): Boolean {
    val symbol = toResolvedCallableSymbol()
    return when {
        isLawValueOperation() -> true
        this is FirFunctionCall -> symbol?.isPure(session) == true
        symbol is FirValueParameterSymbol -> true
        symbol is FirPropertySymbol -> {
            val property = symbol.fir
            val getter = property.getter
            val fieldRead = property.backingField != null && property.status.modality == Modality.FINAL && getter?.body == null
            val valueRead = symbol.callableId?.isLocal == true || fieldRead
            property.delegate == null && (valueRead || getter?.symbol?.isPure(session) == true)
        }
        else -> false
    }
}

private val nonThrowingTypeOperators = setOf(FirOperation.IS, FirOperation.NOT_IS, FirOperation.SAFE_AS)

private fun FirExpression.isNullLiteral(): Boolean = this is FirLiteralExpression && value == null

private fun FirExpression.hasBuiltinEquals(): Boolean {
    val type = resolvedType
    return type.isPrimitiveOrNullablePrimitive || type.isString || type.isNullableString
}

/** `==` on these operands does not call a user-defined `equals`. */
private fun FirEqualityOperatorCall.isBuiltinEquality(): Boolean {
    val operands = argumentList.arguments
    val identity = operation == FirOperation.IDENTITY || operation == FirOperation.NOT_IDENTITY
    return identity || operands.any { it.isNullLiteral() } || operands.all { it.hasBuiltinEquals() }
}

/**
 * Checks every subtree, including receivers and arguments, without evaluating the binding.
 * Only the node kinds listed here are accepted; every other node, including any that can throw or write, is rejected.
 */
internal fun FirExpression.isObserverBindingValue(session: FirSession): Boolean {
    var valid = true
    accept(object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            val allowed = when (element) {
                is FirQualifiedAccessExpression -> element.isBindingValueAccess(session)
                is FirTypeOperatorCall -> element.operation in nonThrowingTypeOperators
                is FirEqualityOperatorCall -> element.isBuiltinEquality()
                is FirLiteralExpression, is FirResolvedQualifier, is FirComparisonExpression,
                is FirBooleanOperatorExpression, is FirSmartCastExpression, is FirWhenExpression, is FirWhenBranch,
                is FirElseIfTrueCondition, is FirWhenSubjectExpression, is FirElvisExpression, is FirSafeCallExpression,
                is FirCheckedSafeCallSubject, is FirBlock, is FirWrappedArgumentExpression, is FirArgumentList,
                is FirReference, is FirTypeRef, is FirTypeProjection -> true
                else -> false
            }
            if (!allowed) valid = false
            element.acceptChildren(this)
        }
    })
    return valid
}
