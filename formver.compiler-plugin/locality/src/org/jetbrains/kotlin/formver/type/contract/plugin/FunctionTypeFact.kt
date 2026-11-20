/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.type.contract.plugin

/**
 * Recursive function type-fact representation based on [TypeFact].
 *
 * @param TypeFact the type of the function [ParameterTypeFact]s.
 * @param parameterTypeFacts the list of function [ParameterTypeFact]s referring to the input parameters.
 * @param resultFunctionTypeFact the function type-fact of the return value (if present).
 * @param holdsNoFunction whether the value never holds a function, as `null` or an expression of type `Nothing`: such a
 *  fact satisfies every requirement and is the identity of joins. Its parameter and result facts are empty.
 */
data class FunctionTypeFact<TypeFact>(
    val parameterTypeFacts: List<ParameterTypeFact<TypeFact>>,
    val resultFunctionTypeFact: FunctionTypeFact<TypeFact>?,
    val holdsNoFunction: Boolean = false,
) {
    companion object {
        fun <TypeFact> noFunction(): FunctionTypeFact<TypeFact> = FunctionTypeFact(emptyList(), null, holdsNoFunction = true)
    }

    data class ParameterTypeFact<TypeFact>(
        val typeFact: TypeFact,
        val functionTypeFact: FunctionTypeFact<TypeFact>?
    )
}
