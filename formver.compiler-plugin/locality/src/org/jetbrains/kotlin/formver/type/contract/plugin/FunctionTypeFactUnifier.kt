/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.type.contract.plugin

import org.jetbrains.kotlin.formver.type.plugin.TypeFactIntersector
import org.jetbrains.kotlin.formver.type.plugin.TypeFactUnifier

/**
 * Implementation of a [TypeFactUnifier] for a nullable [FunctionTypeFact].
 *
 * @param TypeFact the type-fact class of the elements of the contract.
 * @param typeIntersector intersects parameter facts when joining contracts.
 * @param typeUnifier joins parameter facts when intersecting nested input contracts.
 *
 * Joining contracts intersects their parameter facts and nested parameter contracts, and joins result contracts.
 * Intersecting contracts reverses these operations recursively. A null contract carries no function structure.
 * Mismatched arities produce an empty parameter list and combine only the result contracts.
 */
class FunctionTypeFactUnifier<TypeFact>(
    private val typeIntersector: TypeFactIntersector<TypeFact>,
    private val typeUnifier: TypeFactUnifier<TypeFact>,
) : TypeFactUnifier<FunctionTypeFact<TypeFact>?> {
    override fun join(
        left: FunctionTypeFact<TypeFact>?,
        right: FunctionTypeFact<TypeFact>?
    ): FunctionTypeFact<TypeFact>? = combine(left, right, intersect = false)

    private fun combine(
        left: FunctionTypeFact<TypeFact>?,
        right: FunctionTypeFact<TypeFact>?,
        intersect: Boolean,
    ): FunctionTypeFact<TypeFact>? =
        when {
            left == null -> right
            right == null -> left
            else -> FunctionTypeFact(
                if (left.parameterTypeFacts.size != right.parameterTypeFacts.size) {
                    emptyList()
                } else {
                    left.parameterTypeFacts.zip(right.parameterTypeFacts).map { (leftElement, rightElement) ->
                        FunctionTypeFact.ParameterTypeFact(
                            if (intersect) typeUnifier.join(leftElement.typeFact, rightElement.typeFact)
                            else typeIntersector.meet(leftElement.typeFact, rightElement.typeFact),
                            combine(leftElement.functionTypeFact, rightElement.functionTypeFact, !intersect)
                        )
                    }
                },
                combine(left.resultFunctionTypeFact, right.resultFunctionTypeFact, intersect)
            )
        }
}
