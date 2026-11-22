/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.purity

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.common.PurityCondition
import org.jetbrains.kotlin.formver.common.RelatedLocation
import org.jetbrains.kotlin.formver.core.diagnostics.ErrorCollectionContext
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding

/**
 * Reports at the position of the impure expression when it is in the file of [anchor], else at [anchor] with the
 * expression's position as a related location.
 */
class DefaultPurityContext(
    private val source: KtSourceElement,
    private val anchor: KtSourceElement,
    private val errors: ErrorCollectionContext,
) : PurityContext {
    override fun addPurityError(embedding: ExpEmbedding, condition: PurityCondition) {
        val position = embedding.expressionSource(source)
        if (position.isInFileOf(anchor)) errors.reportPurityViolation(position, condition)
        else errors.reportPurityViolation(anchor, condition.copy(inlined = true), listOf(RelatedLocation("inlined expression", position)))
    }
}
