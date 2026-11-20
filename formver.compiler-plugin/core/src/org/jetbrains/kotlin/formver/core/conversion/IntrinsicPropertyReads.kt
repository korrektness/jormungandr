/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.types.isChar
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.IntegerKind
import org.jetbrains.kotlin.formver.core.embeddings.expression.IntegerOperators
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** A read of `IterationSequence.size` or `Char.code` as a built-in operation, or null for any other property. */
internal fun StmtConversionContext.convertIntrinsicPropertyRead(access: FirPropertyAccessExpression): ExpEmbedding? {
    val propertyId = access.toResolvedCallableSymbol()?.callableId
    if (propertyId?.packageName?.asString() == "org.jetbrains.kotlin.formver.plugin" &&
        propertyId.className?.asString() == "IterationSequence" && propertyId.callableName.asString() == "size") {
        return OperatorExpEmbeddings.IterationSequenceSize(convert(access.dispatchReceiver!!))
    }
    val extensionReceiver = access.extensionReceiver
    if (extensionReceiver != null && extensionReceiver.resolvedType.isChar &&
        propertyId == CallableId(FqName("kotlin"), Name.identifier("code"))
    ) {
        return IntegerOperators.unary.getValue(
            IntegerOperators.Unary(IntegerKind.Char, "<get-code>", IntegerKind.Int)
        )(convert(extensionReceiver))
    }
    return null
}
