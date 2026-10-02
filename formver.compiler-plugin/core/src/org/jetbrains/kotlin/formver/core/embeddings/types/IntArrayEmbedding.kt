/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.types

import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain.Companion.isOf
import org.jetbrains.kotlin.formver.core.domains.domainVar
import org.jetbrains.kotlin.formver.core.names.DispatchReceiverName
import org.jetbrains.kotlin.formver.core.names.DomainAssociatedFuncName
import org.jetbrains.kotlin.formver.core.names.embedName
import org.jetbrains.kotlin.formver.viper.ast.*
import org.jetbrains.kotlin.formver.viper.ast.Exp.Companion.toConjunction
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/**
 * The built-in embedding of `kotlin.IntArray`.
 *
 * The elements live in [contentsField], which the unique predicate owns. The length is the heap-independent function
 * `arraySize`, which the predicate ties to the length of `contents`, so the length can be read without permission.
 */
object IntArrayEmbedding {
    val classId = ClassId.topLevel(FqName("kotlin.IntArray"))

    val classType = ClassTypeEmbedding(classId.embedName())

    private val receiver = Var(DispatchReceiverName, Type.Ref)

    val arraySizeFunction = UserFunction(
        DomainAssociatedFuncName("arraySize"),
        listOf(receiver.decl()),
        Type.Int,
        pres = listOf(),
        posts = listOf(Exp.Result(Type.Int) ge Exp.IntLit(0)),
        body = null,
    )

    fun arraySize(array: Exp, pos: Position = Position.NoPosition, info: Info = Info.NoInfo): Exp =
        arraySizeFunction.toFuncApp(listOf(array), pos, info)

    fun uniquePredicateAccess(array: Exp, pos: Position = Position.NoPosition): Exp.PredicateAccess =
        Exp.PredicateAccess(classType.uniquePredicateName, listOf(array), PermExp.FullPerm(), pos)

    /** `array.contents[index]`, which needs the unique predicate of [array] unfolded. */
    fun element(array: Exp, index: Exp, pos: Position = Position.NoPosition): Exp =
        Exp.SeqIndex(array.fieldAccess(contentsField, pos), index, pos)

    fun uniquePredicate(): Predicate {
        val array = receiver.use()
        return Predicate(
            classType.uniquePredicateName,
            listOf(receiver.decl()),
            listOf(
                array isOf classType.runtimeType,
                array.fieldAccessPredicate(contentsField, PermExp.FullPerm()),
                Exp.SeqLength(array.fieldAccess(contentsField)) eq arraySize(array),
            ).toConjunction(),
        )
    }

    /**
     * `unfolding acc(IntArray_unique(array)) in forall j :: 0 <= j < arraySize(array) ==> array.contents[j] == 0`
     */
    fun allZero(array: Exp, pos: Position = Position.NoPosition, info: Info = Info.NoInfo): Exp {
        val index = domainVar("j", Type.Int)
        val body = Exp.forall(index) { j ->
            assumption { j ge Exp.IntLit(0) }
            assumption { j lt arraySize(array) }
            simpleTrigger { Exp.SeqIndex(array.fieldAccess(contentsField), j) } eq Exp.IntLit(0)
        }
        return Exp.Unfolding(
            uniquePredicateAccess(array),
            body,
            pos,
            info,
        )
    }
}
