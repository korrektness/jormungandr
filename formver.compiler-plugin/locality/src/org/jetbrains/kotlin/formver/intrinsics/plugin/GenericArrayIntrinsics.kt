/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.intrinsics.plugin

import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val arrayClassId = ClassId.topLevel(FqName("kotlin.Array"))
val genericArrayGetId = CallableId(arrayClassId, Name.identifier("get"))
val genericArraySetId = CallableId(arrayClassId, Name.identifier("set"))

fun FirFunctionCall.isGenericArrayElementAccess(): Boolean =
    toResolvedCallableSymbol()?.callableId.let { it == genericArrayGetId || it == genericArraySetId }

/** These factories return fresh storage, including a zero-length array. */
fun FirCallableSymbol<*>.isGenericArrayFactory(): Boolean = callableId.let {
    it?.packageName == FqName("kotlin") && it.className == null &&
            it.callableName.asString() in setOf("arrayOfNulls", "emptyArray")
}
