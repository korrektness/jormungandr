/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.analysis.checkers.directOverriddenSymbolsSafe
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.scopes.ScopeFunctionRequiresPrewarm
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.formver.core.embeddings.properties.CustomGetter
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.formver.core.embeddings.callables.NamedCallableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.isGhostObserver
import org.jetbrains.kotlin.formver.core.isObserver
import org.jetbrains.kotlin.formver.core.isObserverOrOverride
import org.jetbrains.kotlin.formver.core.names.embedName
import org.jetbrains.kotlin.formver.viper.SymbolicName

/** Resolves observer implementations in each registered ownership presentation. */
class ObserverBindingRegistry {
    private val classSymbols = linkedMapOf<SymbolicName, FirRegularClassSymbol>()

    @OptIn(ScopeFunctionRequiresPrewarm::class)
    fun registerClass(ctx: ProgramConversionContext, symbol: FirRegularClassSymbol) {
        val name = symbol.classId.embedName()
        if (classSymbols.putIfAbsent(name, symbol) != null) return
        context(ctx.checkerContext) {
            val scope = symbol.unsubstitutedScope()
            var hasObserver = false
            for (memberName in scope.getCallableNames()) {
                scope.processFunctionsByName(memberName) { if (it.isObserverOrOverride()) hasObserver = true }
                scope.processPropertiesByName(memberName) { if (it.isObserverOrOverride()) hasObserver = true }
            }
            if (hasObserver) ctx.typeResolver.markClassFamilyView(name)
        }
    }

    @OptIn(ScopeFunctionRequiresPrewarm::class)
    fun registerBindings(
        ctx: ProgramConversionContext,
        signature: (SymbolicName) -> NonInlineFunctionSignature,
    ) {
        // An impure implementation, such as an unmodeled library body, cannot define an observation, so the class
        // binds none: its observations stay opaque calls.
        fun bindObserver(
            type: ClassTypeEmbedding,
            observer: SymbolicName,
            implementation: NonInlineFunctionSignature,
            dynamicDispatch: Boolean,
            hasImplementation: Boolean,
        ) {
            if (implementation.isPure) ctx.typeResolver.bindObserver(type, observer, implementation, dynamicDispatch, hasImplementation)
        }
        val visited = mutableSetOf<SymbolicName>()
        while (true) {
            val entry = classSymbols.entries.firstOrNull { it.key !in visited } ?: break
            visited += entry.key
            val type = ctx.typeResolver.lookupClassTypeEmbedding(entry.key)!!
            context(ctx.checkerContext) {
                val scope = entry.value.unsubstitutedScope()
                for (name in scope.getCallableNames()) {
                    scope.processPropertiesByName(name) { member ->
                        // Mutable properties are diagnosed by the observer checker and cannot supply a binding.
                        if (member !is FirPropertySymbol || member.isVar || !member.isObserverOrOverride()) return@processPropertiesByName
                        val implementation = member.unwrapFakeOverrides()
                        val getter = ctx.embedProperty(implementation).getter as CustomGetter
                        val binding = signature(getter.getterMethod.name)
                        fun bind(symbol: FirPropertySymbol) {
                            if (symbol.isObserverOrOverride()) {
                                val observer = ctx.embedProperty(if (symbol.isObserver()) symbol else symbol.unwrapFakeOverrides()).getter as CustomGetter
                                bindObserver(type, observer.getterMethod.name, binding,
                                    dynamicDispatch = !entry.value.isFinal && !implementation.isFinal,
                                    hasImplementation = !implementation.isAbstract)
                            }
                            symbol.directOverriddenSymbolsSafe().filterIsInstance<FirPropertySymbol>().forEach(::bind)
                        }
                        bind(member)
                    }
                    scope.processFunctionsByName(name) { member ->
                        if (!member.isObserverOrOverride()) return@processFunctionsByName
                        val implementation = member.unwrapFakeOverrides()
                        val bindingName = (ctx.embedAnyFunction(implementation) as NamedCallableEmbedding).name
                        val binding = signature(bindingName)
                        fun bind(symbol: FirNamedFunctionSymbol) {
                            if (symbol.isObserverOrOverride()) {
                                val declaration = if (symbol.isObserver()) symbol else symbol.unwrapFakeOverrides()
                                val observerName = (ctx.embedAnyFunction(declaration) as NamedCallableEmbedding).name
                                bindObserver(type, observerName, binding,
                                    dynamicDispatch = !entry.value.isFinal && !implementation.isFinal,
                                    hasImplementation = !implementation.isAbstract && !implementation.isGhostObserver(ctx.session))
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
