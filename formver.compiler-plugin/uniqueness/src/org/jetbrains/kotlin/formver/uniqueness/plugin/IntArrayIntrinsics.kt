/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.resolvedType
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

/** A stdlib function that loops over the elements of its receiver, calling its last argument on each. */
enum class ElementLoop(name: String) {
    ForEach("forEach"),
    ForEachIndexed("forEachIndexed"),
    Count("count"),
    AnyMatch("any"),
    AllMatch("all"),
    NoneMatch("none"),
    IndexOfFirst("indexOfFirst"),
    FirstOrNull("firstOrNull");

    val callableName: Name = Name.identifier(name)
}

/**
 * Whether [this] call is one of the stdlib functions that loop over the elements of an `IntArray` receiver, with or
 * without a lambda to call on them. Such calls only read the receiver: they move nothing.
 */
fun FirFunctionCall.isIntArrayLoop(session: FirSession): Boolean {
    val callableId = toResolvedCallableSymbol()?.callableId ?: return false
    return callableId.packageName == FqName("kotlin.collections") && ElementLoop.entries.any { it.callableName == callableId.callableName } &&
            extensionReceiver?.resolvedType?.fullyExpandedType(session)?.classId == intArrayClassId
}
