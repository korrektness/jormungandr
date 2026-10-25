/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.core.asPosition
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.FieldAccess
import org.jetbrains.kotlin.formver.core.embeddings.expression.PrimitiveFieldAccess
import org.jetbrains.kotlin.formver.core.embeddings.expression.SafeCast
import org.jetbrains.kotlin.formver.core.embeddings.expression.UniqueValAccess
import org.jetbrains.kotlin.formver.core.embeddings.properties.PathStep
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Info
import org.jetbrains.kotlin.formver.viper.ast.PermExp

/** Source provenance for expression-only reads, including the successful branch of a safe cast. */
fun ExpEmbedding.pureReadPath(): OwnedPath? = when (val exp = ignoringCastsAndMetaNodes()) {
    is SafeCast -> exp.exp.pureReadPath()
    is FieldAccess -> if (exp.field.isUnique) exp.receiver.pureReadPath()?.plus(exp.field) else null
    is PrimitiveFieldAccess -> if (exp.field.isUnique) exp.inner.pureReadPath()?.plus(exp.field) else null
    is UniqueValAccess -> exp.receiver.pureReadPath()?.plus(exp.step)
    else -> exp.ownedPath()
}

/** The type whose predicate the source path holds, independent of a cast's target type. */
val OwnedPath.presentationType: TypeEmbedding
    get() = fields.lastOrNull()?.type ?: root.type

/**
 * The unique-predicate access on [classOnPath] that must be unfolded to reach a field on a superclass
 * through [receiver].
 */
fun hierarchyPredicateAccess(
    receiver: Exp,
    classOnPath: ClassTypeEmbedding,
    source: KtSourceElement?,
    info: Info = Info.NoInfo,
): Exp.PredicateAccess =
    Exp.PredicateAccess(
        classOnPath.uniquePredicateName,
        listOf(receiver),
        PermExp.FullPerm(),
        source.asPosition,
        info,
    )

/**
 * The unique-predicate accesses to unfold to reach [field] through [receiver], ordered top-down:
 * the class of [receiverType] first, the class declaring [field] last.
 */
fun LinearizationContext.hierarchyPredicateAccesses(
    receiver: Exp,
    receiverType: TypeEmbedding,
    field: PathStep,
): Sequence<Exp.PredicateAccess> =
    typeResolver.hierarchyPathTo(receiverType.pretype, field)
        .map { hierarchyPredicateAccess(receiver, it, source) }
