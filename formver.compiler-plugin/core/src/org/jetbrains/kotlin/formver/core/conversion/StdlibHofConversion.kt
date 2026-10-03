/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.toResolvedValueParameterSymbol
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
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.Implies
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.Not
import org.jetbrains.kotlin.formver.uniqueness.plugin.ElementLoop
import org.jetbrains.kotlin.formver.uniqueness.plugin.isIntArrayLoop
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val textPackage = FqName("kotlin.text")
private val collectionsPackage = FqName("kotlin.collections")

/** A stdlib function that calls its last argument, a lambda, for each index below a bound. */
private enum class StdlibHof(val callableName: Name, val arity: Int) {
    ForEach(ElementLoop.ForEach),
    ForEachIndexed(ElementLoop.ForEachIndexed),
    Count(ElementLoop.Count),
    AnyMatch(ElementLoop.AnyMatch),
    AllMatch(ElementLoop.AllMatch),
    NoneMatch(ElementLoop.NoneMatch),
    IndexOfFirst(ElementLoop.IndexOfFirst),
    FirstOrNull(ElementLoop.FirstOrNull),
    Repeat(Name.identifier("repeat"), arity = 2);

    constructor(loop: ElementLoop) : this(loop.callableName, arity = 1)

    /** Whether the loop stops at the first index where the lambda's test holds. */
    val searches: Boolean
        get() = this in AnyMatch..FirstOrNull
}

/**
 * The template [call] converts as, with the lambda it calls; `null` when [call] is not a call a template supports or
 * its last argument is neither a lambda nor an inline parameter bound to one.
 */
private fun StmtConversionContext.stdlibHof(call: FirFunctionCall): Pair<StdlibHof, LambdaExp>? {
    val callableId = call.toResolvedCallableSymbol()?.callableId ?: return null
    // The arity tells overloads without a lambda, such as `CharSequence.count()`, apart.
    val hof = StdlibHof.entries.firstOrNull {
        it.callableName == callableId.callableName && it.arity == call.arguments.size
    } ?: return null
    val supported = when (callableId.packageName) {
        FqName("kotlin") -> hof == StdlibHof.Repeat
        textPackage -> hof != StdlibHof.Repeat
        collectionsPackage -> call.isIntArrayLoop(session)
        else -> false
    }
    if (!supported) return null
    val lambda = when (val argument = call.arguments.last()) {
        is FirAnonymousFunctionExpression -> convert(argument)
        is FirPropertyAccessExpression ->
            argument.calleeReference.toResolvedValueParameterSymbol()?.let(::resolveParameter)
        else -> null
    }?.ignoringMetaNodes() as? LambdaExp ?: return null
    return hof to lambda
}

/**
 * The receiver a template iterates over, held in [variable]: its elements are [elementAt] each index below [length].
 * [factsReadable] says whether a pure expression over the elements may be stated in a loop invariant.
 */
private data class TemplateReceiver(
    val variable: VariableEmbedding,
    val length: ExpEmbedding,
    val elementAt: (ExpEmbedding) -> ExpEmbedding,
    val factsReadable: Boolean,
)

/**
 * Converts a call to a stdlib inline higher-order function whose body is not available to a loop over the index that
 * calls the lambda argument inlined; `null` when [call] is not such a call.
 *
 * The loop head knows the index's bounds. When the lambda's body starts with `loopInvariants { }`, its invariants hold
 * at the loop head too; there the lambda's index parameter is the loop's index, and its element parameter is not in
 * scope. A search (`any`, `all`, `none`, `indexOfFirst`, `firstOrNull`) records the first index where its test holds
 * and stops there; when the lambda's body is a single pure expression, the loop keeps that the test holds at that
 * index and at no index before it.
 */
fun StmtConversionContext.convertStdlibHof(call: FirFunctionCall): ExpEmbedding? {
    val (hof, lambda) = stdlibHof(call) ?: return null
    val intType = buildType { int() }
    val prelude = mutableListOf<ExpEmbedding>()
    // The point whose state holds at the loop head. A lambda bound to an inline parameter is analysed with the function
    // that passes it and cannot name this body's variables, and the call moves nothing, so the state before the call
    // stands in for it.
    val head: FirElement = lambda.function.takeIf { ownershipFrame.analysis?.hasState(it) == true } ?: call
    val receiver = if (hof == StdlibHof.Repeat) null else templateReceiver(call, hof, head, prelude)
    val bound = receiver?.length ?: declareAnonVar(intType, convert(call.arguments[0])).also(prelude::add).variable
    val index = freshAnonVar(intType)
    prelude.add(Declare(index, IntLit(0)))
    val count = if (hof == StdlibHof.Count) freshAnonVar(intType) else null
    count?.let { prelude.add(Declare(it, IntLit(0))) }
    // The first index where the test holds, or -1.
    val found = if (hof.searches) freshAnonVar(intType) else null
    found?.let { prelude.add(Declare(it, IntLit(-1))) }

    val parameters = lambda.function.valueParameters.map { it.symbol }
    val (indexParameter, elementParameter) = when (hof) {
        StdlibHof.ForEachIndexed -> parameters[0] to parameters[1]
        StdlibHof.Repeat -> parameters.single() to null
        else -> null to parameters.single()
    }
    val test: (ExpEmbedding) -> ExpEmbedding = { if (hof == StdlibHof.AllMatch) Not(it) else it }

    val inScope = ownershipFrame.scopeWith(retrievePropertiesAndParameters().toList())
    val typeInvariants =
        (inScope + listOfNotNull(receiver?.variable, index, count, found)).flatMap { it.provenInvariants() }
    val invariants = buildList {
        add(GeIntInt(index, IntLit(0)))
        // `repeat` with a negative count runs no iteration.
        add(if (receiver != null) LeIntInt(index, bound) else Or(LeIntInt(index, bound), EqCmp(index, IntLit(0))))
        count?.let { add(And(GeIntInt(it, IntLit(0)), LeIntInt(it, index))) }
        found?.let { add(And(GeIntInt(it, IntLit(-1)), LtIntInt(it, index))) }
        if (found != null && receiver?.factsReadable == true && elementParameter != null) {
            addAll(searchFacts(lambda, elementParameter, receiver, index, found, test))
        }
        lambda.function.body?.statements?.let(::extractLoopInvariants)?.let { block ->
            addAll(convertHoistedInvariants(block, indexParameter, index, elementParameter))
        }
    }
    // A lambda analysed in place has the state before it join the entry and the back edge.
    val headShapes = ownedShapes({ it.stateBefore(head) }, inScope)
    val exitShapes = ownedShapes({ it.stateAfter(call) }, inScope)

    val loop = withFreshWhile(label = null) {
        val element = receiver?.elementAt(index)
        val args = listOfNotNull(index.takeIf { indexParameter != null }, element)
        val invocation = withCallSite(call) { lambda.insertCall(args, this) }
        val unit = buildType { unit() }
        val body = when {
            count != null -> If(invocation, Assign(count, AddIntInt(count, IntLit(1))), UnitLit, unit)
            found != null -> If(test(invocation), Assign(found, index), UnitLit, unit)
            else -> invocation
        }
        val step = Assign(index, AddIntInt(index, IntLit(1)))
        val condition = LtIntInt(index, bound).let { if (found != null) And(it, LtIntInt(found, IntLit(0))) else it }
        loopOverUsedRoots(
            condition,
            blockOf(body, step),
            continueLabelName(),
            typeInvariants,
            invariants,
            headShapes,
            exitShapes,
        )
    }
    val result = when (hof) {
        StdlibHof.Count -> count!!
        StdlibHof.Repeat, StdlibHof.ForEach, StdlibHof.ForEachIndexed -> UnitLit
        else -> searchResult(call, hof, receiver!!, found!!)
    }
    return (prelude + loop + result).toBlock()
}

/**
 * The receiver of [call], a template over a receiver, declaring in [prelude] what it needs. Ownership of the receiver
 * is read at [head], the point whose state holds at the loop head.
 */
private fun StmtConversionContext.templateReceiver(
    call: FirFunctionCall,
    hof: StdlibHof,
    head: FirElement,
    prelude: MutableList<ExpEmbedding>,
): TemplateReceiver {
    val receiverExp = call.extensionReceiver
        ?: throw SnaktInternalException(call.source, "A call to `${hof.callableName}` has no receiver.")
    val packageName = call.toResolvedCallableSymbol()?.callableId?.packageName
    return when {
        packageName == textPackage && receiverExp.resolvedType.isString -> {
            val variable = declareAnonVar(buildType { string() }, convert(receiverExp)).also(prelude::add).variable
            TemplateReceiver(variable, StringLength(variable), { StringGet(variable, it) }, factsReadable = true)
        }
        packageName == textPackage -> throw UnsupportedFeatureException(
            call.source,
            "`${hof.callableName}` on a receiver that is not a `String`",
        )
        else -> {
            val array = convert(receiverExp)
            // A `val` is read in place, so that an owned array is read through its own predicate; the lambda
            // cannot reassign it.
            val inPlace = (array.ignoringMetaNodes() as? VariableEmbedding)
                ?.takeIf { (receiverExp.toResolvedCallableSymbol(session) as? FirVariableSymbol<*>)?.isVal == true }
            val variable = inPlace ?: declareAnonVar(embedType(receiverExp), array).also(prelude::add).variable
            val owned = inPlace != null && ownsBefore(head, receiverExp)
            TemplateReceiver(
                variable,
                IntArraySize(variable),
                { IntArrayGet(variable, it, owned) },
                factsReadable = owned,
            )
        }
    }
}

/** The value of the search [hof] over [receiver], given [found], the first index where its test holds or -1. */
private fun StmtConversionContext.searchResult(
    call: FirFunctionCall,
    hof: StdlibHof,
    receiver: TemplateReceiver,
    found: VariableEmbedding,
): ExpEmbedding = when (hof) {
    StdlibHof.AnyMatch -> GeIntInt(found, IntLit(0))
    StdlibHof.AllMatch, StdlibHof.NoneMatch -> LtIntInt(found, IntLit(0))
    StdlibHof.IndexOfFirst -> found
    StdlibHof.FirstOrNull -> {
        val type = embedType(call)
        If(GeIntInt(found, IntLit(0)), receiver.elementAt(found).withType(type), NullLit.withType(type), type)
    }
    else -> throw SnaktInternalException(call.source, "`${hof.callableName}` is not a search.")
}

/**
 * The facts a search loop keeps when [lambda]'s body is a pure expression: the [test] of its value holds at [found]
 * when [found] is an index, and at no index below both [index] and [found].
 */
private fun StmtConversionContext.searchFacts(
    lambda: LambdaExp,
    elementParameter: FirValueParameterSymbol,
    receiver: TemplateReceiver,
    index: VariableEmbedding,
    found: VariableEmbedding,
    test: (ExpEmbedding) -> ExpEmbedding,
): List<ExpEmbedding> {
    fun holdsAt(at: ExpEmbedding) =
        pureLambdaValue(lambda, mapOf(elementParameter to receiver.elementAt(at)))?.let(test)
    val j = freshAnonBuiltinVar(buildType { int() })
    val atFound = holdsAt(found) ?: return emptyList()
    val atJ = holdsAt(j) ?: return emptyList()
    val searched =
        And(And(GeIntInt(j, IntLit(0)), LtIntInt(j, index)), Or(LtIntInt(found, IntLit(0)), LtIntInt(j, found)))
    return listOf(
        Implies(GeIntInt(found, IntLit(0)), atFound),
        ForAllEmbedding(j, listOf(Implies(searched, Not(atJ)))),
    )
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
