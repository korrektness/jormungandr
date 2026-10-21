/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.types

import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain.Companion.isOf
import org.jetbrains.kotlin.formver.core.domains.domainVar
import org.jetbrains.kotlin.formver.core.names.DispatchReceiverName
import org.jetbrains.kotlin.formver.core.names.DomainAssociatedFuncName
import org.jetbrains.kotlin.formver.core.names.SpecialFieldName
import org.jetbrains.kotlin.formver.core.names.embedName
import org.jetbrains.kotlin.formver.viper.ast.*
import org.jetbrains.kotlin.formver.viper.ast.Exp.Companion.toConjunction
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/** Reference storage carries no element-type invariant; each read inhales its static Kotlin type. */
object GenericArrayEmbedding {
    val classId = ClassId.topLevel(FqName("kotlin.Array"))
    val classType = ClassTypeEmbedding(classId.embedName())
    val contentsField = Field(SpecialFieldName("referenceContents"), Type.Seq(Type.Ref), includeInShortDump = true)
    private val receiver = Var(DispatchReceiverName, Type.Ref)
    val arraySizeFunction = UserFunction(
        DomainAssociatedFuncName("referenceArraySize"), listOf(receiver.decl()), Type.Int,
        pres = listOf(),
        posts = listOf(Exp.Result(Type.Int) ge Exp.IntLit(0), Exp.Result(Type.Int) le Exp.IntLit(Int.MAX_VALUE)),
        body = null,
    )

    private val repeatedValue = Var(DomainAssociatedFuncName("repeatedValue"), Type.Ref)
    private val repeatedCount = Var(DomainAssociatedFuncName("repeatedCount"), Type.Int)
    val repeatFunction = UserFunction(
        DomainAssociatedFuncName("repeatReferences"), listOf(repeatedValue.decl(), repeatedCount.decl()), Type.Seq(Type.Ref),
        pres = listOf(repeatedCount.use() ge Exp.IntLit(0)),
        posts = listOf(
            Exp.SeqLength(Exp.Result(Type.Seq(Type.Ref))) eq repeatedCount.use(),
            Exp.forall(domainVar("j", Type.Int)) { j ->
                assumption { j ge Exp.IntLit(0) }
                assumption { j lt repeatedCount.use() }
                simpleTrigger { Exp.SeqIndex(Exp.Result(Type.Seq(Type.Ref)), j) } eq repeatedValue.use()
            },
        ), body = null,
    )

    fun arraySize(array: Exp, pos: Position = Position.NoPosition): Exp = arraySizeFunction.toFuncApp(listOf(array), pos)
    fun uniquePredicateAccess(array: Exp, pos: Position = Position.NoPosition): Exp.PredicateAccess =
        Exp.PredicateAccess(classType.uniquePredicateName, listOf(array), PermExp.FullPerm(), pos)
    fun element(array: Exp, index: Exp, pos: Position = Position.NoPosition): Exp =
        Exp.SeqIndex(array.fieldAccess(contentsField, pos), index, pos)

    fun uniquePredicate(): Predicate {
        val array = receiver.use()
        return Predicate(classType.uniquePredicateName, listOf(receiver.decl()), listOf(
            array isOf classType.runtimeType,
            array.fieldAccessPredicate(contentsField, PermExp.FullPerm()),
            Exp.SeqLength(array.fieldAccess(contentsField)) eq arraySize(array),
        ).toConjunction())
    }

    fun allNull(array: Exp, pos: Position = Position.NoPosition): Exp = Exp.Unfolding(
        uniquePredicateAccess(array),
        Exp.forall(domainVar("j", Type.Int)) { j ->
            assumption { j ge Exp.IntLit(0) }
            assumption { j lt arraySize(array) }
            simpleTrigger { element(array, j) } eq RuntimeTypeDomain.nullValue()
        }, pos,
    )
}
