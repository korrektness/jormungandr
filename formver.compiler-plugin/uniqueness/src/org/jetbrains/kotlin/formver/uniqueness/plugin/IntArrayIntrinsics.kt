/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val intArrayClassId = ClassId.topLevel(FqName("kotlin.IntArray"))

val intArrayGetId = CallableId(intArrayClassId, Name.identifier("get"))
val intArraySetId = CallableId(intArrayClassId, Name.identifier("set"))

/**
 * Whether [this] call reads or writes an element of an `IntArray`. Such calls are intrinsic: they move nothing.
 */
fun FirFunctionCall.isIntArrayElementAccess(): Boolean =
    toResolvedCallableSymbol()?.callableId.let { it == intArrayGetId || it == intArraySetId }
