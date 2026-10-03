/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.directOverriddenSymbolsSafe
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.getSingleMatchedExpectForActualOrNull
import org.jetbrains.kotlin.fir.declarations.utils.isActual
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.formver.locality.plugin.borrowsDispatchReceiver
import org.jetbrains.kotlin.formver.locality.plugin.locality
import org.jetbrains.kotlin.formver.locality.plugin.ownsDispatchReceiver
import org.jetbrains.kotlin.formver.uniqueness.attribute.uniquenessAttribute
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors.ACTUAL_UNIQUENESS_MISMATCH
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors.OVERRIDE_UNIQUENESS_MISMATCH

/**
 * Checks that an override repeats the `@Unique` and `@Borrowed` annotations of every declaration it directly overrides,
 * on its value parameters, extension receiver and result, and on the function itself for its dispatch receiver.
 */
object OverrideUniquenessChecker : FirCallableDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirCallableDeclaration) {
        if (!declaration.isOverride) return
        checkMatchingOwnership(declaration, declaration.symbol.directOverriddenSymbolsSafe(), OVERRIDE_UNIQUENESS_MISMATCH)
    }
}

/**
 * Checks that an `actual` declaration repeats the `@Unique` and `@Borrowed` annotations of its `expect` declaration,
 * on its value parameters, extension receiver and result. Callers in common code see only the `expect` signature.
 */
object ActualUniquenessChecker : FirCallableDeclarationChecker(MppCheckerKind.Platform) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirCallableDeclaration) {
        if (!declaration.isActual) return
        val expect = declaration.symbol.getSingleMatchedExpectForActualOrNull() as? FirCallableSymbol<*> ?: return
        checkMatchingOwnership(declaration, listOf(expect), ACTUAL_UNIQUENESS_MISMATCH)
    }
}

/**
 * Reports [factory] on each type of [declaration] whose ownership annotations differ from the corresponding type of
 * one of [counterparts], and on [declaration] when its dispatch receiver annotations differ from one of theirs.
 */
context(context: CheckerContext, reporter: DiagnosticReporter)
private fun checkMatchingOwnership(
    declaration: FirCallableDeclaration,
    counterparts: List<FirCallableSymbol<*>>,
    factory: KtDiagnosticFactory0,
) {
    val mismatchingTypeRefs = mutableSetOf<FirTypeRef>()
    for (counterpart in counterparts) {
        fun compare(typeRef: FirTypeRef?, counterpartType: ConeKotlinType?) {
            if (typeRef == null || counterpartType == null) return
            if (typeRef.coneType.ownershipAnnotations != counterpartType.ownershipAnnotations) {
                mismatchingTypeRefs.add(typeRef)
            }
        }

        compare(declaration.returnTypeRef, counterpart.resolvedReturnType)
        compare(declaration.receiverParameter?.typeRef, counterpart.receiverParameterSymbol?.resolvedType)
        if (declaration is FirFunction && counterpart is FirFunctionSymbol<*>) {
            declaration.valueParameters.zip(counterpart.valueParameterSymbols) { parameter, counterpartParameter ->
                compare(parameter.returnTypeRef, counterpartParameter.resolvedReturnType)
            }
        }
    }

    val session = context.session
    val dispatchReceiverOwnership = { symbol: FirCallableSymbol<*> ->
        Pair(symbol.ownsDispatchReceiver(session), symbol.borrowsDispatchReceiver(session))
    }
    if (declaration.dispatchReceiverType != null &&
        counterparts.any { dispatchReceiverOwnership(it) != dispatchReceiverOwnership(declaration.symbol) }
    ) {
        reporter.reportOn(declaration.source, factory)
    }

    for (typeRef in mismatchingTypeRefs) {
        val source = typeRef.source?.takeUnless { it.kind is KtFakeSourceElementKind } ?: declaration.source
        reporter.reportOn(source, factory)
    }
}

/**
 * Whether [this] type is annotated `@Unique`, and whether it is annotated `@Borrowed`.
 */
private val ConeKotlinType.ownershipAnnotations: Pair<Boolean, Boolean>
    get() = Pair(attributes.uniquenessAttribute != null, attributes.locality != null)
