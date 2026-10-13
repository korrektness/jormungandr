/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.purity

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.core.diagnostics.ErrorCollectionContext
import org.jetbrains.kotlin.formver.core.embeddings.expression.Assert
import org.jetbrains.kotlin.formver.core.embeddings.expression.Refute
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.VariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.WithPosition
import org.jetbrains.kotlin.formver.core.embeddings.types.MultisetTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.IterationSequenceTypeEmbedding

/**
 * Reports specification-only values computed in an impure body outside its verification probes. Variables are
 * reported where their values are computed. Loop invariants are not children of their loop.
 */
fun ExpEmbedding.checkNoSpecificationValues(source: KtSourceElement, errors: ErrorCollectionContext) {
    val nextSource = (this as? WithPosition)?.source ?: source
    when {
        this is Assert || this is Refute || this is VariableEmbedding -> {}
        type.pretype == MultisetTypeEmbedding ->
            errors.reportUnsupportedFeature(nextSource, "a `Multiset` outside a specification",
                org.jetbrains.kotlin.formver.common.UnsupportedReason.MULTISET_OUTSIDE_SPECIFICATION)
        type.pretype == IterationSequenceTypeEmbedding ->
            errors.reportUnsupportedFeature(nextSource, "an `IterationSequence` outside a specification",
                org.jetbrains.kotlin.formver.common.UnsupportedReason.ITERATION_SEQUENCE_OUTSIDE_SPECIFICATION)
        else -> children().forEach { it.checkNoSpecificationValues(nextSource, errors) }
    }
}
