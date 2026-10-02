/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.names

import org.jetbrains.kotlin.formver.viper.SymbolicName

/** How the Kotlin source spells the entity this name stands for, `null` when the source does not name it. */
val SymbolicName.sourceSpelling: String?
    get() = when (this) {
        is ScopedName -> name.sourceSpelling
        is SsaVariableName -> baseName.sourceSpelling
        is SimpleKotlinName -> name.asStringStripSpecialMarkers()
        is TypedKotlinName -> name.asStringStripSpecialMarkers()
        is TypedKotlinNameWithType -> name.asStringStripSpecialMarkers()
        is ClassKotlinName -> name.shortName().asString()
        else -> null
    }
