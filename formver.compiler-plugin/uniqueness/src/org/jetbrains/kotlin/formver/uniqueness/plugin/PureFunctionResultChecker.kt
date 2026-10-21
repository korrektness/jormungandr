/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.formver.common.reportAttributeFailure
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirOperation
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirTypeOperatorCall
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.intrinsics.plugin.genericArrayGetId
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.formver.readonly.plugin.isPure
import org.jetbrains.kotlin.formver.uniqueness.attribute.uniquenessAttribute
import org.jetbrains.kotlin.formver.common.isExternalSpecificationFile
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors.INVALID_PURE_REFERENCE_RESULT
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessErrors.INVALID_PURE_UNIQUE_RESULT

private val FirTypeRef.isUnique: Boolean
    get() = coneType.attributes.uniquenessAttribute != null

/**
 * Checks the result of a `@Pure` function. It may not be `@Unique`, and when the function has a `@Unique` parameter,
 * receiver or context parameter its reference results must be shared array elements.
 * Ownership and cast safety are checked during conversion.
 */
object PureFunctionResultChecker : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        if (!declaration.symbol.isPure(context.session)) return
        // A library model describes a result the library computes, which may alias the receiver's elements.
        if (context.session.isExternalSpecificationFile(context.containingFilePath)) return

        val returnTypeRef = declaration.returnTypeRef
        val source = returnTypeRef.source ?: declaration.source

        if (returnTypeRef.isUnique) {
            reporter.reportAttributeFailure(source, INVALID_PURE_UNIQUE_RESULT)
            return
        }

        val parameterTypeRefs =
            listOfNotNull(declaration.receiverParameter?.typeRef) +
                    declaration.contextParameters.map { it.returnTypeRef } +
                    declaration.valueParameters.map { it.returnTypeRef }

        if (parameterTypeRefs.any { it.isUnique } && !returnTypeRef.coneType.isValueType() &&
            !declaration.returnsArrayElements()) {
            reporter.reportAttributeFailure(source, INVALID_PURE_REFERENCE_RESULT)
        }
    }
}

private fun FirExpression.isArrayElement(): Boolean = when (this) {
    is FirFunctionCall -> toResolvedCallableSymbol()?.callableId == genericArrayGetId
    is FirTypeOperatorCall -> operation == FirOperation.AS && arguments.single().isArrayElement()
    else -> false
}

private fun FirFunction.returnsArrayElements(): Boolean {
    var found = false
    var allElements = true
    body?.accept(object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) = element.acceptChildren(this)
        override fun visitReturnExpression(returnExpression: FirReturnExpression) {
            if (returnExpression.target.labeledElement == this@returnsArrayElements) {
                found = true
                allElements = allElements && returnExpression.result.isArrayElement()
            }
            returnExpression.acceptChildren(this)
        }
    })
    return found && allElements
}
