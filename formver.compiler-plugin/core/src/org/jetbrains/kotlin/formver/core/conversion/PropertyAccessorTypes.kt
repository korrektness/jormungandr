/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.formver.core.embeddings.types.FunctionTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildFunctionPretype

internal fun propertyGetterType(receiverType: TypeEmbedding, returnType: TypeEmbedding): FunctionTypeEmbedding =
    buildFunctionPretype {
        withDispatchReceiver(receiverType)
        withReturnType(returnType)
    }
