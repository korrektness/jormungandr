/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.expression

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.conversion.StmtConversionContext
import org.jetbrains.kotlin.formver.core.embeddings.ExpVisitor
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding

/** A function value with a statically known adapter. It must not enter the opaque function-object encoding. */
class CallableReferenceValue(
    override val type: TypeEmbedding,
    private val source: KtSourceElement?,
    private val target: String,
    val capture: Declare?,
    private val invoke: StmtConversionContext.(List<ExpEmbedding>, TypeEmbedding) -> ExpEmbedding,
) : ExpEmbedding {
    fun captured(): CallableReferenceValue = CallableReferenceValue(type, source, target, null, invoke)

    fun call(args: List<ExpEmbedding>, result: TypeEmbedding, ctx: StmtConversionContext): ExpEmbedding {
        val invocation = ctx.invoke(args, result)
        return capture?.let { blockOf(it, invocation) } ?: invocation
    }

    fun unsupportedUse(): Nothing =
        throw UnsupportedFeatureException(source, "callable reference to $target cannot be materialized as a function value")

    override fun <R> accept(v: ExpVisitor<R>): R = v.visitCallableReferenceValue(this)
}
