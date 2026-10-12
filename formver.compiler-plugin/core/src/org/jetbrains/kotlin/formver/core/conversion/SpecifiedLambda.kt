/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.scopes.getFunctions
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.core.embeddings.callables.*
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.properties.*
import org.jetbrains.kotlin.formver.core.embeddings.types.*
import org.jetbrains.kotlin.formver.core.isUnique
import org.jetbrains.kotlin.formver.core.isFormverFunctionNamed
import org.jetbrains.kotlin.formver.core.names.*
import org.jetbrains.kotlin.formver.locality.plugin.resolveCapturedSymbols
import org.jetbrains.kotlin.formver.locality.plugin.ownsDispatchReceiver
import org.jetbrains.kotlin.formver.uniqueness.plugin.specifiedLambda
import org.jetbrains.kotlin.formver.uniqueness.plugin.uniquenessAnalysisGraph
import org.jetbrains.kotlin.formver.viper.ast.Type
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.core.linearization.pureToViper
import org.jetbrains.kotlin.formver.core.purity.isPure
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.util.OperatorNameConventions

/** One literal SAM conversion and the function it implements. */
data class SpecifiedLambda(
    val expression: FirExpression,
    val owner: FirSimpleFunction,
    val function: FirAnonymousFunction,
    val invoke: FirNamedFunctionSymbol,
    val name: ScopedName,
    val parent: SpecifiedLambda?,
) {
    companion object {
        context(converter: ProgramConversionContext)
        fun collect(declaration: FirSimpleFunction): List<SpecifiedLambda> {
            val result = mutableListOf<SpecifiedLambda>()
            declaration.accept(object : FirVisitorVoid() {
                private var enclosing: SpecifiedLambda? = null

                override fun visitElement(element: FirElement) {
                    if (element is FirSimpleFunction && element !== declaration) return
                    val parent = enclosing
                    val lambda = context(converter.checkerContext) { element.specifiedLambda() }
                    if (lambda != null) {
                        val expression = element as FirExpression
                        val cls = expression.resolvedType.toRegularClassSymbol(converter.session)!!
                        val scope = cls.unsubstitutedScope(
                            converter.session, converter.checkerContext.scopeSession, false, FirResolvePhase.BODY_RESOLVE,
                        )
                        val invoke = scope.getFunctions(OperatorNameConventions.INVOKE).single()
                        val owner = declaration.symbol.embedName(converter)
                        val source = SpecifiedLambda(expression, declaration, lambda, invoke,
                            ScopedName(CallableScope(owner.scope, owner.name), ClassKotlinName(FqName("Lambda${result.size}"))), parent)
                        result += source
                        enclosing = source
                    }
                    element.acceptChildren(this)
                    enclosing = parent
                }
            })
            return result
        }
    }
}

/** A capture's storage belongs to the synthetic object's unique predicate. */
private data class CaptureField(
    override val name: ScopedName,
    override val type: TypeEmbedding,
    override val containingClass: ClassTypeEmbedding,
    override val isUnique: Boolean,
) : FieldEmbedding {
    override val viperType = Type.Ref
    override val accessPolicy = AccessPolicy.BY_RECEIVER_UNIQUENESS
    override val includeInShortDump = true
}

class LiftedLambda(
    val source: SpecifiedLambda,
    val type: ClassTypeEmbedding,
    val interfaceType: TypeEmbedding,
    val captures: Map<FirBasedSymbol<*>, PropertyEmbedding>,
    val constructor: NonInlineFunctionSignature,
    internal val creationPreconditions: MutableList<ExpEmbedding>,
) {
    internal val creationFactContexts = mutableListOf<() -> List<Exp>>()
    internal val baseCreationPreconditions = creationPreconditions.toList()

    fun accesses(receiver: ExpEmbedding): Map<FirBasedSymbol<*>, PropertyAccessEmbedding> =
        captures.mapValues { (_, property) -> ClassPropertyAccess(receiver, property, property.type, receiverOwned = true) }
}

context(converter: ProgramConversionContext)
private fun SpecifiedLambda.captureTypes(): Map<FirBasedSymbol<*>, org.jetbrains.kotlin.fir.types.ConeKotlinType> {
    val graph = function.uniquenessAnalysisGraph
        ?: error("A specified lambda has no control-flow graph")
    val captures = linkedSetOf<FirBasedSymbol<*>>()
    context(converter.checkerContext) {
        graph.resolveCapturedSymbols().filterTo(captures) {
            it is FirValueParameterSymbol || it is FirPropertySymbol && it.isLocal
        }
    }
    val receiverTypes = mutableMapOf<FirBasedSymbol<*>, org.jetbrains.kotlin.fir.types.ConeKotlinType>()
    function.accept(object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            if (element is FirFunctionCall &&
                (element.toResolvedCallableSymbol() as? FirNamedFunctionSymbol)?.isFormverFunctionNamed("exposes") == true) return
            if (element is FirThisReceiverExpression) {
                val symbol = element.calleeReference.symbol
                if (symbol is FirClassSymbol<*> || symbol is FirReceiverParameterSymbol && symbol != function.receiverParameter?.symbol) {
                    captures += symbol!!
                    receiverTypes[symbol] = element.resolvedType
                }
            }
            element.acceptChildren(this)
        }
    })
    return captures.associateWith { symbol ->
        when (symbol) {
            is FirVariableSymbol<*> -> symbol.resolvedReturnType
            is FirClassSymbol<*> -> receiverTypes.getValue(symbol)
            is FirReceiverParameterSymbol -> symbol.resolvedType
            else -> error("Unexpected capture: $symbol")
        }
    }
}

context(converter: ProgramConversionContext)
fun SpecifiedLambda.lift(): LiftedLambda {
    val type = ClassTypeEmbedding(name)
    val resolver = converter.typeResolver
    resolver.register(type, isInterface = false)
    val interfaceType = converter.embedType(expression.resolvedType)
    resolver.addSubtypeRelation(type.name, interfaceType.pretype.name)
    val properties = captureTypes().entries.mapIndexed { index, (symbol, coneType) ->
        val field = CaptureField(ScopedName(name.asScope(), SpecialFieldName("capture$index")), converter.embedType(coneType), type,
            context(converter.checkerContext) { symbol.isUnique() } ||
                symbol == owner.symbol.dispatchReceiverRoot && owner.symbol.ownsDispatchReceiver(converter.session))
        val property = PropertyEmbedding(BackingFieldGetter(field), BackingFieldSetter(field), true, field.isUnique,
            symbol !is FirPropertySymbol || symbol.isVal, field.type)
        resolver.getOrPutProperty(ClassPropertyPair(name, PropertyKotlinName(name.asScope(), Name.identifier("capture$index")))) { property }
        symbol to property
    }.toMap()
    val concreteType = buildType { existing(type) }
    val target = converter.returnTargetProducer.getFresh(concreteType)
    val params = properties.values.mapIndexed { index, property ->
        PlaceholderVariableEmbedding(ScopedName(name.asScope(), PretypeName("captureArg$index")), property.type, property.isUnique)
    }
    val callableType = buildFunctionPretype {
        params.forEach { param -> withParam { isNullable = param.type.isNullable; existing(param.type.pretype) } }
        withReturnType { existing(type) }
        returnsUnique = true
    }
    val signature = SignatureWithTarget<FunctionSignature>(
        FunctionSignatureImpl(callableType, null, null, params, target.variable, false), target,
    ).toNamedSignature(ScopedName(name.asScope(), PretypeName("create")))
        .toNonInlineSignature(null).toCompleteSignature(expression.source) {
            userFunctionPreconditions()
            userFunctionPostcondition(resultFolded = false)
            addPostconditions(listOf(with(resolver) {
                type.uniquePredicateBody(target.variable, NestedPredicates.Held, includeRepresentationFacts = false)
            }))
            addPostconditions(properties.values.zip(params).map { (property, param) ->
                EqCmp(PrimitiveFieldAccess(target.variable, (property.getter as BackingFieldGetter).field), param)
            })
        }.signature
    val preconditions = signature.preconditions.toMutableList()
    return LiftedLambda(this, type, interfaceType, properties,
        signature.copy(preconditions = preconditions, openConstructedClass = type), preconditions)
}

private fun FirStatement.result(): FirStatement = (this as? FirReturnExpression)?.result ?: this

private fun FirStatement.isExposes(): Boolean =
    ((result() as? FirFunctionCall)?.toResolvedCallableSymbol() as? FirNamedFunctionSymbol)?.isFormverFunctionNamed("exposes") == true

/** A concrete observer of the lifted class: its body is the exposed binding value read over the capture fields. */
data class LiftedObserver(val signature: NonInlineFunctionSignature, val body: ExpEmbedding)

/**
 * Converts each `observer() == value` statement of the lambda's `exposes` block into a concrete observer and binds the
 * fun interface's ghost observer to it.
 */
context(converter: ProgramConversionContext)
fun LiftedLambda.bindObservers(): List<LiftedObserver> {
    val exposes = source.function.body?.statements?.firstOrNull()?.takeIf { it.isExposes() }?.result() as? FirFunctionCall
        ?: return emptyList()
    val block = exposes.argumentList.arguments.firstNotNullOf {
        (it.unwrapArgument() as? FirAnonymousFunctionExpression)?.anonymousFunction
    }
    return block.body!!.statements.map { statement ->
        val (target, value) = (statement.result() as FirEqualityOperatorCall).argumentList.arguments
        val observer = (target as FirFunctionCall).toResolvedCallableSymbol()!!.unwrapFakeOverrides() as FirNamedFunctionSymbol
        bindObserver(observer, value)
    }
}

context(converter: ProgramConversionContext)
private fun LiftedLambda.bindObserver(observer: FirNamedFunctionSymbol, value: FirExpression): LiftedObserver {
    val abstract = converter.embedAnyFunction(observer) as NamedCallableEmbedding
    val returns = abstract.callableType.returnType
    val dispatch = PlaceholderVariableEmbedding(DispatchReceiverName, buildType { existing(type) }, isUnique = true)
    val target = ReturnTarget.createForPureFunction(returns)
    val callableType = buildFunctionPretype {
        withDispatchReceiver { existing(type) }
        withReturnType { isNullable = returns.isNullable; existing(returns.pretype) }
    }
    val signature = SignatureWithTarget<FunctionSignature>(
        FunctionSignatureImpl(callableType, dispatch, null, emptyList(), target.variable, true), target,
    ).toNamedSignature(ScopedName(source.name.asScope(), FunctionKotlinName(observer.name, callableType)))
        .toNonInlineSignature(null).toCompleteSignature(value.source) { userFunctionContract() }.signature
    val resolver = RootParameterResolver(converter, signature, emptyList(), null, target)
    val context = MethodConverter(converter, signature, resolver, converter.scopeIndexProducer.getFresh(),
        OwnershipFrame.root(null, emptyList()), captures = accesses(dispatch)).statementCtxt()
    converter.typeResolver.bindObserver(type, abstract.name, signature)
    return LiftedObserver(signature, Return(context.convert(value).withType(returns), target))
}

context(converter: ProgramConversionContext)
fun LiftedLambda.invokeSignature(inherited: CompleteFunctionSignature): SignatureWithTarget<NonInlineFunctionSignature> {
    val concreteType = buildType { existing(type) }
    val receiver = inherited.dispatchReceiver!!
    val dispatch = PlaceholderVariableEmbedding(receiver.name, concreteType, receiver.isUnique, receiver.isBorrowed)
    val target = converter.returnTargetProducer.getFresh(inherited.returns.type)
    val callableType = buildFunctionPretype {
        withDispatchReceiver { existing(type) }
        inherited.extensionReceiver?.let { withExtensionReceiver { isNullable = it.type.isNullable; existing(it.type.pretype) } }
        inherited.params.forEach { withParam { isNullable = it.type.isNullable; existing(it.type.pretype) } }
        withReturnType { isNullable = inherited.returns.type.isNullable; existing(inherited.returns.type.pretype) }
        returnsUnique = inherited.callableType.returnsUnique
    }
    val named = SignatureWithTarget<FunctionSignature>(
        FunctionSignatureImpl(callableType, dispatch, inherited.extensionReceiver, inherited.params, target.variable, false), target,
    ).toNamedSignature(ScopedName(source.name.asScope(), PretypeName("invoke")))
    return named.toNonInlineSignature(null).toCompleteSignature(source.function.source) {
        userFunctionContract()
        val specification = extractFirSpecification(
            source.function.body!!.statements.dropWhile { it.isExposes() }, source.function.returnTypeRef.coneType,
        )
        val (pre, post) = if (specification.precond != null || specification.postcond != null) {
            val resolver = RootParameterResolver(converter, named.signature, source.function.symbol.valueParameterSymbols,
                source.function.symbol.label?.name, target)
            val postResolver = specification.returnVar?.let {
                SubstitutedReturnParameterResolver(resolver, ReturnVarSubstitutor(it, named.signature.returns))
            } ?: resolver
            fun context(resolver: ParameterResolver) = MethodConverter(
                converter, named.signature, resolver, ScopeIndex.NoScope,
                OwnershipFrame.root(converter.uniquenessAnalysisOf(source.function.symbol), emptyList()),
                captures = accesses(dispatch),
            ).statementCtxt()
            (specification.precond?.let { context(resolver).collectInvariants(it) } ?: emptyList()) to
                (specification.postcond?.let { context(postResolver).collectInvariants(it) } ?: emptyList())
        } else converter.embedContract(source.invoke, named.signature, target)
        addPreconditions(pre)
        addPostconditions(post)
    }
}

context(converter: ProgramConversionContext)
fun LiftedLambda.invokeBody(
    signature: NonInlineFunctionSignature,
    target: ReturnTarget,
    analysis: org.jetbrains.kotlin.formver.uniqueness.plugin.FunctionUniquenessAnalysis?,
): ConvertedMethodBody {
    val dispatch = signature.dispatchReceiver!!
    val resolver = RootParameterResolver(converter, signature, source.function.symbol.valueParameterSymbols,
        source.function.symbol.label?.name, target)
    val frame = OwnershipFrame.withStorage(analysis, dispatch) { facts, state ->
        org.jetbrains.kotlin.formver.core.linearization.OwnedShape(dispatch, captures.flatMap { (symbol, property) ->
            val step = property.ownedStep ?: return@flatMap emptyList()
            if (!facts.owns(state, listOf(symbol))) listOf(listOf(step))
            else facts.movedBelow(state, symbol).mapNotNull { path ->
                val steps = path.map { part ->
                    val field = part as? FirPropertySymbol ?: return@mapNotNull null
                    converter.embedProperty(field).ownedStep ?: return@mapNotNull null
                }
                listOf(step) + steps
            }
        })
    }
    val context = MethodConverter(converter, signature, resolver, converter.scopeIndexProducer.getFresh(),
        frame, captures = accesses(dispatch)).statementCtxt()
    val body = source.function.body!!.statements.filterNot { it.isExposes() }.map { context.convert(it) }.toBlock()
    return ConvertedMethodBody(FunctionExp(signature, body, target.label!!), target)
}

private fun StmtConversionContext.establishCaptureFacts(lambda: LiftedLambda, arguments: List<ExpEmbedding>) {
    val immutable = lambda.captures.entries.zip(arguments).zip(lambda.constructor.params).filter { (capture, _) ->
        val symbol = capture.first.key
        symbol !is FirPropertySymbol || symbol.isVal
    }
    val replacements = immutable.flatMap { (capture, param) ->
        val argument = capture.second
        listOf(false, true).map { builtin ->
            argument.pureToViper(toBuiltin = builtin, typeResolver).captureValueKey() to
                param.pureToViper(toBuiltin = builtin, typeResolver)
        }
    }.toMap()
    val parameters = lambda.constructor.params.mapTo(mutableSetOf()) { it.name }
    val known = creationPathFacts
    val receiver = signature.dispatchReceiver?.takeIf { it.isUnique }
    val receiverClass = receiver?.type?.pretype as? ClassTypeEmbedding
    val receiverValue = receiver?.pureToViper(toBuiltin = false, typeResolver)
    val aliases = creationPathAliases.filterValues { it.isPure(admitsInlinedCalls = true) }.map { (name, value) ->
        listOf(name) to value.pureToViper(toBuiltin = false, typeResolver)
    }.toMap()
    val aliasFacts = immutable.groupBy { (capture, _) ->
        capture.second.pureToViper(toBuiltin = false, typeResolver).captureValueKey()
    }.values.flatMap { group ->
        group.drop(1).map { (_, param) -> Exp.EqCmp(
            group.first().second.pureToViper(toBuiltin = false, typeResolver),
            param.pureToViper(toBuiltin = false, typeResolver),
        ) }
    }
    val directFacts = known.filter { it.isPure(admitsInlinedCalls = true) }.flatMap {
        it.pureToViper(toBuiltin = true, typeResolver).conjuncts()
    }
    lambda.creationFactContexts += {
        val inheritedFacts = if (receiverClass != null && receiverValue != null)
            typeResolver.captureFacts(receiverClass, receiverValue) else emptyList()
        (directFacts + inheritedFacts).filterNot { it.hasOwnershipAssertion() }.map { fact ->
            var expanded = fact
            repeat(aliases.size) { expanded = expanded.rewriteCaptureValues(aliases) }
            expanded.rewriteCaptureValues(replacements)
        }.filter { fact ->
            fact.freeVariables().all(parameters::contains) && !(fact is Exp.BoolLit && fact.value) &&
                    fact.isStableCaptureFact(this)
        }.plus(aliasFacts).distinct()
    }
}

context(converter: ProgramConversionContext)
internal fun LiftedLambda.finalizeCreationFacts() {
    val alternatives = creationFactContexts.map { it() }.distinctBy { facts -> facts.mapTo(mutableSetOf()) { it.captureValueKey() } }
    // Each site proves its own conjunction, hence the disjunction; the body assumes only that disjunction.
    val facts = when (alternatives.size) {
        0 -> emptyList()
        1 -> alternatives.single()
        else -> listOf(alternatives.map { alternative ->
            alternative.fold<Exp, Exp>(Exp.BoolLit(true)) { acc, fact -> Exp.And(acc, fact) }
        }.reduce { left, right -> Exp.Or(left, right) })
    }
    val boolean = buildType { boolean() }
    creationPreconditions.clear()
    creationPreconditions += baseCreationPreconditions
    creationPreconditions += facts.map { ExpWrapper(boolean.injection.toRef(it), boolean) }
    val fields = captures.values.zip(constructor.params).associate { (property, param) ->
        listOf(param.name) to Exp.FieldAccess(Exp.LocalVar(DispatchReceiverName, Type.Ref),
            (property.getter as BackingFieldGetter).field.toViper())
    }
    converter.typeResolver.setCaptureFacts(type, facts.map { it.rewriteCaptureValues(fields) })
}

fun StmtConversionContext.constructLambda(lambda: LiftedLambda): ExpEmbedding {
    val arguments = lambda.captures.keys.map { symbol ->
        capturedAccess(symbol)?.getValue(this) ?: when (symbol) {
            is FirValueParameterSymbol -> resolveParameter(symbol)
            is FirPropertySymbol -> resolveLocal(symbol)
            is FirClassSymbol<*> -> resolveDispatchReceiver()!!
            is FirReceiverParameterSymbol -> resolveExtensionReceiver((symbol.containingDeclarationSymbol as FirFunctionSymbol<*>).name.asString())!!
            else -> error("Unexpected capture: $symbol")
        }
    }
    establishCaptureFacts(lambda, arguments)
    return lambda.constructor.insertCall(arguments).withNewTypeInvariants(lambda.interfaceType, typeResolver) {
        proven = true
        access = true
    }
}
