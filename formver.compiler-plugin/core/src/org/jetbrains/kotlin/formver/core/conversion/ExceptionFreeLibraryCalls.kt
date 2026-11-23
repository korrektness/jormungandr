/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.common.isLibraryDeclaration
import org.jetbrains.kotlin.formver.core.embeddings.callables.CallableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.callables.insertCall
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

private val valueClassIds: Set<ClassId> = setOf(
    StandardClassIds.String, StandardClassIds.Char, StandardClassIds.Boolean,
    StandardClassIds.Byte, StandardClassIds.Short, StandardClassIds.Int, StandardClassIds.Long,
    StandardClassIds.Float, StandardClassIds.Double,
    StandardClassIds.UByte, StandardClassIds.UShort, StandardClassIds.UInt, StandardClassIds.ULong,
)

private val recordClassIds: Set<ClassId> = setOf(ClassId.topLevel(FqName("kotlin.Pair")), ClassId.topLevel(FqName("kotlin.Triple")))

private val kotlinPackage = FqName("kotlin")
private val TO = Name.identifier("to")
private val TO_STRING = Name.identifier("toString")
private val HASH_CODE = Name.identifier("hashCode")
private val EQUALS = Name.identifier("equals")

/**
 * Whether [this] call is to a library function that throws no exception for any arguments (SPECIFICATIONS.md,
 * "Exception freedom"): `to`, the constructor of `Pair` or `Triple`, or `toString`, `hashCode` or `equals` on a
 * receiver whose static type is `String`, `Char`, `Boolean` or a signed or unsigned primitive number type, possibly
 * nullable. Each of those classes is final, so the call cannot dispatch to source code.
 */
fun FirFunctionCall.isExceptionFreeLibraryCall(): Boolean {
    val symbol = toResolvedCallableSymbol()?.takeIf { it.isLibraryDeclaration() } ?: return false
    val id = symbol.callableId ?: return false
    val topLevelKotlin = id.className == null && id.packageName == kotlinPackage
    return when {
        symbol is FirConstructorSymbol -> id.classId in recordClassIds
        topLevelKotlin && id.callableName == TO -> true
        else -> isValueTypeMethodCall(id.callableName, if (topLevelKotlin) extensionReceiver else dispatchReceiver)
    }
}

private fun FirFunctionCall.isValueTypeMethodCall(name: Name, receiver: FirExpression?): Boolean {
    val arity = when (name) {
        TO_STRING, HASH_CODE -> 0
        EQUALS -> 1
        else -> return false
    }
    val receiverClass = (receiver?.resolvedType?.lowerBoundIfFlexible() as? ConeClassLikeType)?.lookupTag?.classId
    return arguments.size == arity && receiverClass in valueClassIds
}

/** Inserts [callee] at [call] on the evaluated [args], with no exceptional exit when the call is exception-free. */
fun StmtConversionContext.insertCallAt(
    call: FirFunctionCall,
    callee: CallableEmbedding,
    args: List<ExpEmbedding>,
    returnType: TypeEmbedding,
): ExpEmbedding = withCallSite(call) {
    if (call.isExceptionFreeLibraryCall()) withoutExceptionalExits { callee.insertCall(args, this, returnType) }
    else callee.insertCall(args, this, returnType)
}
