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
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.types.ConeDefinitelyNotNullType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.formver.uniqueness.attribute.uniquenessAttribute
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors.INVALID_TYPE_PARAMETER_UNIQUENESS

/**
 * Checks that no declaration whose type is a type parameter is declared `@Unique`: predicates are per erased class, so
 * ownership cannot cross erasure.
 *
 * The declared type of a property, local, parameter or function result is checked, and so is the type of its extension
 * receiver. Inferred types are skipped, since they take their uniqueness from a declaration checked on its own.
 */
object TypeParameterUniquenessChecker : FirCallableDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirCallableDeclaration) {
        if (declaration.source?.kind is KtFakeSourceElementKind) return

        val typeRefs = listOfNotNull(declaration.returnTypeRef, declaration.receiverParameter?.typeRef)
        for (typeRef in typeRefs) {
            if (typeRef.isUniqueTypeParameter) {
                reporter.reportOn(typeRef.source, INVALID_TYPE_PARAMETER_UNIQUENESS)
            }
        }
    }

    private val FirTypeRef.isUniqueTypeParameter: Boolean
        get() {
            val source = source ?: return false
            if (source.kind is KtFakeSourceElementKind) return false

            val type = coneType
            return type.attributes.uniquenessAttribute != null && type.isTypeParameter
        }

    private val ConeKotlinType.isTypeParameter: Boolean
        get() = when (val type = lowerBoundIfFlexible()) {
            is ConeTypeParameterType -> true
            is ConeDefinitelyNotNullType -> type.original.isTypeParameter
            else -> false
        }
}
