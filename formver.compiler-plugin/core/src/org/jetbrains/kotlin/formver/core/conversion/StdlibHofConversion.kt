/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirCheckedSafeCallSubject
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.toResolvedValueParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.isString
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.core.isFormverFunctionNamed
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
import org.jetbrains.kotlin.formver.core.embeddings.types.nullableAny
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.Implies
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.Not
import org.jetbrains.kotlin.formver.intrinsics.plugin.ElementLoop
import org.jetbrains.kotlin.formver.intrinsics.plugin.isIntArrayLoop
import org.jetbrains.kotlin.formver.intrinsics.plugin.isCollectionLoop
import org.jetbrains.kotlin.formver.uniqueness.plugin.Uniqueness
import org.jetbrains.kotlin.formver.uniqueness.plugin.resolveUniqueness
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.formver.core.embeddings.expression.OperatorExpEmbeddings.IterationSequenceSize
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val textPackage = FqName("kotlin.text")
private val collectionsPackage = FqName("kotlin.collections")

/** A stdlib function that calls its last argument, a lambda, for each index below a bound. */
private enum class StdlibHof(val callableName: Name, val arity: Int) {
    ForEach(ElementLoop.ForEach),
    ForEachIndexed(ElementLoop.ForEachIndexed),
    Count(ElementLoop.Count),
    Fold(ElementLoop.Fold),
    MapElements(ElementLoop.MapElements),
    FilterElements(ElementLoop.FilterElements),
    AnyMatch(ElementLoop.AnyMatch),
    AllMatch(ElementLoop.AllMatch),
    NoneMatch(ElementLoop.NoneMatch),
    IndexOfFirst(ElementLoop.IndexOfFirst),
    FirstOrNull(ElementLoop.FirstOrNull),
    Repeat(Name.identifier("repeat"), arity = 2),
    Let(Name.identifier("let"), arity = 1),
    Also(Name.identifier("also"), arity = 1),
    Apply(Name.identifier("apply"), arity = 1),
    Run(Name.identifier("run"), arity = 1),
    With(Name.identifier("with"), arity = 2);

    val isScope: Boolean
        get() = this in Let..With

    constructor(loop: ElementLoop) : this(loop.callableName, arity = loop.arity)

    /** Whether the loop stops at the first index where the lambda's test holds. */
    val searches: Boolean
        get() = this in AnyMatch..FirstOrNull
}

private fun stdlibHofKind(symbol: FirCallableSymbol<*>, arity: Int): StdlibHof? {
    val id = symbol.callableId ?: return null
    if (id.className != null) return null
    val hof = StdlibHof.entries.firstOrNull { it.callableName == id.callableName && it.arity == arity } ?: return null
    val receiver = symbol.resolvedReceiverTypeRef?.coneType
    val function = symbol as? FirFunctionSymbol<*> ?: return null
    val lambdaArity = when (hof) {
        StdlibHof.ForEachIndexed, StdlibHof.Fold -> 2
        StdlibHof.Run -> if (receiver == null) 0 else 1
        else -> 1
    }
    if (function.valueParameterSymbols.last().resolvedReturnType.classId != ClassId.topLevel(FqName("kotlin.Function$lambdaArity"))) return null
    return when (id.packageName) {
        FqName("kotlin") -> hof.takeIf {
            when (it) {
                StdlibHof.Repeat, StdlibHof.With -> receiver == null
                StdlibHof.Run -> true
                else -> it.isScope && receiver != null
            }
        }
        collectionsPackage -> hof.takeIf { symbol.isCollectionLoop() || symbol.isIntArrayLoop() }
        textPackage -> hof.takeIf {
            it != StdlibHof.Fold && it != StdlibHof.Repeat && !it.isScope &&
                    it != StdlibHof.MapElements && it != StdlibHof.FilterElements &&
                    receiver?.classId in listOf(ClassId.topLevel(FqName("kotlin.CharSequence")), ClassId.topLevel(FqName("kotlin.String")))
        }
        else -> null
    }
}

/** Whether a callable belongs to the higher-order conversion path, including its opaque function-value calls. */
internal fun hasStdlibHofTemplate(symbol: FirFunctionSymbol<*>): Boolean =
    stdlibHofKind(symbol, symbol.valueParameterSymbols.size) != null

/**
 * The template [call] converts as, with the lambda it calls; `null` when [call] is not a call a template supports or
 * its last argument is neither a lambda nor an inline parameter bound to one.
 */
private fun StmtConversionContext.stdlibHof(call: FirFunctionCall): Pair<StdlibHof, LambdaExp>? {
    val symbol = call.toResolvedCallableSymbol() ?: return null
    // The arity tells overloads without a lambda, such as `CharSequence.count()`, apart.
    val hof = stdlibHofKind(symbol, call.arguments.size) ?: return null
    if (call.isCollectionLoop() && !ownsCollectionReceiver(call)) return null
    val lambda = when (val argument = call.arguments.last()) {
        is FirAnonymousFunctionExpression -> convert(argument)
        is FirPropertyAccessExpression ->
            argument.calleeReference.toResolvedValueParameterSymbol()?.let(::resolveParameter)
        else -> null
    }?.ignoringMetaNodes() as? LambdaExp ?: return null
    return hof to lambda
}

/**
 * Whether the receiver of the collection loop [call] is `Unique` on entry to [call]: the uniqueness checker finds the
 * path it denotes `Unique` there, or, when it denotes no path, declares it `Unique`. The collection template reads the
 * receiver's iteration observer, which needs it owned; any other receiver is iterated by the opaque library call.
 */
private fun StmtConversionContext.ownsCollectionReceiver(call: FirFunctionCall): Boolean {
    val receiver = call.extensionReceiver ?: return false
    val denoted = (receiver as? FirCheckedSafeCallSubject)?.originalReceiverRef?.value ?: receiver
    if (ownershipFrame.pathOf(denoted) != null) return ownsBefore(call, receiver)
    return context(checkerContext) { denoted.resolveUniqueness() } == Uniqueness.Unique
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
    val snapshotInvariant: ExpEmbedding? = null,
    val owner: VariableEmbedding? = null,
)

/**
 * Converts a call to a stdlib inline higher-order function whose body is not available to a loop over the index that
 * calls the lambda argument inlined; `null` when [call] is not such a call.
 *
 * The loop head knows the index's bounds. Leading lambda invariants see the index, a fold accumulator, and an
 * optional iteration prefix; the element parameter is not in scope. A search (`any`, `all`, `none`, `indexOfFirst`,
 * `firstOrNull`) records the first index where its test holds and stops there; when the lambda's body is a single
 * pure expression, the loop keeps that the test holds at that
 * index and at no index before it.
 */
fun StmtConversionContext.convertStdlibHof(call: FirFunctionCall): ExpEmbedding? {
    val (hof, lambda) = stdlibHof(call) ?: return null
    return when {
        hof.isScope -> convertScopeCall(call, hof, lambda)
        hof == StdlibHof.MapElements || hof == StdlibHof.FilterElements ->
            convertElementTemplate(call, hof, lambda, TemplateFamily.Builder)
        hof == StdlibHof.Fold || hof == StdlibHof.Count || hof.searches ->
            convertElementTemplate(call, hof, lambda, TemplateFamily.Reduction)
        else -> convertElementTemplate(call, hof, lambda, TemplateFamily.Visitor)
    }
}

private enum class TemplateFamily { Visitor, Builder, Reduction }

private fun StmtConversionContext.convertElementTemplate(
    call: FirFunctionCall,
    hof: StdlibHof,
    lambda: LambdaExp,
    family: TemplateFamily,
): ExpEmbedding {
    val intType = buildType { int() }
    val prelude = mutableListOf<ExpEmbedding>()
    // The point whose state holds at the loop head. A lambda bound to an inline parameter is analysed with the function
    // that passes it and cannot name this body's variables, and the call moves nothing, so the state before the call
    // stands in for it.
    val head: FirElement = lambda.function.takeIf { ownershipFrame.analysis?.hasState(it) == true } ?: call
    val receiver = if (hof == StdlibHof.Repeat) null else templateReceiver(call, hof, lambda, head, prelude)
    val bound = receiver?.length ?: declareAnonVar(intType, convert(call.arguments[0])).also(prelude::add).variable
    val index = freshAnonVar(intType)
    prelude.add(Declare(index, IntLit(0)))
    val count = if (hof == StdlibHof.Count) freshAnonVar(intType) else null
    count?.let { prelude.add(Declare(it, IntLit(0))) }
    // The first index where the test holds, or -1.
    val found = if (hof.searches) freshAnonVar(intType) else null
    found?.let { prelude.add(Declare(it, IntLit(-1))) }

    val output = if (hof == StdlibHof.MapElements || hof == StdlibHof.FilterElements) {
        freshAnonVar(buildType { iterationSequence() }).also {
            prelude.add(Declare(it, WithPosition(IterationSequenceOf(emptyList()), checkNotNull(call.source), specificationSnapshot = true)))
        }
    } else null
    val accumulator = if (hof == StdlibHof.Fold) {
        declareAnonVar(embedType(call), convert(call.arguments.first())).also(prelude::add).variable
    } else null
    val parameters = lambda.function.valueParameters.map { it.symbol }
    val accumulatorParameter = if (accumulator != null) parameters.first() else null
    val (indexParameter, elementParameter) = when (hof) {
        StdlibHof.ForEachIndexed -> parameters[0] to parameters[1]
        StdlibHof.Repeat -> parameters.single() to null
        StdlibHof.Fold -> null to parameters.last()
        else -> null to parameters.single()
    }
    val state = TemplateLoopState(index, count, found, accumulator, output)

    val inScope = ownershipFrame.scopeWith(retrievePropertiesAndParameters().toList())
    val typeInvariants =
        (inScope + listOfNotNull(receiver?.variable, receiver?.owner, index, count, found, accumulator, output)).flatMap { it.provenInvariants() }
    val invariants = templateInvariants(
        call, hof, lambda, receiver, bound,
        state,
        TemplateLambdaParameters(indexParameter, elementParameter, accumulatorParameter),
    )
    // A lambda analysed in place has the state before it join the entry and the back edge.
    val temporaryShapes = listOfNotNull(receiver?.owner?.let { org.jetbrains.kotlin.formver.core.linearization.OwnedShape(it, emptyList()) })
    val headShapes = ownedShapes({ it.stateBefore(head) }, inScope) + temporaryShapes
    val exitShapes = ownedShapes({ it.stateAfter(call) }, inScope) + temporaryShapes

    val loop = withFreshWhile(label = null) {
        val element = receiver?.elementAt(index)
        val args = listOfNotNull(accumulator, index.takeIf { indexParameter != null }, element)
        val invocation = withCallSite(call) { lambda.insertCall(args, this) }
        val body = when (family) {
            TemplateFamily.Visitor -> invocation
            TemplateFamily.Builder -> builderIteration(call, hof, invocation, output!!, element!!)
            TemplateFamily.Reduction -> reductionIteration(hof, invocation, state)
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
        StdlibHof.Fold -> accumulator!!
        StdlibHof.MapElements, StdlibHof.FilterElements -> allocateCollectionList(call, output!!)
        StdlibHof.Repeat, StdlibHof.ForEach, StdlibHof.ForEachIndexed -> UnitLit
        else -> searchResult(call, hof, receiver!!, found!!)
    }
    return (prelude + loop + result).toBlock()
}

private fun StmtConversionContext.builderIteration(
    call: FirFunctionCall,
    hof: StdlibHof,
    invocation: ExpEmbedding,
    output: VariableEmbedding,
    element: ExpEmbedding,
): ExpEmbedding {
    // isCollectionLoop restricts builders to List results and admits Map entries only for map;
    // Map.filter is rejected during classification.
    val mapped = if (hof == StdlibHof.MapElements) declareAnonVar(invocation.type, invocation) else null
    val value = mapped?.variable ?: element
    val append = WithPosition(
        Assign(output, OperatorExpEmbeddings.IterationSequenceAppend(output, IterationSequenceOf(listOf(value)))),
        checkNotNull(call.source), specificationSnapshot = true,
    )
    return if (hof == StdlibHof.FilterElements) If(invocation, append, UnitLit, buildType { unit() })
    else blockOf(mapped!!, append, UnitLit)
}

private fun reductionIteration(hof: StdlibHof, invocation: ExpEmbedding, state: TemplateLoopState): ExpEmbedding {
    val unit = buildType { unit() }
    val (index, count, found, accumulator) = state
    return when {
        accumulator != null -> Assign(accumulator, invocation)
        count != null -> If(invocation, Assign(count, AddIntInt(count, IntLit(1))), UnitLit, unit)
        else -> {
            val test = if (hof == StdlibHof.AllMatch) Not(invocation) else invocation
            If(test, Assign(found!!, index), UnitLit, unit)
        }
    }
}

private data class TemplateLoopState(
    val index: VariableEmbedding,
    val count: VariableEmbedding?,
    val found: VariableEmbedding?,
    val accumulator: VariableEmbedding?,
    val output: VariableEmbedding?,
)

private data class TemplateLambdaParameters(
    val index: FirValueParameterSymbol?,
    val element: FirValueParameterSymbol?,
    val accumulator: FirValueParameterSymbol?,
)

private fun StmtConversionContext.templateInvariants(
    call: FirFunctionCall,
    hof: StdlibHof,
    lambda: LambdaExp,
    receiver: TemplateReceiver?,
    bound: ExpEmbedding,
    state: TemplateLoopState,
    parameters: TemplateLambdaParameters,
): List<ExpEmbedding> {
    val (index, count, found, accumulator, output) = state
    val (indexParameter, elementParameter, accumulatorParameter) = parameters
    return buildList {
        add(GeIntInt(index, IntLit(0)))
        // `repeat` with a negative count runs no iteration.
        add(if (receiver != null) LeIntInt(index, bound) else Or(LeIntInt(index, bound), EqCmp(index, IntLit(0))))
        receiver?.snapshotInvariant?.let(::add)
        count?.let { add(And(GeIntInt(it, IntLit(0)), LeIntInt(it, index))) }
        found?.let { add(And(GeIntInt(it, IntLit(-1)), LtIntInt(it, index))) }
        output?.let {
            val size = IterationSequenceSize(it)
            add(GeIntInt(size, IntLit(0)))
            add(if (hof == StdlibHof.MapElements) EqCmp(size, index) else LeIntInt(size, index))
            addAll(builderFacts(hof, lambda, elementParameter!!, receiver!!, index, it))
        }
        if (found != null && receiver?.factsReadable == true && elementParameter != null) {
            val test: (ExpEmbedding) -> ExpEmbedding = { if (hof == StdlibHof.AllMatch) Not(it) else it }
            addAll(searchFacts(lambda, elementParameter, receiver, index, found, test))
        }
        lambda.function.body?.statements?.firstOrNull()
            ?.extractFormverFirBlock { isFormverFunctionNamed("loopInvariants") }?.let { specification ->
                val prefixParameter = specification.valueParameters.singleOrNull()?.symbol
                val prefix = prefixParameter?.let {
                    if (!call.isCollectionLoop()) {
                        throw UnsupportedFeatureException(specification.source, "an iteration prefix invariant on this receiver")
                    }
                    OperatorExpEmbeddings.IterationSequenceTake(receiver!!.variable, index)
                }
                addAll(
                    convertHoistedInvariants(
                        checkNotNull(specification.body), indexParameter, index, elementParameter,
                        listOfNotNull(
                            accumulatorParameter?.let { it to accumulator!! },
                            prefixParameter?.let { it to prefix!! },
                        ).toMap(),
                    )
                )
            }
    }
}

/** The shared trusted allocation boundary for collection builders. */
internal fun StmtConversionContext.allocateCollectionList(call: FirFunctionCall, sequence: ExpEmbedding): ExpEmbedding {
    val result = freshAnonVar(embedType(call))
    return CollectionListAllocation(result, WithPosition(
        EqCmp(collectionIterationSequence(result), sequence), checkNotNull(call.source), specificationSnapshot = true,
    ))
}

private fun StmtConversionContext.builderFacts(
    hof: StdlibHof,
    lambda: LambdaExp,
    parameter: FirValueParameterSymbol,
    receiver: TemplateReceiver,
    index: VariableEmbedding,
    output: VariableEmbedding,
): List<ExpEmbedding> {
    val j = freshAnonBuiltinVar(buildType { int() })
    val source = receiver.elementAt(j).ignoringCastsAndMetaNodes()
    val mapped = pureLambdaValue(lambda, mapOf(parameter to source)) ?: return emptyList()
    val size = IterationSequenceSize(output)
    if (hof == StdlibHof.MapElements) {
        val value = IterationSequenceGet(output, j, mapped.type)
        val referenceType = buildType { nullableAny() }
        return listOf(ForAllEmbedding(j, listOf(Implies(
            And(And(GeIntInt(j, IntLit(0)), LtIntInt(j, index)), source.type.subTypeInvariant().fillHole(source)),
            EqCmp(value.withType(referenceType), mapped.withType(referenceType)),
        ))))
    }
    if (mapped.subtree().none { it == j }) {
        return listOf(EqCmp(size, If(mapped, index, IntLit(0), buildType { int() })))
    }
    val value = IterationSequenceGet(output, j, source.type, direct = true)
    val predicate = pureLambdaValue(lambda, mapOf(parameter to value)) ?: return emptyList()
    return listOf(ForAllEmbedding(j, listOf(Implies(
        And(GeIntInt(j, IntLit(0)), LtIntInt(j, size)),
        predicate,
    ))))
}

/** Calls the scope lambda through the same inlining path as source inline functions. */
private fun StmtConversionContext.convertScopeCall(
    call: FirFunctionCall,
    hof: StdlibHof,
    lambda: LambdaExp,
): ExpEmbedding {
    val receiver = if (hof == StdlibHof.With) call.arguments.first() else call.extensionReceiver
    val declaration = receiver?.let { declareAnonVar(embedType(it), convert(it)) }
    val invocation = withCallSite(call) { lambda.insertCall(listOfNotNull(declaration?.variable), this) }
    val result = if (hof == StdlibHof.Also || hof == StdlibHof.Apply) {
        blockOf(invocation, declaration!!.variable)
    } else invocation
    return (listOfNotNull(declaration) + result).toBlock()
}

/**
 * The receiver of [call], a template over a receiver, declaring in [prelude] what it needs. Ownership of the receiver
 * is read at [head], the point whose state holds at the loop head.
 */
private fun StmtConversionContext.templateReceiver(
    call: FirFunctionCall,
    hof: StdlibHof,
    lambda: LambdaExp,
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
        call.isCollectionLoop() -> {
            val value = convert(receiverExp)
            val owner = if (value.ignoringCastsAndMetaNodes() is VariableEmbedding) null else {
                freshAnonVar(embedType(receiverExp)).also { prelude.add(Declare(it, value, targetOwned = true)) }
            }
            val collection = owner ?: value
            // The declared receiver selects the observer, including entry-valued Map iteration.
            val map = call.toResolvedCallableSymbol()?.resolvedReceiverTypeRef?.coneType?.classId ==
                    ClassId.topLevel(FqName("kotlin.collections.Map"))
            val sequence = declareAnonVar(
                buildType { iterationSequence() },
                WithPosition(collectionIterationSequence(collection, map), checkNotNull(call.source), specificationSnapshot = true),
            ).also(prelude::add).variable
            val elementType = embedType(lambda.function.valueParameters.last().symbol.resolvedReturnType)
            val j = freshAnonBuiltinVar(buildType { int() })
            // Kotlin's substituted element type supplies the same typing assumption as an individual read.
            prelude.add(InhaleDirect(ForAllEmbedding(j, listOf(Implies(
                And(GeIntInt(j, IntLit(0)), LtIntInt(j, IterationSequenceSize(sequence))),
                elementType.subTypeInvariant().fillHole(IterationSequenceGet(sequence, j, elementType, direct = true)),
            )))))
            TemplateReceiver(
                sequence,
                IterationSequenceSize(sequence),
                { IterationSequenceGet(sequence, it, elementType).withInvariants(typeResolver) { proven = true } },
                factsReadable = true,
                snapshotInvariant = EqCmp(sequence, collectionIterationSequence(collection, map)),
                owner = owner,
            )
        }
        else -> {
            // stdlibHofKind restricts this remaining receiver to IntArray.
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
        // stdlibHofKind excludes Map searches, so the witness has the result's element representation.
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
        pureLambdaValue(lambda, mapOf(elementParameter to receiver.elementAt(at).ignoringCastsAndMetaNodes()))?.let(test)
    val j = freshAnonBuiltinVar(buildType { int() })
    val atFound = holdsAt(found) ?: return emptyList()
    val atJ = holdsAt(j) ?: return emptyList()
    // A parameter-independent predicate has no quantified element term to trigger on.
    if (atJ.subtree().none { it == j }) {
        return listOf(
            Implies(GeIntInt(found, IntLit(0)), atFound),
            Implies(
                GeIntInt(index, IntLit(1)),
                EqCmp(found, If(atJ, IntLit(0), IntLit(-1), buildType { int() })),
            ),
        )
    }
    val searched =
        And(And(GeIntInt(j, IntLit(0)), LtIntInt(j, index)), Or(LtIntInt(found, IntLit(0)), LtIntInt(j, found)))
    return listOf(
        Implies(GeIntInt(found, IntLit(0)), atFound),
        ForAllEmbedding(j, listOf(Implies(searched, Not(atJ)))),
    )
}

/**
 * The invariants of [block], written at the start of a lambda, converted for the head of the loop that calls the
 * lambda: [indexParameter] stands for [index], [additionalParameters] binds loop state, and [elementParameter]
 * may not be read.
 */
private fun StmtConversionContext.convertHoistedInvariants(
    block: FirBlock,
    indexParameter: FirValueParameterSymbol?,
    index: VariableEmbedding,
    elementParameter: FirValueParameterSymbol?,
    additionalParameters: Map<FirValueParameterSymbol, ExpEmbedding> = emptyMap(),
): List<ExpEmbedding> {
    val resolver = LoopHeadParameterResolver(
        InlineParameterResolver(
            substitutions = (additionalParameters + listOfNotNull(indexParameter).associateWith { index })
                .mapKeys { SubstitutedArgument.ValueParameter(it.key) },
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
