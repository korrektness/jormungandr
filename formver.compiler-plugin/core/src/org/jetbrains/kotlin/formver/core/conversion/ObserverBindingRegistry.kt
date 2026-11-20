/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.analysis.checkers.directOverriddenSymbolsSafe
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.formver.common.externalSpecifications
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

    /**
     * Binds each registered class's observers to their implementations. A library class that binds an observer of its
     * own also binds every observer of its supertypes' external models that its own members do not bind, since the
     * models' contracts relate their observers.
     */
    @OptIn(ScopeFunctionRequiresPrewarm::class)
    fun registerBindings(
        ctx: ProgramConversionContext,
        signature: (SymbolicName) -> NonInlineFunctionSignature,
    ) {
        val bound = mutableSetOf<SymbolicName>()
        var inherited = false
        // An impure implementation, such as an unmodeled library body, cannot define an observation, so the class
        // binds none: its observations stay opaque calls.
        fun bindObserver(
            type: ClassTypeEmbedding,
            observer: SymbolicName,
            implementation: NonInlineFunctionSignature,
            dynamicDispatch: Boolean,
            hasImplementation: Boolean,
        ) {
            if (!implementation.isPure || (inherited && observer in bound)) return
            bound += observer
            ctx.typeResolver.bindObserver(type, observer, implementation, dynamicDispatch, hasImplementation)
        }
        val visited = mutableSetOf<SymbolicName>()
        while (true) {
            val entry = classSymbols.entries.firstOrNull { it.key !in visited } ?: break
            visited += entry.key
            val type = ctx.typeResolver.lookupClassTypeEmbedding(entry.key)!!
            bound.clear()
            val scopes = listOf(entry.value) + (if (entry.value.origin == FirDeclarationOrigin.Source) emptyList()
                else modelSupertypes(ctx, entry.value))
            for ((index, owner) in scopes.withIndex()) context(ctx.checkerContext) {
                inherited = index > 0
                if (inherited && bound.isEmpty()) return@context
                val scope = owner.unsubstitutedScope()
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

    /** The external models of [symbol]'s supertypes. */
    private fun modelSupertypes(ctx: ProgramConversionContext, symbol: FirRegularClassSymbol): List<FirRegularClassSymbol> =
        lookupSuperTypes(symbol, lookupInterfaces = true, deep = true, useSiteSession = ctx.session)
            .mapNotNull { supertype ->
                supertype.toRegularClassSymbol(ctx.session)?.let { ctx.session.externalSpecifications?.modelClass(it) }
            }.distinct()
}
