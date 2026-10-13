/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.common.LawRule
import org.jetbrains.kotlin.formver.locality.plugin.ConstructionPhases
import org.jetbrains.kotlin.formver.locality.plugin.effectiveConstructionMember
import org.jetbrains.kotlin.formver.locality.plugin.isFixedConstructionDispatch

/** Front-end constructor discipline; phase membership is supplied by Kotlin definite assignment. */
fun constructionDisciplineFailures(owner: FirRegularClassSymbol, session: FirSession): List<LawRule> =
    if (owner.origin != FirDeclarationOrigin.Source) emptyList() else ConstructionDiscipline(owner, session).failures()

@OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
private class ConstructionDiscipline(private val owner: FirRegularClassSymbol, private val session: FirSession) {
    private val phases = ConstructionPhases(owner)
    private val failures = linkedSetOf<LawRule>()
    private val hierarchy = linkedSetOf<FirRegularClassSymbol>()
    private val inspectedMembers = mutableSetOf<FirCallableSymbol<*>>()
    private val fixedReceivers = mutableSetOf<FirThisReceiverExpression>()

    private fun collect(symbol: FirRegularClassSymbol) {
        if (!hierarchy.add(symbol)) return
        symbol.resolvedSuperTypes.mapNotNull { it.toRegularClassSymbol(session) }.forEach(::collect)
    }

    private fun thisReceiver(expression: FirExpression?): FirThisReceiverExpression? = when (expression) {
        is FirThisReceiverExpression -> expression.takeIf { it.calleeReference.symbol in hierarchy }
        is FirSmartCastExpression -> thisReceiver(expression.originalExpression)
        else -> null
    }

    private fun isThis(expression: FirExpression?): Boolean = thisReceiver(expression) != null

    private fun isInitializationStore(access: FirPropertyAccessExpression?): Boolean {
        if (access == null || !isThis(access.dispatchReceiver)) return false
        val property = access.toResolvedCallableSymbol() as? FirPropertySymbol ?: return false
        return property.getContainingClassSymbol() == owner && property.fir.hasBackingField && property.fir.setter?.body == null
    }

    /** Whether [access] reads a final stored property of [owner] that every path to it has assigned. */
    private fun isAssignedFieldRead(access: FirQualifiedAccessExpression): Boolean {
        if (access !is FirPropertyAccessExpression) return false
        val property = access.toResolvedCallableSymbol() as? FirPropertySymbol ?: return false
        return property.getContainingClassSymbol() == owner && property.resolvedStatus.modality == Modality.FINAL &&
            property.fir.getter?.body == null && phases.assignedBefore(property, access)
    }

    private fun inspectAssignment(element: FirVariableAssignment, phaseTwo: Boolean, inClosure: Boolean) {
        inspect(element.rValue, phaseTwo, inClosure)
        val target = (element.lValue as? FirDesugaredAssignmentValueReferenceExpression)?.expressionRef?.value ?: element.lValue
        if (!phaseTwo && target is FirPropertyAccessExpression && isThis(target.dispatchReceiver)) {
            if (isInitializationStore(target)) return
            failures += LawRule.CONSTRUCTION_CALL
            return
        }
        inspect(target, phaseTwo, inClosure)
    }

    private fun inspect(element: FirElement, inheritedPhaseTwo: Boolean, inClosure: Boolean = false) {
        val phaseTwo = if (inClosure) inheritedPhaseTwo else phases.phaseTwoBefore(element) ?: inheritedPhaseTwo
        if (element is FirVariableAssignment) {
            inspectAssignment(element, phaseTwo, inClosure)
            return
        }
        if (element is FirThisReceiverExpression && isThis(element)) {
            if (!phaseTwo) {
                failures += LawRule.CONSTRUCTION_THIS_USE
                return
            }
            // A callee that receives `this` of an open class may dispatch to an override that runs before the
            // subclass's construction.
            if (!owner.isFinal && element !in fixedReceivers) failures += LawRule.CONSTRUCTION_VIRTUAL_DISPATCH
        }
        if (element is FirQualifiedAccessExpression && isThis(element.dispatchReceiver)) {
            if (!phaseTwo) {
                if (isAssignedFieldRead(element)) return
                failures += if (element is FirFunctionCall) LawRule.CONSTRUCTION_CALL else LawRule.CONSTRUCTION_READ
                return
            }
            element.toResolvedCallableSymbol()?.let { callee ->
                if (!owner.isFixedConstructionDispatch(callee, session)) failures += LawRule.CONSTRUCTION_VIRTUAL_DISPATCH
                else {
                    thisReceiver(element.dispatchReceiver)?.let(fixedReceivers::add)
                    if (callee is FirNamedFunctionSymbol || callee is FirPropertySymbol) {
                        inspectMember(owner.effectiveConstructionMember(callee, session))
                    }
                }
            }
        }
        val closure = inClosure || element is FirAnonymousFunctionExpression || element is FirSimpleFunction
        element.acceptChildren(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) = inspect(element, phaseTwo, closure)
        })
    }

    /**
     * Inspects the body of a member that construction calls on `this`, so that the members it calls on `this` are
     * fixed too.
     */
    private fun inspectMember(member: FirCallableSymbol<*>) {
        if (member.getContainingClassSymbol() !in hierarchy || !inspectedMembers.add(member)) return
        val bodies = when (val declaration = member.fir) {
            is FirSimpleFunction -> listOf(declaration.body)
            is FirProperty -> listOf(declaration.getter?.body, declaration.setter?.body)
            else -> emptyList()
        }
        bodies.filterNotNull().forEach { inspect(it, true, inClosure = true) }
    }

    fun failures(): List<LawRule> {
        collect(owner)
        owner.fir.declarations.forEach { declaration ->
            when (declaration) {
                is FirProperty -> declaration.initializer?.let { inspect(it, false) }
                is FirAnonymousInitializer -> declaration.body?.let { inspect(it, false) }
                is FirConstructor -> {
                    declaration.delegatedConstructor?.argumentList?.arguments?.forEach { inspect(it, false, inClosure = true) }
                    declaration.body?.let { inspect(it, !declaration.isPrimary) }
                }
                else -> Unit
            }
        }
        if (!phases.isFullyAssigned) failures += LawRule.CONSTRUCTION_INCOMPLETE
        return failures.toList()
    }
}
