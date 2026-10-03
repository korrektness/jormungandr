/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeAttributes
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.withAttributes
import org.jetbrains.kotlin.formver.core.isFormverFunctionNamed

fun FirStatement.extractFormverFirBlock(predicate: FirFunctionSymbol<*>.() -> Boolean): FirAnonymousFunction? {
    if (this !is FirFunctionCall) return null
    val firFunction = toResolvedCallableSymbol() as? FirFunctionSymbol<*> ?: return null
    if (!predicate(firFunction)) return null
    val formverInvariantsArgument = argument
    if (formverInvariantsArgument !is FirAnonymousFunctionExpression)
        error("Only lambdas are allowed as arguments of ${firFunction.name}.")
    return formverInvariantsArgument.anonymousFunction
}

fun extractLoopInvariants(loopBody: List<FirStatement>): FirBlock? {
    val firstStmt = loopBody.firstOrNull() ?: return null
    return firstStmt.extractFormverFirBlock { isFormverFunctionNamed("loopInvariants") }?.body
}

data class FirSpecification(val precond: FirBlock?, val postcond: FirBlock?, val returnVar: FirValueParameterSymbol?) {
    constructor() : this(null, null, null)
}

private fun FirAnonymousFunction.extractFormverReturnVar(returnType: ConeKotlinType): FirValueParameterSymbol {
    val param = valueParameters.first()
    // Type annotations such as `@Unique` live in attributes; they do not make the types different.
    if (!param.symbol.resolvedReturnType.sameUpToAttributes(returnType))
        error("Expected type ${returnType} based on signature, got ${param.symbol.resolvedReturnType}")
    return param.symbol
}

private fun ConeKotlinType.withoutAttributes(): ConeKotlinType = withAttributes(ConeAttributes.Empty)

/** Whether the two types are the same, ignoring type annotations such as `@Unique`. */
fun ConeKotlinType.sameUpToAttributes(other: ConeKotlinType): Boolean = withoutAttributes() == other.withoutAttributes()

/**
 * The type of the result parameter of the `postconditions` block among this block's leading specification blocks,
 * or `null` when there is no such block.
 */
fun FirBlock.postconditionsResultType(): ConeKotlinType? {
    val pre = statements.firstOrNull()?.extractFormverFirBlock { isFormverFunctionNamed("preconditions") }
    val post = statements.getOrNull(if (pre != null) 1 else 0)
        ?.extractFormverFirBlock { isFormverFunctionNamed("postconditions") }
    return post?.valueParameters?.firstOrNull()?.symbol?.resolvedReturnType
}

fun extractFirSpecification(parentBlock: FirBlock, returnType: ConeKotlinType): FirSpecification {
    val firstStmt = parentBlock.statements.firstOrNull() ?: return FirSpecification()

    firstStmt.extractFormverFirBlock { isFormverFunctionNamed("postconditions") }?.let { lambda ->
        return FirSpecification(null, lambda.body, lambda.extractFormverReturnVar(returnType))
    }

    val precond = firstStmt.extractFormverFirBlock { isFormverFunctionNamed("preconditions") }
        ?: return FirSpecification()
    val postcond =
        parentBlock.statements.getOrNull(1)?.extractFormverFirBlock { isFormverFunctionNamed("postconditions") }
    return FirSpecification(precond.body, postcond?.body, postcond?.extractFormverReturnVar(returnType))
}
