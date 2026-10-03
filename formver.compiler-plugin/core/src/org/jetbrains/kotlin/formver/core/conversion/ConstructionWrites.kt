/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertySetter
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirSuperReceiverExpression
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid

/**
 * What the property initializers and `init` blocks of [this] class and its superclasses may do to the object under
 * construction after a primary constructor stores its parameters. They assign the properties in [assigned], read or
 * assign those in [accessed], and when [usesThis], use `this` other than to read or assign a field, so that a member,
 * an accessor or a borrowing callee may read or write any field.
 */
class ConstructionWrites(
    val assigned: Set<FirPropertySymbol>,
    val accessed: Set<FirPropertySymbol>,
    val usesThis: Boolean,
) {
    /** Whether [property] may hold another value once construction ends. */
    fun mayChange(property: FirPropertySymbol): Boolean = property in assigned || (usesThis && property.isVar)

    /** Whether construction code may reach the value [property] holds, and so change what that value refers to. */
    fun mayReach(property: FirPropertySymbol): Boolean = property in accessed || usesThis
}

@OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
fun FirRegularClassSymbol.constructionWrites(session: FirSession): ConstructionWrites {
    val finder = ConstructionWriteFinder(isFinal)
    val pending = mutableListOf(this)
    val seen = mutableSetOf<FirRegularClassSymbol>()
    while (pending.isNotEmpty()) {
        val classSymbol = pending.removeLast()
        if (!seen.add(classSymbol)) continue
        finder.classSymbol = classSymbol
        for (declaration in classSymbol.fir.declarations) {
            when (declaration) {
                is FirAnonymousInitializer -> declaration.body?.accept(finder)
                is FirProperty -> {
                    declaration.initializer?.accept(finder)
                    declaration.delegate?.accept(finder)
                }
                else -> {}
            }
        }
        classSymbol.resolvedSuperTypes.mapNotNullTo(pending) { it.toRegularClassSymbol(session) }
    }
    return ConstructionWrites(finder.assigned, finder.accessed, finder.usesThis)
}

/** [constructedIsFinal]: whether the class whose construction is searched is final, so no override replaces its accessors. */
private class ConstructionWriteFinder(private val constructedIsFinal: Boolean) : FirVisitorVoid() {
    lateinit var classSymbol: FirRegularClassSymbol
    val assigned = mutableSetOf<FirPropertySymbol>()
    val accessed = mutableSetOf<FirPropertySymbol>()
    var usesThis = false

    private fun FirElement.isThis(): Boolean =
        this is FirSuperReceiverExpression ||
                (this is FirThisReceiverExpression && calleeReference.symbol == classSymbol)

    /** Whether [this] access on the object under construction reaches a backing field and no accessor code. */
    private val FirQualifiedAccessExpression.accessesField: Boolean
        @OptIn(SymbolInternals::class)
        get() {
            val property = toResolvedCallableSymbol() as? FirPropertySymbol ?: return false
            return dispatchReceiver?.isThis() == true && !property.isAbstract &&
                    (property.isFinal || constructedIsFinal) &&
                    property.getterSymbol?.fir.let { it == null || it is FirDefaultPropertyGetter } &&
                    property.setterSymbol?.fir.let { it == null || it is FirDefaultPropertySetter }
        }

    override fun visitElement(element: FirElement) {
        when {
            element is FirVariableAssignment -> {
                val target = element.lValue as? FirQualifiedAccessExpression
                if (target != null && target.accessesField) assigned.add(target.toResolvedCallableSymbol() as FirPropertySymbol)
                element.acceptChildren(this)
            }
            element is FirPropertyAccessExpression && element.accessesField -> {
                accessed.add(element.toResolvedCallableSymbol() as FirPropertySymbol)
                element.extensionReceiver?.takeIf { it !== element.dispatchReceiver }?.accept(this)
            }
            element.isThis() -> usesThis = true
            else -> element.acceptChildren(this)
        }
    }
}
