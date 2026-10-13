/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.core.embeddings.LabelLink
import org.jetbrains.kotlin.formver.core.embeddings.toLink
import org.jetbrains.kotlin.formver.core.embeddings.toViper
import org.jetbrains.kotlin.formver.core.embeddings.toViperGoto
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType

/** A single finalizer joins throwing states and preserves the pending completion until dispatch. */
internal class FinallyLinearizable(
    private val e: TryFinally,
    private val visitor: LinearizationVisitor,
) : OptionalResultLinearizable(e) {
    override fun toViperMaybeStoringIn(result: VariableEmbedding?, ctx: LinearizationContext) {
        val interceptors = ctx.jumpInterceptors ?: throw SnaktInternalException(ctx.source, "Finally needs statement linearization")
        val pending = ctx.freshAnonVar(buildType { int() })
        val jumps = linkedMapOf<LabelLink, Int>()
        fun setPending(tag: Int, context: LinearizationContext) {
            context.store(pending, IntLit(tag).accept(visitor))
        }
        val interceptor = JumpInterceptor(e.body.placedLabels() + e.exceptionEntry.name + e.finalEntry.name) { target, context ->
            val tag = jumps.getOrPut(target) { jumps.size + 2 }
            setPending(tag, context)
            e.finalEntry.toLink()
        }
        interceptors.add(interceptor)
        try {
            e.body.accept(visitor).toViperMaybeStoringIn(result, ctx)
            setPending(0, ctx)
            ctx.addStatement { e.finalEntry.toLink().toViperGoto(this) }
        } finally {
            check(interceptors.removeAt(interceptors.lastIndex) === interceptor)
        }
        ctx.addLabel(e.exceptionEntry.toViper(ctx))
        setPending(1, ctx)
        ctx.addStatement { e.finalEntry.toLink().toViperGoto(this) }
        ctx.addLabel(e.finalEntry.toViper(ctx))
        e.finalizer.accept(visitor).toViperUnusedResult(ctx)
        fun dispatch(tag: Int, completion: ExpEmbedding) {
            If(EqCmp(pending, IntLit(tag)), completion, UnitLit, buildType { unit() })
                .accept(visitor).toViperUnusedResult(ctx)
        }
        dispatch(1, e.exceptionalExit)
        jumps.forEach { (target, tag) -> dispatch(tag, Goto(target)) }
    }
}
