/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.directOverriddenSymbolsSafe
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.formver.locality.plugin.locality
import org.jetbrains.kotlin.formver.uniqueness.attribute.uniquenessAttribute
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors.OVERRIDE_UNIQUENESS_MISMATCH

/**
 * Checks that an override repeats the `@Unique` and `@Borrowed` annotations of every declaration it directly overrides,
 * on its value parameters, extension receiver and result.
 */
object OverrideUniquenessChecker : FirCallableDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirCallableDeclaration) {
        if (!declaration.isOverride) return

        val mismatchingTypeRefs = mutableSetOf<FirTypeRef>()
        for (overridden in declaration.symbol.directOverriddenSymbolsSafe()) {
            fun compare(typeRef: FirTypeRef?, overriddenType: ConeKotlinType?) {
                if (typeRef == null || overriddenType == null) return
                if (typeRef.coneType.ownershipAnnotations != overriddenType.ownershipAnnotations) {
                    mismatchingTypeRefs.add(typeRef)
                }
            }

            compare(declaration.returnTypeRef, overridden.resolvedReturnType)
            compare(declaration.receiverParameter?.typeRef, overridden.receiverParameterSymbol?.resolvedType)
            if (declaration is FirFunction && overridden is FirFunctionSymbol<*>) {
                declaration.valueParameters.zip(overridden.valueParameterSymbols) { parameter, overriddenParameter ->
                    compare(parameter.returnTypeRef, overriddenParameter.resolvedReturnType)
                }
            }
        }

        for (typeRef in mismatchingTypeRefs) {
            val source = typeRef.source?.takeUnless { it.kind is KtFakeSourceElementKind } ?: declaration.source
            reporter.reportOn(source, OVERRIDE_UNIQUENESS_MISMATCH)
        }
    }

    /**
     * Whether [this] type is annotated `@Unique`, and whether it is annotated `@Borrowed`.
     */
    private val ConeKotlinType.ownershipAnnotations: Pair<Boolean, Boolean>
        get() = Pair(attributes.uniquenessAttribute != null, attributes.locality != null)
}
