/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.utils.isData
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.builder.buildPropertyAccessExpression
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.ClassPropertyAccess
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val mapEntryId = ClassId(FqName("kotlin.collections"), FqName("Map.Entry"), false)
private val mapEntryComponents = mapOf(
    CallableId(FqName("kotlin.collections"), Name.identifier("component1")) to Name.identifier("key"),
    CallableId(FqName("kotlin.collections"), Name.identifier("component2")) to Name.identifier("value"),
)

/**
 * Converts a `componentN` call that reads a property as a read of that property: the `n`th primary-constructor property
 * of a data class, or `key` and `value` of a `Map.Entry`. The value has the call's type, as the result of a call does.
 * Returns null for any other call, and inside a constructor, where reads of the object under construction need its
 * construction storage.
 */
internal fun StmtConversionContext.convertPropertyComponent(call: FirFunctionCall): ExpEmbedding? {
    if (constructionReceiver != null) return null
    val read = componentPropertyRead(call) ?: return null
    convertUnownedSpecifiedMember(read, call)?.let { return it }
    reportOpaqueLibraryRead(read)
    val symbol = read.calleeReference.toResolvedCallableSymbol() as FirPropertySymbol
    val property = embedProperty(symbol)
    val receiver = read.dispatchReceiver!!
    val converted = convertExpecting(receiver, symbol.dispatchReceiverType)
    return ClassPropertyAccess(converted, property, embedType(call), ownsFor(call, receiver, property)).getValue(this)
}

private fun StmtConversionContext.componentPropertyRead(call: FirFunctionCall): FirPropertyAccessExpression? {
    val symbol = call.calleeReference.toResolvedCallableSymbol() as? FirNamedFunctionSymbol ?: return null
    val index = componentIndex(symbol.name)?.takeIf { call.arguments.isEmpty() } ?: return null
    val entryProperty = mapEntryComponents[symbol.callableId]
    val receiver = (if (entryProperty == null) call.dispatchReceiver else call.extensionReceiver) ?: return null
    val cls = receiver.resolvedType.toRegularClassSymbol(session) ?: return null
    val name = if (entryProperty == null) cls.dataComponentName(index) else entryProperty.takeIf { cls.classId == mapEntryId }
    val property = name?.let { cls.memberProperty(it) } ?: return null
    return buildPropertyRead(call, receiver, property)
}

/** The name of the [index]th primary-constructor parameter of this class if it is a data class, else null. */
@OptIn(DirectDeclarationsAccess::class)
private fun FirRegularClassSymbol.dataComponentName(index: Int): Name? {
    if (!isData) return null
    val constructor = declarationSymbols.filterIsInstance<FirConstructorSymbol>().singleOrNull { it.isPrimary }
    return constructor?.valueParameterSymbols?.getOrNull(index - 1)?.name
}

private fun componentIndex(name: Name): Int? =
    name.asString().removePrefix("component").takeIf { it != name.asString() }?.toIntOrNull()?.takeIf { it > 0 }

@OptIn(DirectDeclarationsAccess::class)
private fun FirRegularClassSymbol.memberProperty(name: Name): FirPropertySymbol? =
    declarationSymbols.filterIsInstance<FirPropertySymbol>().singleOrNull { it.name == name }

private fun buildPropertyRead(call: FirFunctionCall, receiver: FirExpression, property: FirPropertySymbol) =
    buildPropertyAccessExpression {
        source = call.source
        coneTypeOrNull = call.resolvedType
        calleeReference = buildResolvedNamedReference {
            source = call.calleeReference.source
            name = property.name
            resolvedSymbol = property
        }
        explicitReceiver = receiver
        dispatchReceiver = receiver
    }
