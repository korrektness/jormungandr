/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.properties

import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.callables.toFuncApp
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.names.sourceSpelling
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Position

/**
 * A property that a path to a value can go through: a backing field, or the getter of a `@Unique` `val`.
 */
interface PathStep {
    val name: SymbolicName
    val type: TypeEmbedding
    val containingClass: ClassTypeEmbedding?
    val symbol: FirPropertySymbol?

    /** The property as the Kotlin source spells it. */
    val spelling: String
        get() = name.sourceSpelling ?: symbol?.name?.asString() ?: "<field>"

    /** The value of the property on [receiver], read without unfolding anything. */
    fun valueOf(receiver: ExpEmbedding): ExpEmbedding

    /** The value of the property on [receiver] in Viper, read without unfolding anything. */
    fun valueOf(receiver: Exp, pos: Position): Exp
}

/**
 * A `@Unique` `val` with default behaviour, read through the heap-independent function [getter]. Reading the value
 * needs no permission; its unique predicate is nested in the predicate of [containingClass].
 */
data class UniqueValStep(
    override val symbol: FirPropertySymbol,
    override val type: TypeEmbedding,
    override val containingClass: ClassTypeEmbedding,
    val getter: NonInlineFunctionSignature,
) : PathStep {
    override val name: SymbolicName
        get() = getter.name

    override val spelling: String
        get() = symbol.name.asString()

    override fun valueOf(receiver: ExpEmbedding): ExpEmbedding = getter.insertCall(listOf(receiver))

    override fun valueOf(receiver: Exp, pos: Position): Exp = getter.toFuncApp(listOf(receiver), pos)
}
