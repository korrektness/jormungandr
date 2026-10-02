/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.properties

import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding

data class PropertyEmbedding(
    val getter: GetterEmbedding?,
    val setter: SetterEmbedding?,
    val hasDefaultBehaviour: Boolean,
    val isUnique: Boolean,
    val isVal: Boolean,
    val type: TypeEmbedding,
) {
    /** The step an owned path takes through this property, `null` when the property's value is not tracked. */
    val ownedStep: PathStep?
        get() = when (getter) {
            is BackingFieldGetter -> getter.field.takeIf { it.isUnique }
            is UniqueValGetter -> getter.step
            else -> null
        }
}
