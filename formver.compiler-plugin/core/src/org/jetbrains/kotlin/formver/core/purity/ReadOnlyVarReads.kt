/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.purity

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.common.ConversionOwnershipCondition
import org.jetbrains.kotlin.formver.common.ConversionOwnershipPath
import org.jetbrains.kotlin.formver.core.embeddings.expression.FirVariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.PrimitiveFieldAccess
import org.jetbrains.kotlin.formver.core.names.FunctionResultVariableName
import org.jetbrains.kotlin.formver.core.names.ReturnVariableName
import org.jetbrains.kotlin.formver.core.names.sourceSpelling
import org.jetbrains.kotlin.formver.core.conversion.AccessPolicy
import org.jetbrains.kotlin.formver.core.diagnostics.ErrorCollectionContext
import org.jetbrains.kotlin.formver.core.embeddings.expression.Assert
import org.jetbrains.kotlin.formver.core.embeddings.expression.Refute
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.FieldAccess
import org.jetbrains.kotlin.formver.core.embeddings.expression.FunctionCall
import org.jetbrains.kotlin.formver.core.embeddings.expression.IntArrayContents
import org.jetbrains.kotlin.formver.core.embeddings.expression.IntArrayGet
import org.jetbrains.kotlin.formver.core.embeddings.expression.Old
import org.jetbrains.kotlin.formver.core.embeddings.expression.StringBuilderRead
import org.jetbrains.kotlin.formver.core.embeddings.expression.UniqueValAccess
import org.jetbrains.kotlin.formver.core.embeddings.expression.VariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.While
import org.jetbrains.kotlin.formver.core.embeddings.expression.WithPosition
import org.jetbrains.kotlin.formver.core.names.DispatchReceiverName
import org.jetbrains.kotlin.formver.viper.SymbolicName

/**
 * Reports the `var` reads, array element and contents reads and `StringBuilder` reads in [this] read-only context (a pure function body or a
 * specification) that no unique predicate covers: a read through a receiver the uniqueness checker does not find
 * `Unique`, and, outside `old`, a read through a path rooted at one of [consumed] or a pure call passing such a path
 * to a `@Unique` parameter.
 */
fun ExpEmbedding.checkReadOnlyVarReads(
    source: KtSourceElement,
    errors: ErrorCollectionContext,
    consumed: Set<SymbolicName> = emptySet(),
) {
    val nextSource = (this as? WithPosition)?.source ?: source
    fun checkOwnedRead(
        receiver: ExpEmbedding, receiverOwned: Boolean, kind: ConversionOwnershipCondition.ReadKind,
        property: org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol? = null,
    ) {
        val reason = when {
            !receiverOwned -> ConversionOwnershipCondition.ReadReason.NEEDS_UNIQUE_RECEIVER
            receiver.pathRoot()?.name in consumed -> ConversionOwnershipCondition.ReadReason.CONSUMED_ROOT
            else -> return
        }
        errors.reportUnsupportedOwnership(nextSource,
            ConversionOwnershipCondition.Read(kind, reason, receiver.conversionOwnershipPath(), property))
    }
    when {
        this is FieldAccess && field.accessPolicy == AccessPolicy.BY_RECEIVER_UNIQUENESS ->
            checkOwnedRead(receiver, receiverOwned, ConversionOwnershipCondition.ReadKind.VAR_PROPERTY, field.symbol)
        this is IntArrayGet -> checkOwnedRead(array, receiverOwned, ConversionOwnershipCondition.ReadKind.ARRAY_ELEMENT)
        this is IntArrayContents -> checkOwnedRead(array, receiverOwned, ConversionOwnershipCondition.ReadKind.ARRAY_CONTENTS)
        this is StringBuilderRead -> checkOwnedRead(builder, receiverOwned, ConversionOwnershipCondition.ReadKind.STRING_BUILDER)
        this is FunctionCall -> function.formalArgs.zip(args)
            .filter { (param, arg) -> param.isUnique && arg.pathRoot()?.name in consumed }
            .forEach { (_, arg) ->
                errors.reportUnsupportedOwnership(
                    (arg as? WithPosition)?.source ?: nextSource,
                    ConversionOwnershipCondition.ConsumedArgument(checkNotNull(arg.conversionOwnershipPath()), function.symbol),
                )
            }
    }
    val childConsumed = if (this is Old) emptySet() else consumed
    children().forEach { it.checkReadOnlyVarReads(nextSource, errors, childConsumed) }
}

/**
 * Applies [checkReadOnlyVarReads] to the loop invariants and verification probe conditions of [this] impure body.
 */
fun ExpEmbedding.checkSpecificationVarReads(source: KtSourceElement, errors: ErrorCollectionContext) {
    preorder(source).forEach { (embedding, embeddingSource) ->
        val specSource = embeddingSource ?: source
        when (embedding) {
            is While -> embedding.invariants.forEach { it.checkReadOnlyVarReads(specSource, errors) }
            is Assert -> embedding.exp.checkReadOnlyVarReads(specSource, errors)
            is Refute -> embedding.exp.checkReadOnlyVarReads(specSource, errors)
            else -> {}
        }
    }
}

internal fun ExpEmbedding.conversionOwnershipPath(): ConversionOwnershipPath? = when (val exp = ignoringCastsAndMetaNodes()) {
    is VariableEmbedding -> ConversionOwnershipPath(ConversionOwnershipPath.Root(
        kind = when (exp.name) {
            DispatchReceiverName -> ConversionOwnershipPath.Kind.DISPATCH_RECEIVER
            is ReturnVariableName, FunctionResultVariableName -> ConversionOwnershipPath.Kind.RESULT
            else -> if (exp.name.sourceSpelling == null) ConversionOwnershipPath.Kind.SYNTHETIC else ConversionOwnershipPath.Kind.LOCAL
        },
        symbol = (exp as? FirVariableEmbedding)?.symbol,
        name = exp.name.sourceSpelling,
    ))
    is FieldAccess -> exp.receiver.conversionOwnershipPath()?.let {
        it.copy(members = it.members + ConversionOwnershipPath.Member(exp.field.symbol, exp.field.name.sourceSpelling))
    }
    is PrimitiveFieldAccess -> exp.inner.conversionOwnershipPath()?.let {
        it.copy(members = it.members + ConversionOwnershipPath.Member(exp.field.symbol, exp.field.name.sourceSpelling))
    }
    is UniqueValAccess -> exp.receiver.conversionOwnershipPath()?.let {
        it.copy(members = it.members + ConversionOwnershipPath.Member(exp.step.symbol, exp.step.name.sourceSpelling))
    }
    else -> null
}

private fun ExpEmbedding.pathRoot(): VariableEmbedding? = when (val exp = ignoringCastsAndMetaNodes()) {
    is VariableEmbedding -> exp
    is FieldAccess -> exp.receiver.pathRoot()
    is UniqueValAccess -> exp.receiver.pathRoot()
    else -> null
}
