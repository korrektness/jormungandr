/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.formver.common.externalSpecifications
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

/** The embedding of [modelSymbol], which [symbol] is bound to; its values also have [symbol]'s transitive Kotlin supertypes. */
internal fun ProgramConverter.embedModelClass(symbol: FirRegularClassSymbol, modelSymbol: FirRegularClassSymbol): ClassTypeEmbedding {
    val model = embedClass(modelSymbol)
    trust.recordBinding(symbol)
    if (typeResolver.markModelHierarchy(model.name)) registerModelRuntimeSupertypes(symbol, model)
    return model
}

private fun ProgramConversionContext.registerModelRuntimeSupertypes(symbol: FirRegularClassSymbol, model: ClassTypeEmbedding) {
    val visited = mutableSetOf(symbol)
    fun visit(current: FirRegularClassSymbol) {
        for (type in current.resolvedSuperTypes) {
            val parent = type.toClassSymbol(session) as? FirRegularClassSymbol ?: continue
            if (!visited.add(parent)) continue
            val bound = session.externalSpecifications?.modelClass(parent) ?: parent
            typeResolver.addRuntimeSubtypeRelation(model.name, bound.classId.embedName())
            visit(parent)
        }
    }
    visit(symbol)
}
