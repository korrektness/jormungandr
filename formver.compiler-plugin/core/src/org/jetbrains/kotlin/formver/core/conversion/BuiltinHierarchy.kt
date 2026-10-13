/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.core.names.embedName

/** Records built-in subtyping without embedding library representation fields. */
internal fun ProgramConversionContext.registerBuiltinHierarchy(symbol: FirRegularClassSymbol, embedding: ClassTypeEmbedding) {
    if (typeResolver.lookupClassTypeEmbedding(embedding.name) != null) return
    typeResolver.register(embedding, isInterface = false)
    val visited = mutableSetOf<SymbolicName>()
    fun visit(current: FirRegularClassSymbol, name: SymbolicName) {
        if (!visited.add(name)) return
        for (type in current.resolvedSuperTypes) {
            // Kotlin diagnoses unresolved supertypes; only resolvable class edges enter the hierarchy.
            val parent = type.toClassSymbol(session) as? FirRegularClassSymbol ?: continue
            val parentName = parent.classId.embedName()
            typeResolver.addSubtypeRelation(name, parentName)
            visit(parent, parentName)
        }
    }
    visit(symbol, embedding.name)
}
