/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.formver.common.*
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
import org.jetbrains.kotlin.formver.core.isGhostObserver
import org.jetbrains.kotlin.formver.core.isPure
import org.jetbrains.kotlin.formver.core.isObserverOrOverride

private val FirCallableSymbol<*>.isInInterface: Boolean
    get() = (getContainingClassSymbol() as? FirClassSymbol<*>)?.classKind?.isInterface == true


context(context: CheckerContext)
internal fun FirCallableDeclaration.overriddenGhostObserver(): FirCallableSymbol<*>? =
    if (isOverride) symbol.directOverriddenSymbolsSafe().map { it.unwrapFakeOverrides() }
        .firstOrNull { it.isGhostObserver(context.session) } else null

/**
 * Reports a member declaration that breaks a rule on observers: a ghost observer has no interface overrides; an override of an
 * observer is `@Pure`, and a concrete implementation has a body; a `@Pure` override overrides only observers and
 * their overrides.
 */
object ObserverDeclarationChecker : FirCallableDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirCallableDeclaration) {
        if (declaration !is FirSimpleFunction && declaration !is FirProperty) return
        val overridden = if (declaration.isOverride) declaration.symbol.directOverriddenSymbolsSafe() else emptyList()
        val violation = declaration.overriddenGhostObserver()?.takeIf { declaration.symbol.isInInterface }
            ?.let { KotlinCondition.Observer(ObserverRule.GHOST_OVERRIDE, it) } ?: violation(declaration, overridden)
        if (violation != null) reporter.reportObserver(declaration.source, violation)
    }

    context(context: CheckerContext)
    @OptIn(org.jetbrains.kotlin.fir.symbols.SymbolInternals::class)
    private fun violation(declaration: FirCallableDeclaration, overridden: List<FirCallableSymbol<*>>): KotlinCondition.Observer? {
        val symbol = declaration.symbol
        val isPure = symbol.isPure(context.session)
        val overridesObserver = overridden.any { it.isObserverOrOverride() }
        if (declaration is FirProperty && declaration.isVar && isPure) return KotlinCondition.Observer(ObserverRule.MUTABLE_PURE_PROPERTY)
        if (overridesObserver && !isPure) return KotlinCondition.Observer(ObserverRule.IMPURE_OVERRIDE)
        if (isPure) {
            overridden.firstOrNull { !it.isObserverOrOverride() }?.let {
                return KotlinCondition.Observer(ObserverRule.NON_OBSERVER_OVERRIDE, it)
            }
        }
        return null
    }
}

/**
 * Reports a class that implements an observer without declaring the implementation: by delegation, which gives the
 * implementation no body, or by inheriting a member that is not `@Pure` from a superclass.
 */
object ObserverImplementationChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    // Each member's overrides are read right after the scope processes the member by name.
    context(context: CheckerContext, reporter: DiagnosticReporter)
    @OptIn(ScopeFunctionRequiresPrewarm::class)
    override fun check(declaration: FirRegularClass) {
        if (declaration.isInterface) return
        val scope = declaration.symbol.unsubstitutedScope()
        val violations = mutableListOf<KotlinCondition.Observer>()
        for (name in scope.getCallableNames()) {
            scope.processFunctionsByName(name) {
                val overridden = scope.getDirectOverriddenFunctions(it)
                (implicitGhostOverride(declaration, it, overridden)
                    ?: violation(declaration, it, overridden))?.let(violations::add)
            }
            scope.processPropertiesByName(name) { property ->
                if (property !is FirPropertySymbol) return@processPropertiesByName
                violation(declaration, property, scope.getDirectOverriddenProperties(property))?.let(violations::add)
            }
        }
        violations.distinct().forEach { reporter.reportObserver(declaration.source, it) }
    }

    // A member a superclass declares or creates is checked with the superclass.
    private fun FirCallableSymbol<*>.isCreatedIn(declaration: FirRegularClass): Boolean =
        getContainingClassSymbol() == declaration.symbol

    context(context: CheckerContext)
    private fun implicitGhostOverride(
        declaration: FirRegularClass,
        member: FirCallableSymbol<*>,
        overridden: List<FirCallableSymbol<*>>,
    ): KotlinCondition.Observer? {
        val implicitOverride = member.origin == FirDeclarationOrigin.Delegated || member.isIntersectionOverride
        if (!member.isCreatedIn(declaration) || !implicitOverride) return null
        val ghost = overridden.map { it.unwrapFakeOverrides() }.firstOrNull { it.isGhostObserver(context.session) }
        return ghost?.let { KotlinCondition.Observer(ObserverRule.GHOST_OVERRIDE, it) }
    }

    context(context: CheckerContext)
    private fun violation(
        declaration: FirRegularClass,
        member: FirCallableSymbol<*>,
        overridden: List<FirCallableSymbol<*>>,
    ): KotlinCondition.Observer? {
        if (!member.isCreatedIn(declaration)) return null
        if (overridden.none { it.isObserverOrOverride() }) return null
        if (member.origin == FirDeclarationOrigin.Delegated) {
            return KotlinCondition.Observer(ObserverRule.DELEGATED_IMPLEMENTATION, member)
        }
        if (!member.isIntersectionOverride) return null
        val implementation = overridden.map { it.unwrapFakeOverrides() }
            .firstOrNull { !it.isAbstract && !it.isPure(context.session) && !it.isObserverOrOverride() }
            ?: return null
        return KotlinCondition.Observer(ObserverRule.IMPURE_IMPLEMENTATION, member, implementation)
    }
}
