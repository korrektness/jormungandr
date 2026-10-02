/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.types

import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain.Companion.isOf
import org.jetbrains.kotlin.formver.core.names.DispatchReceiverName
import org.jetbrains.kotlin.formver.core.names.SpecialFieldName
import org.jetbrains.kotlin.formver.core.names.embedName
import org.jetbrains.kotlin.formver.viper.ast.*
import org.jetbrains.kotlin.formver.viper.ast.Exp.Companion.toConjunction
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/**
 * The `Seq[Int]` field in which a built-in owned class keeps its elements: an `IntArray`'s elements, a
 * `StringBuilder`'s character codes.
 */
val contentsField = Field(SpecialFieldName("contents"), Type.Seq(Type.Int), includeInShortDump = true)

/**
 * The built-in embedding of `StringBuilder`.
 *
 * The characters live in [contentsField] as character codes, the representation of a `String`. The unique predicate
 * owns the field.
 */
object StringBuilderEmbedding {
    val classType = ClassTypeEmbedding(ClassId.topLevel(FqName("java.lang.StringBuilder")).embedName())

    private val receiver = Var(DispatchReceiverName, Type.Ref)

    fun uniquePredicateAccess(builder: Exp, pos: Position = Position.NoPosition): Exp.PredicateAccess =
        Exp.PredicateAccess(classType.uniquePredicateName, listOf(builder), PermExp.FullPerm(), pos)

    /** `builder.contents`, which needs the unique predicate of [builder] unfolded. */
    fun contents(builder: Exp, pos: Position = Position.NoPosition): Exp.FieldAccess = builder.fieldAccess(contentsField, pos)

    fun uniquePredicate(): Predicate {
        val builder = receiver.use()
        return Predicate(
            classType.uniquePredicateName,
            listOf(receiver.decl()),
            listOf(
                builder isOf classType.runtimeType,
                builder.fieldAccessPredicate(contentsField, PermExp.FullPerm()),
            ).toConjunction(),
        )
    }
}
