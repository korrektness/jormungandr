/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.purity

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.core.conversion.AccessPolicy
import org.jetbrains.kotlin.formver.core.diagnostics.ErrorCollectionContext
import org.jetbrains.kotlin.formver.core.embeddings.expression.Assert
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.FieldAccess
import org.jetbrains.kotlin.formver.core.embeddings.expression.IntArrayGet
import org.jetbrains.kotlin.formver.core.embeddings.expression.Old
import org.jetbrains.kotlin.formver.core.embeddings.expression.StringBuilderRead
import org.jetbrains.kotlin.formver.core.embeddings.expression.UniqueValAccess
import org.jetbrains.kotlin.formver.core.embeddings.expression.VariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.While
import org.jetbrains.kotlin.formver.core.embeddings.expression.WithPosition
import org.jetbrains.kotlin.formver.viper.SymbolicName

/**
 * Reports the `var` reads, array element reads and `StringBuilder` reads in [this] read-only context (a pure function body or a
 * specification) that no unique predicate covers: a read through a receiver the uniqueness checker does not find
 * `Unique`, and, outside `old`, a read through a path rooted at one of [consumed].
 */
fun ExpEmbedding.checkReadOnlyVarReads(
    source: KtSourceElement,
    errors: ErrorCollectionContext,
    consumed: Set<SymbolicName> = emptySet(),
) {
    val nextSource = (this as? WithPosition)?.source ?: source
    fun checkOwnedRead(receiver: ExpEmbedding, receiverOwned: Boolean, read: String, owner: String) = when {
        !receiverOwned -> errors.reportUnsupportedOwnership(nextSource, "Reading $read needs a @Unique $owner.")
        receiver.pathRoot()?.name in consumed -> errors.reportUnsupportedOwnership(
            nextSource,
            "Reading $read in a postcondition needs a @Borrowed root; the function consumes this @Unique parameter.",
        )
        else -> {}
    }
    when {
        this is FieldAccess && field.accessPolicy == AccessPolicy.BY_RECEIVER_UNIQUENESS ->
            checkOwnedRead(receiver, receiverOwned, "this var property", "receiver")
        this is IntArrayGet -> checkOwnedRead(array, receiverOwned, "an array element", "array")
        this is StringBuilderRead -> checkOwnedRead(builder, receiverOwned, "a StringBuilder", "builder")
    }
    val childConsumed = if (this is Old) emptySet() else consumed
    children().forEach { it.checkReadOnlyVarReads(nextSource, errors, childConsumed) }
}

/**
 * Applies [checkReadOnlyVarReads] to the loop invariants and `verify` conditions of [this] impure body.
 */
fun ExpEmbedding.checkSpecificationVarReads(source: KtSourceElement, errors: ErrorCollectionContext) {
    preorder(source).forEach { (embedding, embeddingSource) ->
        val specSource = embeddingSource ?: source
        when (embedding) {
            is While -> embedding.invariants.forEach { it.checkReadOnlyVarReads(specSource, errors) }
            is Assert -> embedding.exp.checkReadOnlyVarReads(specSource, errors)
            else -> {}
        }
    }
}

private fun ExpEmbedding.pathRoot(): VariableEmbedding? = when (val exp = ignoringCastsAndMetaNodes()) {
    is VariableEmbedding -> exp
    is FieldAccess -> exp.receiver.pathRoot()
    is UniqueValAccess -> exp.receiver.pathRoot()
    else -> null
}
