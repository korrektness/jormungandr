/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.purity

import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.core.diagnostics.ErrorCollectionContext
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.SharingContext
import org.jetbrains.kotlin.formver.core.embeddings.expression.WithPosition
import org.jetbrains.kotlin.formver.core.exhaustiveAll

/**
 * Positioning information for a node is stored in the closest parent `WithPosition` node.
 *
 * @return a preordered sequence of pairs, each containing the `ExpEmbedding` and its FIR source as a `KtSourceElement`
 */
fun ExpEmbedding.preorder(currentSource: KtSourceElement? = null): Sequence<Pair<ExpEmbedding, KtSourceElement?>> =
    sequence {
        val nextSource = (this@preorder as? WithPosition)?.source ?: currentSource

        if (this@preorder !is WithPosition) yield(Pair(this@preorder, nextSource))

        yieldAll(this@preorder.children().flatMap { it.preorder(currentSource = nextSource) })
    }

/**
 * Validates all nodes using the isValid function.
 * Avoids `all` to prevent short-circuiting, ensuring all errors are reported.
 * A node is reported in the file of [source]: at its position there, or, when its position is in another file, at the
 * closest enclosing position in that file.
 */
fun ExpEmbedding.checkValidity(source: KtSourceElement?, errors: ErrorCollectionContext): Boolean =
    anchoredPreorder(source, source, source)
        .exhaustiveAll { (embedding, position, anchor) ->
            val position = checkNotNull(position) {
                "Purity-check expected a KtSourceElement, but none was present"
            }
            embedding.isValid(DefaultPurityContext(position, anchor ?: position, errors))
        }

/** Like [preorder], with each node's closest position in the file of [root] as the third component. */
private fun ExpEmbedding.anchoredPreorder(
    root: KtSourceElement?,
    currentSource: KtSourceElement?,
    currentAnchor: KtSourceElement?,
): Sequence<Triple<ExpEmbedding, KtSourceElement?, KtSourceElement?>> = sequence {
    val position = (this@anchoredPreorder as? WithPosition)?.source
    val nextSource = position ?: currentSource
    val nextAnchor = position?.takeIf { root == null || it.isInFileOf(root) } ?: currentAnchor
    if (this@anchoredPreorder !is WithPosition) yield(Triple(this@anchoredPreorder, nextSource, nextAnchor))
    yieldAll(this@anchoredPreorder.children().flatMap { it.anchoredPreorder(root, nextSource, nextAnchor) })
}

/** Whether [this] and [other] are positions in the same source file. */
internal fun KtSourceElement.isInFileOf(other: KtSourceElement): Boolean = when {
    this is KtPsiSourceElement && other is KtPsiSourceElement -> psi.containingFile == other.psi.containingFile
    this is KtLightSourceElement && other is KtLightSourceElement -> treeStructure === other.treeStructure
    else -> false
}

/**
 * Whether [this] has no side effects. With [admitsInlinedCalls], an inlined call whose body has none counts as pure.
 */
fun ExpEmbedding.isPure(admitsInlinedCalls: Boolean = false): Boolean =
    this.accept(ExprPurityVisitor(admitsInlinedCalls))

/**
 * Returns the `KtSourceElement` of the outermost `WithPosition`
 * inside this expression; returns [fallback] if none is found.
 */
internal fun ExpEmbedding.expressionSource(fallback: KtSourceElement): KtSourceElement {
    var curr: ExpEmbedding = this@expressionSource
    while (true) {
        if (curr is WithPosition) return curr.source
        else if (curr is SharingContext) curr = curr.inner
        else break
    }
    return fallback
}
