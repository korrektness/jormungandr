/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.formver.core.embeddings.types.FunctionTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildFunctionPretype
import org.jetbrains.kotlin.formver.core.embeddings.types.nullableAny

internal fun propertyGetterType(receiverType: TypeEmbedding, returnType: TypeEmbedding): FunctionTypeEmbedding =
    buildFunctionPretype {
        withDispatchReceiver(receiverType)
        withReturnType(returnType)
    }

/** The type of a top-level property's getter. */
internal fun topLevelGetterType(): FunctionTypeEmbedding = buildFunctionPretype {
    withReturnType { nullableAny() }
}

/** The type of a top-level property's setter. */
internal fun topLevelSetterType(): FunctionTypeEmbedding = buildFunctionPretype {
    withParam { nullableAny() }
    withReturnType { nullableAny() }
}

/**
 * The type of an extension property's getter, which SnaKt treats as opaque. A member extension property's getter also
 * takes the dispatch receiver, when [member] holds.
 */
internal fun extensionGetterType(member: Boolean): FunctionTypeEmbedding = buildFunctionPretype {
    if (member) withDispatchReceiver { any() }
    withExtensionReceiver { nullableAny() }
    withReturnType { nullableAny() }
}

/**
 * The type of an extension property's setter, which SnaKt treats as opaque. A member extension property's setter also
 * takes the dispatch receiver, when [member] holds.
 */
internal fun extensionSetterType(member: Boolean): FunctionTypeEmbedding = buildFunctionPretype {
    if (member) withDispatchReceiver { any() }
    withExtensionReceiver { nullableAny() }
    withParam { nullableAny() }
    withReturnType { nullableAny() }
}

/** The type of an opaque member property getter, for a property that may be overridden or has a custom getter. */
internal fun memberGetterType(): FunctionTypeEmbedding = buildFunctionPretype {
    withDispatchReceiver { any() }
    withReturnType { nullableAny() }
}

/** The type of an opaque member property setter, for a property that may be overridden or has a custom setter. */
internal fun memberSetterType(): FunctionTypeEmbedding = buildFunctionPretype {
    withDispatchReceiver { any() }
    withParam { nullableAny() }
    withReturnType { nullableAny() }
}
