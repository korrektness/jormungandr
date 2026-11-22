/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.formver.core.embeddings.ExpVisitor

/** [result], a declared local of a class type, given the full unique predicate of that class and owned from here. */
data class UnconstrainedOwnedValue(val result: VariableEmbedding) : ExpEmbedding {
    override val type = result.type
    override fun children() = emptySequence<ExpEmbedding>()
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitUnconstrainedOwnedValue(this)
}
