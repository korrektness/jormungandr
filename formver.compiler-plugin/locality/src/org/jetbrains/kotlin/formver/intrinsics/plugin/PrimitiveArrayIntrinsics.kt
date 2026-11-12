/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.intrinsics.plugin

import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val intArrayClassId = ClassId.topLevel(FqName("kotlin.IntArray"))
val byteArrayClassId = ClassId.topLevel(FqName("kotlin.ByteArray"))
val charArrayClassId = ClassId.topLevel(FqName("kotlin.CharArray"))
val longArrayClassId = ClassId.topLevel(FqName("kotlin.LongArray"))

/** The primitive array classes with an element model. */
val primitiveArrayClassIds = setOf(intArrayClassId, byteArrayClassId, charArrayClassId, longArrayClassId)

private val getName = Name.identifier("get")
private val setName = Name.identifier("set")

/** Whether [this] callable is the `get` of a modelled primitive array class. */
fun CallableId.isPrimitiveArrayGet(): Boolean = classId in primitiveArrayClassIds && callableName == getName

/**
 * Whether [this] call reads or writes an element of a modelled primitive array. Such calls are intrinsic: they move
 * nothing.
 */
fun FirFunctionCall.isPrimitiveArrayElementAccess(): Boolean =
    toResolvedCallableSymbol()?.callableId.let {
        it != null && it.classId in primitiveArrayClassIds && (it.callableName == getName || it.callableName == setName)
    }

/** A stdlib function that loops over the elements of its receiver, calling its last argument on each. */
enum class ElementLoop(name: String, val arity: Int = 1, val lambdaArity: Int = 1) {
    ForEach("forEach"),
    ForEachIndexed("forEachIndexed", lambdaArity = 2),
    Count("count"),
    Fold("fold", arity = 2, lambdaArity = 2),
    MapElements("map"),
    FilterElements("filter"),
    AnyMatch("any"),
    AllMatch("all"),
    NoneMatch("none"),
    IndexOfFirst("indexOfFirst"),
    FirstOrNull("firstOrNull");

    val callableName: Name = Name.identifier(name)
}

/** Whether the resolved overload iterates an Iterable or Map through the collection template. */
fun FirFunctionCall.isCollectionLoop(): Boolean = toResolvedCallableSymbol()?.isCollectionLoop() == true

fun FirCallableSymbol<*>.isCollectionLoop(): Boolean {
    val loop = collectionElementLoop() ?: return false
    if (loop in listOf(ElementLoop.MapElements, ElementLoop.FilterElements) &&
        resolvedReturnType.classId != ClassId.topLevel(FqName("kotlin.collections.List"))) return false
    return when (resolvedReceiverTypeRef?.coneType?.classId) {
        ClassId.topLevel(FqName("kotlin.collections.Iterable")),
        ClassId.topLevel(FqName("kotlin.collections.List")) -> true
        ClassId.topLevel(FqName("kotlin.collections.Map")) -> loop == ElementLoop.ForEach || loop == ElementLoop.MapElements
        else -> false
    }
}

/** Whether this stdlib element loop reads a modelled primitive array receiver without moving it. */
fun FirFunctionCall.isPrimitiveArrayLoop(): Boolean = toResolvedCallableSymbol()?.isPrimitiveArrayLoop() == true

fun FirCallableSymbol<*>.isPrimitiveArrayLoop(): Boolean {
    val loop = collectionElementLoop() ?: return false
    return loop != ElementLoop.MapElements && loop != ElementLoop.FilterElements &&
            resolvedReceiverTypeRef?.coneType?.classId in primitiveArrayClassIds
}

private fun FirCallableSymbol<*>.collectionElementLoop(): ElementLoop? {
    val id = callableId ?: return null
    if (id.packageName != FqName("kotlin.collections") || id.className != null) return null
    val function = this as? FirFunctionSymbol<*> ?: return null
    return ElementLoop.entries.firstOrNull {
        it.callableName == id.callableName && it.arity == function.valueParameterSymbols.size &&
                function.valueParameterSymbols.last().resolvedReturnType.classId ==
                ClassId.topLevel(FqName("kotlin.Function${it.lambdaArity}"))
    }
}
