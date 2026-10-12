/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.analysis.checkers.directOverriddenSymbolsSafe
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.scopes.ScopeFunctionRequiresPrewarm
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.formver.core.embeddings.callables.NamedCallableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineFunctionSignature
import org.jetbrains.kotlin.formver.core.isObserver
import org.jetbrains.kotlin.formver.core.isObserverOrOverride
import org.jetbrains.kotlin.formver.core.names.embedName
import org.jetbrains.kotlin.formver.viper.SymbolicName

/** Resolves observer implementations in each registered ownership presentation. */
class ObserverBindingRegistry {
    private val classSymbols = linkedMapOf<SymbolicName, FirRegularClassSymbol>()

    fun registerClass(symbol: FirRegularClassSymbol) {
        classSymbols.putIfAbsent(symbol.classId.embedName(), symbol)
    }

    @OptIn(ScopeFunctionRequiresPrewarm::class)
    fun registerBindings(ctx: ProgramConversionContext, signature: (SymbolicName) -> NonInlineFunctionSignature) {
        val visited = mutableSetOf<SymbolicName>()
        while (true) {
            val entry = classSymbols.entries.firstOrNull { it.key !in visited } ?: break
            visited += entry.key
            val type = ctx.typeResolver.lookupClassTypeEmbedding(entry.key)!!
            context(ctx.checkerContext) {
                val scope = entry.value.unsubstitutedScope()
                for (name in scope.getCallableNames()) {
                    scope.processFunctionsByName(name) { member ->
                        if (!member.isObserverOrOverride()) return@processFunctionsByName
                        val bindingName = (ctx.embedAnyFunction(member.unwrapFakeOverrides()) as NamedCallableEmbedding).name
                        val binding = signature(bindingName)
                        fun bind(symbol: FirNamedFunctionSymbol) {
                            if (symbol.isObserver()) {
                                val observerName = (ctx.embedAnyFunction(symbol) as NamedCallableEmbedding).name
                                ctx.typeResolver.bindObserver(type, signature(observerName), binding)
                            }
                            symbol.directOverriddenSymbolsSafe().filterIsInstance<FirNamedFunctionSymbol>().forEach(::bind)
                        }
                        bind(member)
                    }
                }
            }
        }
    }
}
