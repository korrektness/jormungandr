/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.core.embeddings.types.GenericArrayEmbedding
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.intrinsics.plugin.genericArrayGetId

fun StmtConversionContext.convertGenericArrayLastIndex(access: FirPropertyAccessExpression): ExpEmbedding? {
    val receiver = access.extensionReceiver ?: return null
    if (receiver.resolvedType.classId != GenericArrayEmbedding.classId ||
        access.toResolvedCallableSymbol()?.callableId != CallableId(FqName("kotlin.collections"), Name.identifier("lastIndex"))) return null
    return OperatorExpEmbeddings.SubIntInt(GenericArraySize(convert(receiver)), IntLit(1))
}

fun StmtConversionContext.convertGenericArrayElementAccess(call: FirFunctionCall): ExpEmbedding {
    val receiver = call.dispatchReceiver
        ?: throw SnaktInternalException(call.source, "An Array element access has no receiver.")
    val array = convert(receiver)
    val args = call.argumentList.arguments.map { convert(it) }
    val owned = ownsBefore(call, receiver)
    return if (call.toResolvedCallableSymbol()?.callableId == genericArrayGetId) {
        GenericArrayGet(array, args[0], owned, embedType(call), receiver.arraySymbol())
            .withInvariants(typeResolver) { proven = true; access = true }
    } else {
        warnIfUntrackedWrite(call, receiver, owned)
        GenericArraySet(array, args[0], args[1], owned, receiver.arraySymbol())
    }
}

/** Factory arguments are evaluated once, before allocation and its initialization facts. */
fun StmtConversionContext.convertGenericArrayFactory(call: FirFunctionCall): ExpEmbedding {
    val size = freshAnonVar(IntLit(0).type)
    val array = freshAnonVar(embedType(call))
    return blockOf(
        Declare(size, call.argumentList.arguments.singleOrNull()?.let { convert(it) } ?: IntLit(0)),
        Assert(OperatorExpEmbeddings.GeIntInt(size, IntLit(0))),
        CollectionListAllocation(array, OperatorExpEmbeddings.And(
            EqCmp(GenericArraySize(array), size), GenericArrayAllNull(array),
        )),
    )
}
