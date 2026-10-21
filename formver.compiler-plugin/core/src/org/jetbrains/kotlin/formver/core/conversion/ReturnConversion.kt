/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.impl.FirUnitExpression
import org.jetbrains.kotlin.formver.common.PurityCondition
import org.jetbrains.kotlin.formver.common.PurityRule
import org.jetbrains.kotlin.formver.core.embeddings.callables.NamedFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.Return
import org.jetbrains.kotlin.formver.core.embeddings.expression.UnitLit
import org.jetbrains.kotlin.formver.core.embeddings.expression.withType
import org.jetbrains.kotlin.formver.core.linearization.ownedPath
import org.jetbrains.kotlin.formver.core.purity.isPure

internal fun StmtConversionContext.convertReturn(expression: FirReturnExpression): ExpEmbedding {
    val value = when (expression.result) {
        is FirUnitExpression -> UnitLit
        else -> convert(expression.result)
    }
    checkPureResult(expression, value)
    val target = resolveReturnTarget(expression.target.labelName)
    return Return(value.withType(target.variable.type), target)
}

private fun StmtConversionContext.checkPureResult(expression: FirReturnExpression, value: ExpEmbedding) {
    if (!signature.isPure || signature.formalArgs.none { it.isUnique }) return
    if ((signature as? NamedFunctionSignature)?.symbol != expression.target.labeledElement.symbol) return
    // Shared observations carry no owned predicate; only returning an owned path aliases the borrow.
    val path = value.ownedPath() ?: return
    if (value.isPure() && path.root.isUnique) {
        reportPurityViolation(expression.source, PurityCondition(PurityRule.FUNCTION_BODY))
    }
}
