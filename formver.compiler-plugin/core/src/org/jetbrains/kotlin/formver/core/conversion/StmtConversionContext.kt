/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirLabel
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.expressions.FirOperation
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.isBoolean
import org.jetbrains.kotlin.fir.types.isUnit
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.core.embeddings.FunctionBodyEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.LabelEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.toLink
import org.jetbrains.kotlin.formver.core.embeddings.callables.FunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.callables.NamedFunctionSignatureWithContract
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.properties.BackingFieldGetter
import org.jetbrains.kotlin.formver.core.embeddings.properties.ClassPropertyAccess
import org.jetbrains.kotlin.formver.core.embeddings.properties.PropertyAccessEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.PropertyEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.asPropertyAccess
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.uniqueness.plugin.indexedArrayInitializer
import org.jetbrains.kotlin.formver.uniqueness.plugin.FunctionUniquenessAnalysis
import org.jetbrains.kotlin.formver.uniqueness.plugin.UniquenessState
import org.jetbrains.kotlin.formver.uniqueness.plugin.isCustom
import org.jetbrains.kotlin.formver.core.isInvariantBuilderFunctionNamed
import org.jetbrains.kotlin.formver.core.linearization.*
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.jetbrains.kotlin.utils.filterIsInstanceAnd

/**
 * Interface for statement conversion.
 *
 * Naming convention:
 * - Functions that return a new `StmtConversionContext` should describe what change they make (`addResult`, `removeResult`...)
 * - Functions that take a lambda to execute should describe what extra state the lambda will have (`withResult`...)
 */
interface StmtConversionContext : MethodConversionContext {
    val whenSubject: VariableEmbedding?

    /**
     * In a safe call `callSubject?.foo()` we evaluate the call subject first to check for nullness.
     * In case it is not null, we evaluate the call to `callSubject.foo()`. Here we don't want to evaluate
     * the `callSubject` again to we store it in the `StmtConversionContext`.
     */
    val checkedSafeCallSubject: ExpEmbedding?
    val activeCatchLabels: List<LabelEmbedding>

    fun continueLabelName(targetName: String? = null): SymbolicName
    fun breakLabelName(targetName: String? = null): SymbolicName

    /**
     * The head of the innermost loop, for a loop whose `continue` reaches a step before the head.
     */
    fun loopHeadLabelName(): SymbolicName
    fun addLoopName(targetName: String)
    fun convert(stmt: FirStatement): ExpEmbedding

    fun <R> withNewScope(action: StmtConversionContext.() -> R): R
    fun <R> withNoScope(action: StmtConversionContext.() -> R): R
    fun <R> withMethodCtx(factory: MethodContextFactory, action: StmtConversionContext.() -> R): R

    fun <R> withFreshWhile(label: FirLabel?, action: StmtConversionContext.() -> R): R
    fun <R> withWhenSubject(subject: VariableEmbedding?, action: StmtConversionContext.() -> R): R
    fun <R> withCheckedSafeCallSubject(subject: ExpEmbedding?, action: StmtConversionContext.() -> R): R
    fun <R> withCatches(
        catches: List<FirCatch>,
        action: StmtConversionContext.(catchBlockListData: CatchBlockListData) -> R,
    ): Pair<CatchBlockListData, R>
}

fun StmtConversionContext.declareLocalProperty(symbol: FirPropertySymbol, initializer: ExpEmbedding?): Declare {
    registerLocalProperty(symbol)
    val variable = embedLocalProperty(symbol)
    return Declare(variable, initializer?.withType(variable.type))
}

fun StmtConversionContext.declareLocalVariable(symbol: FirVariableSymbol<*>, initializer: ExpEmbedding?): Declare {
    registerLocalVariable(symbol)
    val variable = embedLocalVariable(symbol)
    return Declare(variable, initializer?.withType(variable.type))
}

fun StmtConversionContext.declareAnonVar(type: TypeEmbedding, initializer: ExpEmbedding?): Declare {
    val variable = freshAnonVar(type)
    return Declare(variable, initializer?.withType(variable.type))
}


val FirIntersectionOverridePropertySymbol.propertyIntersections
    get() = intersections.filterIsInstanceAnd<FirPropertySymbol> { it.isVal == isVal }

/**
 * Tries to find final property symbol actually declared in some class instead of
 * (potentially) fake property symbol.
 * Note that if some property is found it is fixed since
 * 1. there can't be two non-abstract properties which don't subsume each other
 * in the hierarchy (kotlin disallows that) and final properties can't be abstract;
 * 2. final property can't subsume other final property as that means final property
 * is overridden.
 * //TODO: decide if we leave this lookup or consider it unsafe.
 */
fun FirPropertySymbol.findFinalParentProperty(): FirPropertySymbol? =
    if (this !is FirIntersectionOverridePropertySymbol)
        (isFinal && !isCustom).ifTrue { this }
    else propertyIntersections.firstNotNullOfOrNull { it.findFinalParentProperty() }


/**
 * This is a key function when looking up properties.
 * It translates a kotlin `receiver.field` expression to an `ExpEmbedding`.
 *
 * Note that in FIR this `field` may be represented as `FirIntersectionOverridePropertySymbol`
 * which is necessary when the property could hypothetically inherit from multiple sources.
 * However, we don't register such symbols in the context when traversing the class.
 * Hence, some advanced logic is needed here.
 *
 * First, we try to find an actual backing field somewhere in the parents of the field with a
 * dfs-like algorithm on `FirIntersectionOverridePropertySymbol`s (it also should be final).
 *
 * If final backing field is not found, we lazily create a getter/setter pair for this
 * `FirIntersectionOverrideProperty`.
 */
fun StmtConversionContext.embedPropertyAccess(
    accessExpression: FirPropertyAccessExpression,
    accessElement: FirElement = accessExpression,
): PropertyAccessEmbedding =
    when (val calleeSymbol = accessExpression.calleeReference.symbol) {
        is FirValueParameterSymbol -> embedParameter(calleeSymbol).asPropertyAccess()
        is FirPropertySymbol -> {
            val type = embedType(calleeSymbol.resolvedReturnType)
            when {
                accessExpression.dispatchReceiver != null -> {
                    val property = calleeSymbol.findFinalParentProperty()?.let {
                        embedProperty(it)
                    } ?: embedProperty(calleeSymbol)
                    val receiver = accessExpression.dispatchReceiver!!
                    ClassPropertyAccess(convert(receiver), property, type, ownsFor(accessElement, receiver, property))
                }

                accessExpression.extensionReceiver != null -> {
                    val property = embedProperty(calleeSymbol)
                    val receiver = accessExpression.extensionReceiver!!
                    ClassPropertyAccess(convert(receiver), property, type, ownsFor(accessElement, receiver, property))
                }

                else -> embedLocalProperty(calleeSymbol)
            }
        }

        else ->
            error("Property access symbol $calleeSymbol has unsupported type.")
    }


/**
 * Whether the uniqueness checker finds the path [expression] denotes `Unique` on entry to [element]. The checked
 * subject of a safe call denotes the path of the safe call's receiver.
 */
fun StmtConversionContext.ownsBefore(element: FirElement, expression: FirExpression): Boolean {
    val analysis = uniquenessAnalysis ?: return false
    val denoted = (expression as? FirCheckedSafeCallSubject)?.originalReceiverRef?.value ?: expression
    val path = analysis.pathOf(denoted) ?: return false
    return analysis.ownsBefore(element, path)
}

/**
 * Whether [receiver] is owned for an access to [property] at [element]: the uniqueness checker finds its path `Unique`,
 * and the backing field of [property] or, for a `@Unique` `val`, the property itself is declared on the class chain of
 * the path's static type, so the path's predicate holds it. A property reached only through a cast to a subtype is
 * accessed as shared.
 */
private fun StmtConversionContext.ownsFor(element: FirElement, receiver: FirExpression, property: PropertyEmbedding): Boolean {
    if (!ownsBefore(element, receiver)) return false
    val step = (property.getter as? BackingFieldGetter)?.field ?: property.ownedStep ?: return true
    val static = embedType(receiver.withoutCasts().resolvedType).pretype as? ClassTypeEmbedding ?: return true
    return typeResolver.declaresOnChain(static, step)
}

private fun FirExpression.withoutCasts(): FirExpression = when (this) {
    is FirSmartCastExpression -> originalExpression.withoutCasts()
    is FirCheckedSafeCallSubject -> originalReceiverRef.value.withoutCasts()
    is FirTypeOperatorCall -> when (operation) {
        FirOperation.AS, FirOperation.SAFE_AS -> argument.withoutCasts()
        else -> this
    }
    else -> this
}

/**
 * Warns at [write] when it stores through [receiver] without owning it and [receiver] reads a local initialized from
 * a constructor call. Such a local is `Shared` unless declared `@Unique`, so the write is dropped.
 */
fun StmtConversionContext.warnIfUntrackedWrite(write: FirElement, receiver: FirExpression, owned: Boolean) {
    if (!owned && receiver.readsConstructedLocal()) reportUntrackedWrite(write.source)
}

private fun FirExpression.readsConstructedLocal(): Boolean {
    val access = (this as? FirSmartCastExpression)?.originalExpression ?: this
    val symbol = (access as? FirPropertyAccessExpression)?.calleeReference?.symbol as? FirPropertySymbol ?: return false
    symbol.indexedArrayInitializer?.let { return it.readsConstructedLocal() }
    val initializer = symbol.resolvedInitializer as? FirFunctionCall ?: return false
    return symbol.isLocal && initializer.calleeReference.symbol is FirConstructorSymbol
}

/**
 * Whether the function being converted may hold a permission beyond a single expression: its signature carries
 * `@Unique` or `@Borrowed`, or the uniqueness checker finds a path that is not `Shared` somewhere in it.
 */
fun StmtConversionContext.holdsOwnership(): Boolean {
    val annotatedSignature = signature.formalArgs.any { it.isUnique || it.isBorrowed } ||
            signature.callableType.returnsUnique
    return annotatedSignature || uniquenessAnalysis?.sharesEveryPath == false
}

fun StmtConversionContext.argumentDeclaration(
    arg: ExpEmbedding,
    callType: TypeEmbedding
): Pair<Declare?, ExpEmbedding> =
    when (arg.ignoringMetaNodes()) {
        is LambdaExp -> null to arg
        else -> {
            val argWithInvariants = arg.withNewTypeInvariants(callType, typeResolver) {
                proven = true
                access = true
            }
            // If `argWithInvariants` is `Cast(...(Cast(someVariable))...)` it is fine to use it
            // since in Viper it will always be translated to `someVariable`.
            // On other hand, `TypeEmbedding` and invariants in Viper are guaranteed
            // via previous line.
            if (argWithInvariants.underlyingVariable != null) null to argWithInvariants
            else declareAnonVar(callType, argWithInvariants).let {
                it to it.variable
            }
        }
    }

fun StmtConversionContext.getInlineFunctionCallArgs(
    args: List<ExpEmbedding>,
    formalArgTypes: List<TypeEmbedding>,
): Pair<List<Declare>, List<ExpEmbedding>> {
    val declarations = mutableListOf<Declare>()
    val storedArgs = args.zip(formalArgTypes).map { (arg, callType) ->
        argumentDeclaration(arg, callType).let { (declaration, usage) ->
            declarations.addIfNotNull(declaration)
            usage
        }
    }
    return Pair(declarations, storedArgs)
}

fun StmtConversionContext.insertInlineFunctionCall(
    calleeSignature: FunctionSignature,
    paramNames: List<SubstitutedArgument>,
    args: List<ExpEmbedding>,
    body: FirBlock,
    returnTargetName: String?,
    parentCtx: MethodConversionContext? = null,
): ExpEmbedding {
    // TODO: It seems like it may be possible to avoid creating a local here, but it is not clear how.
    val returnTarget = returnTargetProducer.getFresh(calleeSignature.callableType.returnType)
    assert(returnTarget.label != null) {
        "Return target label not found for function ${calleeSignature.callableType.name}"
    }
    val (declarations, callArgs) = getInlineFunctionCallArgs(args, calleeSignature.callableType.formalArgTypes)
    val subs = paramNames.zip(callArgs).toMap()
    // A lambda called in place is analysed as part of the function containing it; an inline function is not.
    val enclosingAnalysis = parentCtx?.uniquenessAnalysis
    val bodyAnalysis = enclosingAnalysis?.takeIf { it.hasState(body) }
    if (enclosingAnalysis != null && bodyAnalysis == null && enclosingAnalysis.ownsAnyPath) {
        reportUnsupportedOwnership(body.source, "The uniqueness checker has no state for this lambda body.")
    }
    val methodCtxFactory = MethodContextFactory(
        calleeSignature,
        InlineParameterResolver(subs, returnTargetName, returnTarget),
        uniquenessAnalysis = bodyAnalysis,
        parent = parentCtx,
    )

    return withMethodCtx(methodCtxFactory) {
        Block {
            add(Declare(returnTarget.variable, null))
            addAll(declarations)
            add(FunctionExp(null, convert(body), returnTarget.label!!))
            // if unit is what we return we might not guarantee it yet
            add(returnTarget.variable.withIsUnitInvariantIfUnit(typeResolver))
        }
    }
}

internal fun StmtConversionContext.insertQuantifierFunctionCall(
    symbol: FirValueParameterSymbol,
    block: FirBlock,
    buildEmbedding: (VariableEmbedding, List<ExpEmbedding>, List<ExpEmbedding>) -> ExpEmbedding,
): ExpEmbedding {
    val anonVar = freshAnonBuiltinVar(embedType(symbol.resolvedReturnType))
    val methodCtxFactory = MethodContextFactory(
        signature,
        InlineParameterResolver(
            substitutions = mapOf(SubstitutedArgument.ValueParameter(symbol) to anonVar),
            labelName = null,
            // TODO: ideally, there shouldn't be a return target since return is prohibited
            defaultResolvedReturnTarget = defaultResolvedReturnTarget,
        ),
        uniquenessAnalysis = uniquenessAnalysis,
        parent = this,
    )
    return withNoScope {
        withMethodCtx(methodCtxFactory) {
            val (invariants, triggers) = collectInvariantsAndTriggers(block)
            buildEmbedding(anonVar, invariants, triggers)
        }
    }
}


fun StmtConversionContext.convertImpureBody(
    declaration: FirSimpleFunction,
    signature: NamedFunctionSignatureWithContract,
    returnTarget: ReturnTarget,
): ConvertedMethodBody? {
    val firBody = declaration.body ?: return null
    val body = convert(firBody)
    val returnLabel = returnTarget.label ?: throw SnaktInternalException(
        declaration.source, "Return target label not found for method ${declaration.name}"
    )
    val bodyExp = FunctionExp(signature, body, returnLabel)
    return ConvertedMethodBody(bodyExp, returnTarget)
}

fun StmtConversionContext.convertPureBody(declaration: FirSimpleFunction): ExpEmbedding {
    val firBody = declaration.body ?: throw SnaktInternalException(
        declaration.source,
        "Pure functions expect a function body to exist"
    )
    return convert(firBody)
}

fun ProgramConversionContext.linearizeImpureBody(
    source: KtSourceElement?,
    converted: ConvertedMethodBody,
    tracksOwnership: Boolean,
): FunctionBodyEmbedding {
    val seqnBuilder = SeqnBuilder(source)
    val foldState = if (tracksOwnership) FoldState(typeResolver) else null
    val linearizer = Linearizer(SharedLinearizationState(anonVarProducer), seqnBuilder, source, typeResolver, foldState)
    converted.bodyExp.toLinearizable(source).toViperUnusedResult(linearizer)
    // note: we must guarantee somewhere that returned value is Unit
    // as we may not encounter any `return` statement in the body
    converted.returnTarget.variable.withIsUnitInvariantIfUnit(typeResolver)
        .toLinearizable(source).toViperUnusedResult(linearizer)
    return FunctionBodyEmbedding(seqnBuilder.block)
}

fun ProgramConversionContext.linearizePureBody(
    source: KtSourceElement?,
    body: ExpEmbedding,
): Exp {
    val pureFunBodyLinearizer = PureFunBodyLinearizer(
        source,
        SharedLinearizationState(anonVarProducer),
        SsaConverter(source),
        typeResolver
    )
    body.toLinearizable(source).toViperUnusedResult(pureFunBodyLinearizer)
    return pureFunBodyLinearizer.constructExpression()
}

private fun FirStatement.requireInvariant() {
    if (this !is FirExpression || !resolvedType.isBoolean) {
        throw UnsupportedFeatureException(source, "Every statement in invariant block must be a pure boolean invariant.")
    }
}

data class InvariantsAndTriggers(
    val invariants: List<ExpEmbedding>,
    val triggers: List<ExpEmbedding>
)

private fun FirBlock.isEmptyLambdaBody(): Boolean {
    if (statements.isEmpty()) return false
    return (statements.size == 1 && (statements.first() as? FirReturnExpression)?.result?.resolvedType?.isUnit ?: false)
}

fun StmtConversionContext.collectInvariants(block: FirBlock) = buildList {
    if (block.isEmptyLambdaBody()) {
        return@buildList
    }
    block.statements.forEach { stmt ->
        stmt.requireInvariant()
        add(stmt.accept(StmtConversionVisitor, this@collectInvariants))
    }
}

/**
 * Attempts to extract trigger expressions from a triggers() function call.
 * Returns the list of trigger expressions if this is a triggers() call, or null otherwise.
 */
private fun StmtConversionContext.tryExtractTriggers(stmt: FirStatement): List<ExpEmbedding>? {
    if (stmt !is FirFunctionCall) return null

    val symbol = stmt.toResolvedCallableSymbol() as? FirFunctionSymbol<*>
    if (symbol?.isInvariantBuilderFunctionNamed("triggers") != true) return null

    val varargs = stmt.arguments.firstOrNull() as? FirVarargArgumentsExpression
        ?: throw IllegalArgumentException("triggers() function must have a single varargs parameter.")

    // TODO: check whether trigger is valid in Viper.
    return varargs.arguments.map { expr ->
        expr.accept(StmtConversionVisitor, this)
    }
}

fun StmtConversionContext.collectInvariantsAndTriggers(block: FirBlock): InvariantsAndTriggers {
    val invariants = mutableListOf<ExpEmbedding>()
    val triggers = mutableListOf<ExpEmbedding>()

    block.statements.forEach { stmt ->
        val extractedTriggers = tryExtractTriggers(stmt)
        if (extractedTriggers != null) {
            triggers.addAll(extractedTriggers)
            return@forEach
        }

        // Otherwise, treat as invariant
        stmt.requireInvariant()
        invariants.add(stmt.accept(StmtConversionVisitor, this))
    }

    return InvariantsAndTriggers(invariants, triggers)
}

/**
 * Converts [stmt], reporting an unsupported construct in it and standing in a placeholder,
 * so that the rest of the function still converts and reports its own errors.
 */
fun StmtConversionContext.convertReportingUnsupported(stmt: FirStatement): ExpEmbedding =
    try {
        convert(stmt)
    } catch (e: UnsupportedFeatureException) {
        reportUnsupportedFeature(e.source ?: stmt.source, e.message)
        UnsupportedPlaceholder.withPosition(stmt.source)
    }

/**
 * Leaves by an exception, which may be caught by any enclosing catch of this function, whatever its declared type,
 * or leave the function, in which case the postcondition does not apply.
 */
fun StmtConversionContext.exceptionalExit(): ExpEmbedding = Block {
    activeCatchLabels.forEach { add(NonDeterministically(Goto(it.toLink()))) }
    add(Unreachable)
}

/**
 * A loop whose head carries the proven invariants of the variables in scope, then [boundInvariants], then the
 * user's [userInvariants]. The body is converted in the loop's own context; [headLabelName] names the head there.
 */
fun StmtConversionContext.convertLoop(
    loop: FirWhileLoop,
    condition: ExpEmbedding,
    userInvariants: FirBlock?,
    boundInvariants: List<ExpEmbedding> = emptyList(),
    headLabelName: StmtConversionContext.() -> SymbolicName = { continueLabelName() },
    body: StmtConversionContext.() -> ExpEmbedding,
): ExpEmbedding {
    val inScope = retrievePropertiesAndParameters().toList()
    val invariants = buildList {
        inScope.forEach {
            addAll(it.provenInvariants())
        }
        addAll(boundInvariants)
        userInvariants?.let {
            addAll(withScopeImpl(ScopeIndex.NoScope) { collectInvariants(it) })
        }
    }
    val analysis = uniquenessAnalysis
    val headShapes = analysis?.let { ownedShapes(it, it.stateAtLoopHead(loop), inScope) }.orEmpty()
    val exitShapes = analysis?.let { ownedShapes(it, it.stateAfter(loop), inScope) }.orEmpty()
    return withFreshWhile(loop.label) {
        val convertedBody = body()
        While(condition, convertedBody, breakLabelName(), headLabelName(), invariants, headShapes, exitShapes)
    }
}

/**
 * The shapes of the roots among [roots] that [state] has `Unique`, with a hole at each path below them that [state]
 * has `Moved`.
 *
 * A moved path that does not run through tracked `@Unique` properties alone is left out: the fold state tracks no
 * move of it either, so the root's predicate is not opened for it.
 */
fun StmtConversionContext.ownedShapes(
    analysis: FunctionUniquenessAnalysis,
    state: UniquenessState,
    roots: List<VariableEmbedding>,
): List<OwnedShape> = roots.filterIsInstance<FirVariableEmbedding>()
    .filter { analysis.owns(state, listOf(it.symbol)) }
    .map { root ->
        OwnedShape(root, analysis.movedBelow(state, root.symbol).mapNotNull { path ->
            path.map { symbol ->
                val property = symbol as? FirPropertySymbol ?: return@mapNotNull null
                embedProperty(property).ownedStep ?: return@mapNotNull null
            }
        })
    }
