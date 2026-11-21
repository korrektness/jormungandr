/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.locality.plugin

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.scopes.FirTypeScope
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides

/**
 * The real declaration that implements [callee] in the constructed class's member scope, or null when no member, or
 * more than one, overrides it, as for fields and for Java getters accessed as synthetic properties.
 */
fun FirRegularClassSymbol.effectiveConstructionMember(callee: FirCallableSymbol<*>, session: FirSession): FirCallableSymbol<*>? {
    val scope = unsubstitutedScope(session, ScopeSession(), false, FirResolvePhase.BODY_RESOLVE)
    val candidates = mutableListOf<FirCallableSymbol<*>>()
    when (callee) {
        is FirNamedFunctionSymbol -> scope.processFunctionsByName(callee.name) { candidates += it }
        is FirPropertySymbol -> scope.processPropertiesByName(callee.name) { candidates += it }
        else -> return null
    }
    val target = callee.unwrapFakeOverrides()
    return candidates.singleOrNull { overridesConstructionMember(it, scope, target, mutableSetOf()) }?.unwrapFakeOverrides()
}

/**
 * Whether the constructed class fixes the implementation of [callee]. Fields never dispatch. A function or property is
 * fixed when the member scope resolves it to exactly one [effectiveConstructionMember] and that member or the class is
 * final.
 */
fun FirRegularClassSymbol.isFixedConstructionDispatch(callee: FirCallableSymbol<*>, session: FirSession): Boolean {
    if (callee !is FirNamedFunctionSymbol && callee !is FirPropertySymbol) return true
    val member = effectiveConstructionMember(callee, session) ?: return false
    return isFinal || member.isFinal
}

private fun overridesConstructionMember(
    member: FirCallableSymbol<*>,
    scope: FirTypeScope,
    target: FirCallableSymbol<*>,
    visited: MutableSet<FirCallableSymbol<*>>,
): Boolean {
    if (member.unwrapFakeOverrides() == target) return true
    if (!visited.add(member)) return false
    var matches = false
    fun inspect(overridden: FirCallableSymbol<*>, baseScope: FirTypeScope): ProcessorAction {
        matches = matches || overridesConstructionMember(overridden, baseScope, target, visited)
        return if (matches) ProcessorAction.STOP else ProcessorAction.NEXT
    }
    when (member) {
        is FirNamedFunctionSymbol -> scope.processDirectOverriddenFunctionsWithBaseScope(member, ::inspect)
        is FirPropertySymbol -> scope.processDirectOverriddenPropertiesWithBaseScope(member, ::inspect)
        else -> return false
    }
    return matches
}
