/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.analysis.checkers.directOverriddenSymbolsSafe
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.isIntersectionOverride
import org.jetbrains.kotlin.fir.scopes.ScopeFunctionRequiresPrewarm
import org.jetbrains.kotlin.fir.scopes.getDirectOverriddenFunctions
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isBoolean
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.core.conversion.classLawConstructionFailures
import org.jetbrains.kotlin.formver.core.conversion.ownClassLaws
import org.jetbrains.kotlin.formver.core.conversion.hasSuperclassLaws
import org.jetbrains.kotlin.formver.core.conversion.isLawValueOperation
import org.jetbrains.kotlin.formver.core.conversion.classLawObserverBody
import org.jetbrains.kotlin.formver.core.conversion.classLawObserverFailure
import org.jetbrains.kotlin.formver.core.conversion.isLawReadableFrom
import org.jetbrains.kotlin.formver.core.isLaw
import org.jetbrains.kotlin.formver.core.isObserverOrOverride
import org.jetbrains.kotlin.formver.core.isGhostObserver
import org.jetbrains.kotlin.formver.core.isPure

context(context: CheckerContext)
private fun FirCallableSymbol<*>.isLawOrOverride(): Boolean =
    isLaw(context.session) || directOverriddenSymbolsSafe().any { it.isLawOrOverride() }

object LawImplementationChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    @OptIn(ScopeFunctionRequiresPrewarm::class)
    override fun check(declaration: FirRegularClass) {
        if (!declaration.classKind.isInterface &&
            (declaration.symbol.ownClassLaws(context.session).isNotEmpty() || declaration.symbol.hasSuperclassLaws(context.session))) {
            val failures = classLawConstructionFailures(declaration.symbol, context.session)
            if (failures.isNotEmpty()) reporter.reportOn(declaration.source, PluginErrors.INVALID_LAW, failures.joinToString("; ") + ".")
        }
        val scope = declaration.symbol.unsubstitutedScope()
        for (name in scope.getCallableNames()) {
            scope.processFunctionsByName(name) { member ->
                if (member.getContainingClassSymbol() != declaration.symbol) return@processFunctionsByName
                val overridden = scope.getDirectOverriddenFunctions(member)
                if (overridden.none { it.isLawOrOverride() }) return@processFunctionsByName
                val inheritedImplementation = member.isIntersectionOverride && overridden.any {
                    val implementation = it.unwrapFakeOverrides()
                    !implementation.isAbstract && !implementation.isLawOrOverride()
                }
                if (member.origin == FirDeclarationOrigin.Delegated || inheritedImplementation) {
                    reporter.reportOn(declaration.source, PluginErrors.INVALID_LAW, "Implicit law override.")
                }
            }
        }
    }
}

object LawDeclarationChecker : FirSimpleFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    @OptIn(SymbolInternals::class)
    override fun check(declaration: FirSimpleFunction) {
        val symbol = declaration.symbol
        val failures = mutableListOf<String>()
        if (declaration.isOverride && symbol.directOverriddenSymbolsSafe().any { it.isLawOrOverride() }) {
            failures.add("Overridden law")
        }
        val isLaw = symbol.isLaw(context.session)
        val owner = symbol.getContainingClassSymbol() as? FirClassSymbol<*>
        if (isLaw) {
            if (owner !is FirRegularClassSymbol) failures.add("Law declared outside a class or interface")
            if (!symbol.isPure(context.session)) failures.add("Missing @Pure on law")
            if (!declaration.returnTypeRef.coneType.isBoolean) failures.add("Non-Boolean law result")
            if (declaration.body == null) failures.add("Bodiless law")
            val hasReceiverParameters = declaration.receiverParameter != null || declaration.contextParameters.isNotEmpty()
            if (declaration.valueParameters.isNotEmpty() || hasReceiverParameters || declaration.typeParameters.isNotEmpty()) {
                failures.add("Law parameters")
            }
        }
        if (failures.isNotEmpty()) {
            reporter.reportOn(declaration.source, PluginErrors.INVALID_LAW, failures.joinToString("; ") + ".")
        }
        if (!isLaw) return
        declaration.body?.accept(object : FirVisitorVoid() {
            private val values = mutableSetOf<FirCallableSymbol<*>>()
            private val expanding = mutableSetOf<FirCallableSymbol<*>>()
            private var expansionSource: org.jetbrains.kotlin.KtSourceElement? = null
            private val classOwner = (owner as? FirRegularClassSymbol)?.takeUnless { it.classKind.isInterface }

            private fun reject(element: FirElement, reason: String) {
                reporter.reportOn(expansionSource ?: element.source, PluginErrors.INVALID_LAW, reason)
            }

            private fun expand(element: FirQualifiedAccessExpression, callee: FirCallableSymbol<*>) {
                val failure = classLawObserverFailure(callee)
                if (failure != null) {
                    reject(element, "$failure.")
                    return
                }
                val body = classLawObserverBody(callee)!!
                if (!expanding.add(body)) {
                    reject(element, "Recursive observer in class law.")
                    return
                }
                val savedValues = values.toSet()
                val savedSource = expansionSource
                expansionSource = expansionSource ?: element.source
                values.addAll(body.valueParameterSymbols)
                body.fir.body!!.accept(this)
                values.clear()
                values.addAll(savedValues)
                expansionSource = savedSource
                expanding.remove(body)
            }

            private fun FirExpression?.isOwnThis(): Boolean =
                this is FirThisReceiverExpression &&
                    (calleeReference.symbol == owner || expanding.any { it.getContainingClassSymbol() == calleeReference.symbol })

            override fun visitElement(element: FirElement) {
                when (element) {
                    is FirProperty -> {
                        if (element.isVar || element.delegate != null) {
                            reject(element, "Mutable local in law.")
                        } else values.add(element.symbol)
                        element.initializer?.accept(this)
                    }
                    is FirVariableAssignment -> {
                        reject(element, "Assignment in law.")
                        element.rValue.accept(this)
                    }
                    is FirQualifiedAccessExpression -> {
                        val callee = element.toResolvedCallableSymbol()
                        // Observers reached through another observer's result are deliberately out of scope.
                        val ownAccess = element.dispatchReceiver.isOwnThis() && element.extensionReceiver == null
                        val field = classOwner != null && ownAccess && callee is FirPropertySymbol &&
                                callee.fir.backingField != null && callee.fir.getter?.body == null
                        val observer = ownAccess && (callee?.isObserverOrOverride() == true ||
                                callee?.isGhostObserver(context.session) == true ||
                                (classOwner != null && callee?.isPure(context.session) == true))
                        if (field) {
                            if (!(callee as FirPropertySymbol).isLawReadableFrom(classOwner!!, context.session)) {
                                reject(element, "Invalid inherited var read; see Class laws.")
                            }
                        }
                        if (observer && classOwner != null && !field) expand(element, callee!!)
                        val calleeOwner = callee?.getContainingClassSymbol() as? FirRegularClassSymbol
                        val companionHelper = calleeOwner?.fir?.isCompanion == true
                        val pureHelper = element is FirFunctionCall && callee?.isPure(context.session) == true &&
                                (callee.callableId?.let { it.className == null && !it.isLocal } == true || companionHelper)
                        val value = callee in values || (callee is FirPropertySymbol && callee.isConst)
                        val operation = element.isLawValueOperation()
                        val allowedAccess = observer || field || value || operation
                        if (!allowedAccess && !pureHelper) {
                            reject(element, "Non-value read or call in law.")
                        }
                        if (operation || !element.dispatchReceiver.isOwnThis()) {
                            element.dispatchReceiver?.accept(this)
                        }
                        element.extensionReceiver?.accept(this)
                        element.contextArguments.forEach { it.accept(this) }
                        if (element is FirFunctionCall) element.argumentList.arguments.forEach { it.accept(this) }
                    }
                    else -> element.acceptChildren(this)
                }
            }
        })
    }

}
