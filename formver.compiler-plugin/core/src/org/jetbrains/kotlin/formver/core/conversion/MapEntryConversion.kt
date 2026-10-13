/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.expressions.FirComponentCall
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.withType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@OptIn(DirectDeclarationsAccess::class)
internal fun StmtConversionContext.convertMapEntryComponent(call: FirComponentCall): ExpEmbedding? {
    val receiver = call.explicitReceiver
    val entryId = ClassId(FqName("kotlin.collections"), FqName("Map.Entry"), false)
    if (receiver?.resolvedType?.toRegularClassSymbol(session)?.classId != entryId || call.componentIndex !in 1..2) return null
    val entry = session.symbolProvider.getClassLikeSymbolByClassId(entryId) as FirRegularClassSymbol
    val name = Name.identifier(if (call.componentIndex == 1) "key" else "value")
    val property = entry.declarationSymbols.filterIsInstance<FirPropertySymbol>().single { it.name == name }
    return embedProperty(property).getter!!.getValue(convert(receiver), typeResolver, receiverOwned = false)
        .withType(embedType(call))
}
