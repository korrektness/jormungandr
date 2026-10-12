/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.directOverriddenSymbolsSafe
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isInterface
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.isIntersectionOverride
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.scopes.getDirectOverriddenFunctions
import org.jetbrains.kotlin.fir.scopes.getDirectOverriddenProperties
import org.jetbrains.kotlin.fir.scopes.ScopeFunctionRequiresPrewarm
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.formver.core.isPure
import org.jetbrains.kotlin.formver.core.conversion.inheritedContractImplementation
import org.jetbrains.kotlin.formver.core.isObserverOrOverride

private val FirCallableSymbol<*>.isInInterface: Boolean
    get() = (getContainingClassSymbol() as? FirClassSymbol<*>)?.classKind?.isInterface == true

/** The Kotlin spelling of this member's name in a diagnostic. */
private val FirCallableSymbol<*>.spelling: String
    get() = callableId?.className?.let { "$it.$name" } ?: name.asString()

/**
 * Reports a member declaration that breaks a rule on observers: an override of an observer is `@Pure`, and in a class it
 * has a body and fixed dispatch; a `@Pure` override overrides only observers and their overrides.
 * Property observers are unsupported.
 */
object ObserverDeclarationChecker : FirCallableDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirCallableDeclaration) {
        if (declaration !is FirSimpleFunction && declaration !is FirProperty) return
        violation(declaration)?.let { reporter.reportOn(declaration.source, PluginErrors.INVALID_OBSERVER, it) }
    }

    context(context: CheckerContext)
    @OptIn(org.jetbrains.kotlin.fir.symbols.SymbolInternals::class)
    private fun violation(declaration: FirCallableDeclaration): String? {
        val symbol = declaration.symbol
        val isPure = symbol.isPure(context.session)
        val overridden = if (declaration.isOverride) symbol.directOverriddenSymbolsSafe() else emptyList()
        val overridesObserver = overridden.any { it.isObserverOrOverride() }
        val isPureProperty = declaration is FirProperty && isPure
        if (isPureProperty) return "Invalid @Pure property: unsupported observer property."
        if (overridesObserver && !isPure) return "Invalid observer override: not @Pure."
        if (isPure) {
            overridden.firstOrNull { !it.isObserverOrOverride() }?.let {
                return "Invalid @Pure override: '${it.spelling}' is not an observer."
            }
        }
        val owner = symbol.getContainingClassSymbol() as? FirClassSymbol<*>
        val dispatchIsFixed = declaration.isFinal || owner?.fir?.isFinal == true
        if (overridesObserver && !symbol.isInInterface && !dispatchIsFixed) {
            return "Invalid observer override: open implementation."
        }
        if (overridesObserver && declaration.isAbstract && !symbol.isInInterface) {
            return "Invalid observer override: no body."
        }
        return null
    }
}

/**
 * Reports a class that implements an observer without declaring the implementation: by delegation, which gives the
 * implementation no body, or by inheriting a member that is not `@Pure` from a superclass.
 * Inherited contract implementations are rejected because their refinements require the receiving class's bindings.
 */
object ObserverImplementationChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    // Each member's overrides are read right after the scope processes the member by name.
    context(context: CheckerContext, reporter: DiagnosticReporter)
    @OptIn(ScopeFunctionRequiresPrewarm::class)
    override fun check(declaration: FirRegularClass) {
        if (declaration.isInterface) return
        val scope = declaration.symbol.unsubstitutedScope()
        val violations = mutableListOf<String>()
        for (name in scope.getCallableNames()) {
            scope.processFunctionsByName(name) {
                if (it.inheritedContractImplementation(declaration.symbol)) {
                    violations += "Inherited contract implementation: '${it.spelling}'."
                }
                violation(declaration, it, scope.getDirectOverriddenFunctions(it))?.let(violations::add)
            }
            scope.processPropertiesByName(name) { property ->
                if (property !is FirPropertySymbol) return@processPropertiesByName
                violation(declaration, property, scope.getDirectOverriddenProperties(property))?.let(violations::add)
            }
        }
        for (violation in violations) reporter.reportOn(declaration.source, PluginErrors.INVALID_OBSERVER, violation)
    }

    context(context: CheckerContext)
    private fun violation(
        declaration: FirRegularClass,
        member: FirCallableSymbol<*>,
        overridden: List<FirCallableSymbol<*>>,
    ): String? {
        // A member a superclass declares or creates is checked with the superclass.
        if (member.getContainingClassSymbol() != declaration.symbol) return null
        if (overridden.none { it.isObserverOrOverride() }) return null
        if (member.origin == FirDeclarationOrigin.Delegated) {
            return "Invalid observer override: '${member.spelling}' is delegated, so it has no body."
        }
        if (!member.isIntersectionOverride) return null
        val implementation = overridden.map { it.unwrapFakeOverrides() }
            .firstOrNull { !it.isAbstract && !it.isPure(context.session) && !it.isObserverOrOverride() }
            ?: return null
        return "Invalid observer override: '${member.spelling}' is implemented by '${implementation.spelling}', " +
                "which is not @Pure."
    }
}
