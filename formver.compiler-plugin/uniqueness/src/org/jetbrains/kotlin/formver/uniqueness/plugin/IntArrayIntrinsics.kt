/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

private val intArrayClassId = ClassId.topLevel(FqName("kotlin.IntArray"))

val intArrayGetId = CallableId(intArrayClassId, Name.identifier("get"))
val intArraySetId = CallableId(intArrayClassId, Name.identifier("set"))

/**
 * Whether [this] call reads or writes an element of an `IntArray`. Such calls are intrinsic: they move nothing.
 */
fun FirFunctionCall.isIntArrayElementAccess(): Boolean =
    toResolvedCallableSymbol()?.callableId.let { it == intArrayGetId || it == intArraySetId }

/**
 * The array that [this] symbol stands for when it is the `<array>` temporary that FIR introduces for a compound
 * index assignment or an index increment, such as `a[i] += v` or `a[i]++`; `null` for any other symbol.
 *
 * The temporary is only used as the receiver of the element accesses, so it is an alias of its initializer.
 */
val FirBasedSymbol<*>.indexedArrayInitializer: FirExpression?
    get() = (this as? FirPropertySymbol)?.takeIf { it.isLocal && it.name == SpecialNames.ARRAY }?.resolvedInitializer
