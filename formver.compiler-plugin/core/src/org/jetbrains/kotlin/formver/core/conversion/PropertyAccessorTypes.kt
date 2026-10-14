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

/** The type of an extension property's getter, which SnaKt treats as opaque. */
internal fun extensionGetterType(): FunctionTypeEmbedding = buildFunctionPretype {
    withExtensionReceiver { nullableAny() }
    withReturnType { nullableAny() }
}

/** The type of an extension property's setter, which SnaKt treats as opaque. */
internal fun extensionSetterType(): FunctionTypeEmbedding = buildFunctionPretype {
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
