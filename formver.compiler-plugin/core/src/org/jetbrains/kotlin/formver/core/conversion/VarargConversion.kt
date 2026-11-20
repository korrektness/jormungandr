/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirSpreadArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.VarargArray
import org.jetbrains.kotlin.formver.core.embeddings.expression.VarargPart
import org.jetbrains.kotlin.formver.core.embeddings.expression.blockOf
import org.jetbrains.kotlin.formver.core.embeddings.expression.withInvariants
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding

/**
 * The fresh array of type [type] that [call] passes for a vararg parameter: the elements of [argument] in order, or
 * none when the argument is omitted. A spread `*a` contributes a copy of the elements of `a`.
 */
fun StmtConversionContext.convertVarargArray(
    call: FirElement,
    argument: FirVarargArgumentsExpression?,
    type: TypeEmbedding,
): ExpEmbedding {
    val parts = argument?.arguments.orEmpty().map { element ->
        if (element is FirSpreadArgumentExpression) {
            VarargPart.Spread(convert(element.expression), ownsBefore(call, element.expression))
        } else {
            VarargPart.Element(convert(element))
        }
    }
    val array = freshAnonVar(type)
    return blockOf(VarargArray(array, parts), array.withInvariants(typeResolver) {
        proven = true
        access = true
    })
}

/** [argument] as [call] passes it for a parameter of type [type]: arguments for a vararg parameter become its array. */
fun StmtConversionContext.convertArgument(call: FirElement, argument: FirExpression, type: TypeEmbedding): ExpEmbedding =
    if (argument is FirVarargArgumentsExpression) convertVarargArray(call, argument, type) else convert(argument)

/** The value [call] passes for [parameter] of type [type], which it omits and which has no default value. */
fun StmtConversionContext.omittedArgument(call: FirElement, parameter: FirValueParameterSymbol, type: TypeEmbedding): ExpEmbedding {
    if (!parameter.isVararg) {
        throw SnaktInternalException(call.source, "Omitted parameter `${parameter.name}` is not vararg and has no default value.")
    }
    return convertVarargArray(call, null, type)
}
