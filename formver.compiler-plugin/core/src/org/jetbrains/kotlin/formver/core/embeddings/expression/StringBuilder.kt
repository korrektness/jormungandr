/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.formver.core.embeddings.ExpVisitor
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType

/**
 * An operation on a `StringBuilder`. [receiverOwned] says whether the uniqueness checker finds [builder] `Unique`
 * before the operation: only then does it read or change the contents.
 */
sealed interface StringBuilderOperation : ExpEmbedding {
    val builder: ExpEmbedding
    val receiverOwned: Boolean
}

/** An operation that reads the contents and changes nothing. */
sealed interface StringBuilderRead : StringBuilderOperation

/**
 * An operation that returns its receiver: its result is [builder] itself, and denotes the same path.
 */
sealed interface StringBuilderUpdate : StringBuilderOperation

/** The number of characters in a `StringBuilder`. */
data class StringBuilderLength(override val builder: ExpEmbedding, override val receiverOwned: Boolean) :
    StringBuilderRead {
    override val type: TypeEmbedding = buildType { int() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(builder)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitStringBuilderLength(this)
}

/** The contents of a `StringBuilder`, as a `String`. */
data class StringBuilderToString(override val builder: ExpEmbedding, override val receiverOwned: Boolean) :
    StringBuilderRead {
    override val type: TypeEmbedding = buildType { string() }

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(builder)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitStringBuilderToString(this)
}

/** Appends [value], a `Char` or a `String`, to a `StringBuilder`. */
data class StringBuilderAppend(
    override val builder: ExpEmbedding,
    val value: ExpEmbedding,
    override val receiverOwned: Boolean,
) : StringBuilderUpdate {
    override val type: TypeEmbedding = builder.type

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(builder, value)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitStringBuilderAppend(this)
}

/** Removes every character from a `StringBuilder`. */
data class StringBuilderClear(override val builder: ExpEmbedding, override val receiverOwned: Boolean) :
    StringBuilderUpdate {
    override val type: TypeEmbedding = builder.type

    override fun children(): Sequence<ExpEmbedding> = sequenceOf(builder)
    override fun <R> accept(v: ExpVisitor<R>): R = v.visitStringBuilderClear(this)
}
