/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.purity

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.core.diagnostics.ErrorCollectionContext
import org.jetbrains.kotlin.formver.core.embeddings.expression.Assert
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.VariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.WithPosition
import org.jetbrains.kotlin.formver.core.embeddings.types.MultisetTypeEmbedding

/**
 * Reports each outermost `Multiset` value computed in [this] impure body outside its `verify` conditions; a variable
 * is reported where its value is computed. A
 * `Multiset` exists only in specifications: it has no runtime representation. Loop invariants are not children of
 * their loop, so they are not visited.
 */
fun ExpEmbedding.checkNoMultisetValues(source: KtSourceElement, errors: ErrorCollectionContext) {
    val nextSource = (this as? WithPosition)?.source ?: source
    when {
        this is Assert || this is VariableEmbedding -> {}
        type.pretype == MultisetTypeEmbedding ->
            errors.reportUnsupportedFeature(nextSource, "a `Multiset` outside a specification")
        else -> children().forEach { it.checkNoMultisetValues(nextSource, errors) }
    }
}
