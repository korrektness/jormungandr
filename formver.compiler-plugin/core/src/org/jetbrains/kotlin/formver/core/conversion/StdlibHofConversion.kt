/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.isString
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.AddIntInt
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.And
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.GeIntInt
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.LeIntInt
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.LtIntInt
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.Or
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.StringGet
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.StringLength
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val textPackage = FqName("kotlin.text")

/** A stdlib function that calls its last argument, a lambda, for each index below a bound. */
private enum class StdlibHof(val callableId: CallableId, val overString: Boolean, val arity: Int) {
    ForEach(CallableId(textPackage, Name.identifier("forEach")), overString = true, arity = 1),
    ForEachIndexed(CallableId(textPackage, Name.identifier("forEachIndexed")), overString = true, arity = 1),
    Count(CallableId(textPackage, Name.identifier("count")), overString = true, arity = 1),
    Repeat(CallableId(FqName("kotlin"), Name.identifier("repeat")), overString = false, arity = 2),
}

private fun FirFunctionCall.stdlibHof(): StdlibHof? {
    val callableId = toResolvedCallableSymbol()?.callableId ?: return null
    // The arity tells `CharSequence.count()` without a predicate apart.
    return StdlibHof.entries.firstOrNull { it.callableId == callableId && it.arity == arguments.size }
}

/**
 * Converts a call to a stdlib inline higher-order function whose body is not available to a loop over the index that
 * calls the lambda argument inlined; `null` when [call] is not such a call.
 *
 * The loop head knows the index's bounds. When the lambda's body starts with `loopInvariants { }`, its invariants hold
 * at the loop head too; there the lambda's index parameter is the loop's index, and its element parameter is not in
 * scope.
 */
fun StmtConversionContext.convertStdlibHof(call: FirFunctionCall): ExpEmbedding? {
    val hof = call.stdlibHof() ?: return null
    val intType = buildType { int() }
    val prelude = mutableListOf<ExpEmbedding>()
    val bound: ExpEmbedding
    val receiver: VariableEmbedding?
    if (hof.overString) {
        val receiverExp = call.extensionReceiver
            ?: throw SnaktInternalException(call.source, "A call to `${hof.callableId.callableName}` has no receiver.")
        if (!receiverExp.resolvedType.isString) {
            throw UnsupportedFeatureException(
                call.source,
                "`${hof.callableId.callableName}` on a receiver that is not a `String`",
            )
        }
        val declaration = declareAnonVar(buildType { string() }, convert(receiverExp))
        prelude.add(declaration)
        receiver = declaration.variable
        bound = StringLength(receiver)
    } else {
        val declaration = declareAnonVar(intType, convert(call.arguments[0]))
        prelude.add(declaration)
        receiver = null
        bound = declaration.variable
    }
    val lambdaArg = call.arguments.last()
    val lambda = convert(lambdaArg).ignoringMetaNodes() as? LambdaExp
        ?: throw UnsupportedFeatureException(
            lambdaArg.source,
            "a function argument to `${hof.callableId.callableName}` that is not a lambda",
        )

    val index = freshAnonVar(intType)
    prelude.add(Declare(index, IntLit(0)))
    val count = if (hof == StdlibHof.Count) freshAnonVar(intType) else null
    count?.let { prelude.add(Declare(it, IntLit(0))) }

    val parameters = lambda.function.valueParameters.map { it.symbol }
    val (indexParameter, elementParameter) = when (hof) {
        StdlibHof.ForEach, StdlibHof.Count -> null to parameters.single()
        StdlibHof.ForEachIndexed -> parameters[0] to parameters[1]
        StdlibHof.Repeat -> parameters.single() to null
    }

    val inScope = ownershipFrame.scopeWith(retrievePropertiesAndParameters().toList())
    val typeInvariants = (inScope + listOfNotNull(receiver, index, count)).flatMap { it.provenInvariants() }
    val invariants = buildList {
        add(GeIntInt(index, IntLit(0)))
        // `repeat` with a negative count runs no iteration.
        add(if (receiver != null) LeIntInt(index, bound) else Or(LeIntInt(index, bound), EqCmp(index, IntLit(0))))
        count?.let { add(And(GeIntInt(it, IntLit(0)), LeIntInt(it, index))) }
        lambda.function.body?.statements?.let(::extractLoopInvariants)?.let { block ->
            addAll(convertHoistedInvariants(block, indexParameter, index, elementParameter))
        }
    }
    // The lambda is analysed in place: the state before it joins the entry and the back edge.
    val headShapes = ownedShapes({ it.stateBefore(lambda.function) }, inScope)
    val exitShapes = ownedShapes({ it.stateAfter(call) }, inScope)

    val loop = withFreshWhile(label = null) {
        val element = receiver?.let { StringGet(it, index) }
        val args = listOfNotNull(index.takeIf { indexParameter != null }, element)
        val invocation = withCallSite(call) { lambda.insertCall(args, this) }
        val body = when (count) {
            null -> invocation
            else -> If(invocation, Assign(count, AddIntInt(count, IntLit(1))), UnitLit, buildType { unit() })
        }
        val step = Assign(index, AddIntInt(index, IntLit(1)))
        loopOverUsedRoots(
            LtIntInt(index, bound),
            blockOf(body, step),
            continueLabelName(),
            typeInvariants,
            invariants,
            headShapes,
            exitShapes,
        )
    }
    return (prelude + loop + (count ?: UnitLit)).toBlock()
}

/**
 * The invariants of [block], written at the start of a lambda, converted for the head of the loop that calls the
 * lambda: [indexParameter] stands for [index], and [elementParameter] may not be read.
 */
private fun StmtConversionContext.convertHoistedInvariants(
    block: FirBlock,
    indexParameter: FirValueParameterSymbol?,
    index: VariableEmbedding,
    elementParameter: FirValueParameterSymbol?,
): List<ExpEmbedding> {
    val resolver = LoopHeadParameterResolver(
        InlineParameterResolver(
            substitutions = listOfNotNull(indexParameter).associate { SubstitutedArgument.ValueParameter(it) to index },
            labelName = null,
            defaultResolvedReturnTarget = defaultResolvedReturnTarget,
        ),
        elementParameter,
    )
    val methodCtxFactory = MethodContextFactory(signature, resolver, ownershipFrame = ownershipFrame, parent = this)
    return withNoScope { withMethodCtx(methodCtxFactory) { collectInvariants(block) } }
}

private class LoopHeadParameterResolver(
    private val inner: ParameterResolver,
    private val elementParameter: FirValueParameterSymbol?,
) : ParameterResolver by inner {
    override fun tryResolveParameter(symbol: FirValueParameterSymbol): ExpEmbedding? {
        if (symbol == elementParameter) {
            throw UnsupportedFeatureException(
                symbol.source,
                "a loop invariant over the element; use `forEachIndexed` to state an invariant over the index",
            )
        }
        return inner.tryResolveParameter(symbol)
    }
}
