/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.types

import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain.Companion.isOf
import org.jetbrains.kotlin.formver.core.domains.domainVar
import org.jetbrains.kotlin.formver.core.embeddings.expression.IntegerKind
import org.jetbrains.kotlin.formver.core.names.DispatchReceiverName
import org.jetbrains.kotlin.formver.core.names.DomainAssociatedFuncName
import org.jetbrains.kotlin.formver.core.names.embedName
import org.jetbrains.kotlin.formver.intrinsics.plugin.byteArrayClassId
import org.jetbrains.kotlin.formver.intrinsics.plugin.charArrayClassId
import org.jetbrains.kotlin.formver.intrinsics.plugin.intArrayClassId
import org.jetbrains.kotlin.formver.intrinsics.plugin.longArrayClassId
import org.jetbrains.kotlin.formver.viper.ast.*
import org.jetbrains.kotlin.formver.viper.ast.Exp.Companion.toConjunction
import org.jetbrains.kotlin.name.ClassId

/**
 * The built-in embedding of a primitive array class whose elements are integers of kind [element].
 *
 * The elements live in [contentsField] as integers, which the unique predicate owns and bounds by [element]'s range.
 * The length is the heap-independent function `arraySize`, shared by all primitive array classes, which the predicate
 * ties to the length of `contents`, so the length can be read without permission.
 */
class PrimitiveArrayEmbedding private constructor(val classId: ClassId, val element: IntegerKind) {
    val classType = ClassTypeEmbedding(classId.embedName())

    fun uniquePredicateAccess(array: Exp, pos: Position = Position.NoPosition): Exp.PredicateAccess =
        Exp.PredicateAccess(classType.uniquePredicateName, listOf(array), PermExp.FullPerm(), pos)

    fun uniquePredicate(): Predicate {
        val array = receiver.use()
        return Predicate(
            classType.uniquePredicateName,
            listOf(receiver.decl()),
            listOf(
                array isOf classType.runtimeType,
                array.fieldAccessPredicate(contentsField, PermExp.FullPerm()),
                Exp.SeqLength(array.fieldAccess(contentsField)) eq arraySize(array),
                Exp.forall(domainVar("index", Type.Int)) { index ->
                    assumption { index ge 0.toExp() }
                    assumption { index lt arraySize(array) }
                    element.range(simpleTrigger { element(array, index) })
                },
            ).toConjunction(),
        )
    }

    /**
     * `unfolding acc(C_unique(array)) in forall j :: 0 <= j < arraySize(array) ==> array.contents[j] == 0`, for this
     * array class `C`. Zero is also the code of `Char`'s default element.
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

    companion object {
        val INT = PrimitiveArrayEmbedding(intArrayClassId, IntegerKind.Int)
        val BYTE = PrimitiveArrayEmbedding(byteArrayClassId, IntegerKind.Byte)
        val CHAR = PrimitiveArrayEmbedding(charArrayClassId, IntegerKind.Char)
        val LONG = PrimitiveArrayEmbedding(longArrayClassId, IntegerKind.Long)
        val all = listOf(INT, BYTE, CHAR, LONG)

        fun of(classId: ClassId?): PrimitiveArrayEmbedding? = all.firstOrNull { it.classId == classId }
        fun of(type: PretypeEmbedding): PrimitiveArrayEmbedding? = all.firstOrNull { it.classType == type }

        private val receiver = Var(DispatchReceiverName, Type.Ref)

        val arraySizeFunction = UserFunction(
            DomainAssociatedFuncName("arraySize"),
            listOf(receiver.decl()),
            Type.Int,
            pres = listOf(),
            posts = listOf(Exp.Result(Type.Int) ge Exp.IntLit(0), Exp.Result(Type.Int) le Exp.IntLit(Int.MAX_VALUE)),
            body = null,
        )

        fun arraySize(array: Exp, pos: Position = Position.NoPosition, info: Info = Info.NoInfo): Exp =
            arraySizeFunction.toFuncApp(listOf(array), pos, info)

        /** `array.contents[index]`, which needs the unique predicate of [array] unfolded. */
        fun element(array: Exp, index: Exp, pos: Position = Position.NoPosition): Exp =
            Exp.SeqIndex(array.fieldAccess(contentsField, pos), index, pos)
    }
}
