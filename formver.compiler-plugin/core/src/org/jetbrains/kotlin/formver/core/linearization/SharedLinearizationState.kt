/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.formver.core.conversion.FreshEntityProducer
import org.jetbrains.kotlin.formver.core.embeddings.LabelLink
import org.jetbrains.kotlin.formver.core.embeddings.expression.AnonymousVariableEmbedding
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding

/** A jump leaving [localLabels] is redirected through a pending completion handler. */
data class JumpInterceptor(
    val localLabels: Set<SymbolicName>,
    val redirect: (LabelLink, LinearizationContext) -> LabelLink,
)

class SharedLinearizationState(private val producer: FreshEntityProducer<AnonymousVariableEmbedding, TypeEmbedding>) {
    val jumpInterceptors = mutableListOf<JumpInterceptor>()
    fun freshAnonVar(type: TypeEmbedding) = producer.getFresh(type)
}
