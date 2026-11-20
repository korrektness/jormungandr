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

/** The `loopInvariants` calls that lead the bodies of loops, which the loops' conversions consume. */
interface LoopInvariantAttachments {
    /** Marks [call], a `loopInvariants` call, as the invariants of the loop it leads. */
    fun attachLoopInvariants(call: FirStatement)

    /** Whether [attachLoopInvariants] marked [call]. */
    fun isAttachedLoopInvariants(call: FirStatement): Boolean
}

class LoopInvariantAttachmentSet : LoopInvariantAttachments {
    private val attached = mutableSetOf<FirStatement>()

    override fun attachLoopInvariants(call: FirStatement) {
        attached += call
    }

    override fun isAttachedLoopInvariants(call: FirStatement): Boolean = call in attached
}

/** The `loopInvariants` block that leads [loopBody], marked as attached to its loop; `null` when there is none. */
fun ProgramConversionContext.extractLoopInvariants(loopBody: List<FirStatement>): FirBlock? =
    loopBody.firstOrNull()?.let(::extractLoopInvariantsCall)?.body

/** The `loopInvariants` lambda of [statement], marked as attached to a loop; `null` when [statement] is no such call. */
fun ProgramConversionContext.extractLoopInvariantsCall(statement: FirStatement): FirAnonymousFunction? =
    statement.extractFormverFirBlock { isFormverFunctionNamed("loopInvariants") }?.also { attachLoopInvariants(statement) }

/** [throwsOnlyIf] is the body of the `throwsOnlyIf` block that follows the precondition and postcondition blocks. */
data class FirSpecification(
    val precond: FirBlock?,
    val postcond: FirBlock?,
    val returnVar: FirValueParameterSymbol?,
    val throwsOnlyIf: FirBlock? = null,
) {
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
fun FirBlock.postconditionsResultType(): ConeKotlinType? =
    leadingSpecificationBlocks(statements).post?.valueParameters?.firstOrNull()?.symbol?.resolvedReturnType

fun extractFirSpecification(parentBlock: FirBlock, returnType: ConeKotlinType): FirSpecification =
    extractFirSpecification(parentBlock.statements, returnType)

/** The leading `preconditions`, `postconditions` and `throwsOnlyIf` blocks of a body, each optional, in this order. */
data class LeadingSpecificationBlocks(
    val pre: FirAnonymousFunction?,
    val post: FirAnonymousFunction?,
    val throwsOnlyIf: FirAnonymousFunction?,
) {
    val count: Int get() = listOfNotNull(pre, post, throwsOnlyIf).size
}

fun leadingSpecificationBlocks(statements: List<FirStatement>): LeadingSpecificationBlocks {
    val pre = statements.getOrNull(0)?.extractFormverFirBlock { isFormverFunctionNamed("preconditions") }
    var next = if (pre != null) 1 else 0
    val post = statements.getOrNull(next)?.extractFormverFirBlock { isFormverFunctionNamed("postconditions") }
    if (post != null) next++
    val throws = statements.getOrNull(next)?.extractFormverFirBlock { isFormverFunctionNamed("throwsOnlyIf") }
    return LeadingSpecificationBlocks(pre, post, throws)
}

fun extractFirSpecification(statements: List<FirStatement>, returnType: ConeKotlinType): FirSpecification {
    val (pre, post, throws) = leadingSpecificationBlocks(statements)
    return FirSpecification(pre?.body, post?.body, post?.extractFormverReturnVar(returnType), throws?.body)
}
