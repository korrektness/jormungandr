/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.formver.core.signatureParameterSymbols
import org.jetbrains.kotlin.formver.core.embeddings.callables.parameterVariables
import org.jetbrains.kotlin.formver.core.parameterArguments
import org.jetbrains.kotlin.formver.locality.plugin.ConstructionPhases

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.FirDelegatedConstructorCall
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.references.toResolvedConstructorSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.formver.core.embeddings.properties.PropertyAccessEmbedding
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.formver.core.isUnique
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.properties.BackingFieldGetter
import org.jetbrains.kotlin.formver.core.embeddings.properties.FieldEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.PropertyEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.UserFieldEmbedding
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.SubTypeInvariantEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.formver.core.linearization.OwnedShape
import org.jetbrains.kotlin.formver.uniqueness.plugin.uniquenessFacts

/** Initializes backing storage or the immutable getter on a fresh receiver. */
context(program: ProgramConversionContext)
fun constructionStore(
    receiver: VariableEmbedding,
    property: PropertyEmbedding,
    value: ExpEmbedding,
): ExpEmbedding {
    val getter = property.getter ?: return value
    val field = (getter as? BackingFieldGetter)?.field
    if (field != null) return FieldModification(
        receiver, field, value.withType(field.type), receiverOwned = true, constructionWrite = true,
    )
    if (!property.hasDefaultBehaviour) return value
    val evaluated = program.freshAnonVar(value.type)
    return Block {
        add(Declare(evaluated, value, targetOwned = property.isUnique))
        // Immutable getter values are fixed once on the fresh receiver.
        add(InhaleDirect(EqCmp(getter.getValueSimple(receiver, program.typeResolver), evaluated)))
        property.ownedStep?.let { add(BindOwnedField(receiver, it, evaluated)) }
    }
}

@OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
context(program: ProgramConversionContext)
private fun constructionFields(owner: FirRegularClassSymbol, withLateinit: Boolean = true): List<FieldEmbedding> {
    val fields = linkedSetOf<FieldEmbedding>()
    fun collect(symbol: FirRegularClassSymbol) {
        symbol.resolvedSuperTypes.mapNotNull { it.toRegularClassSymbol(program.session) }
            .filter { it.classKind == ClassKind.CLASS }.forEach(::collect)
        symbol.instancePropertySymbols.filter { withLateinit || !it.resolvedStatus.isLateInit }.forEach { property ->
            (program.embedProperty(property).getter as? BackingFieldGetter)?.field?.let { fields += it }
        }
    }
    collect(owner)
    return fields.toList()
}

/**
 * The declared types of the fields of [parent]'s class and its ancestors when that class is a library class, except
 * `lateinit` fields, which the library constructor may leave unassigned.
 */
context(program: ProgramConversionContext)
private fun libraryFieldTypes(parent: FirConstructorSymbol?, receiver: VariableEmbedding): List<ExpEmbedding> {
    val library = parent?.resolvedReturnType?.toRegularClassSymbol(program.session)
        ?.takeIf { it.origin != FirDeclarationOrigin.Source } ?: return emptyList()
    return constructionFields(library, withLateinit = false).map { field ->
        InhaleDirect(SubTypeInvariantEmbedding(field.type).fillHole(PrimitiveFieldAccess(receiver, field)))
    }
}

/**
 * Passes the explicit arguments of [delegation] to [parent] in order, adding their evaluation to [actions] and binding
 * each parameter in [arguments]. Gives the parameters whose null check [CallOperand] defers, each with its unchecked
 * value, which [arguments] binds until the check.
 */
private fun StmtConversionContext.passSuperArguments(
    delegation: FirDelegatedConstructorCall,
    parent: FirConstructorSymbol,
    actions: MutableList<ExpEmbedding>,
    arguments: MutableMap<SubstitutedArgument, ExpEmbedding>,
): List<Pair<FirValueParameterSymbol, ExpEmbedding>> =
    delegation.parameterArguments(parent).mapNotNull { (parameter, argument) ->
        val variable = freshAnonVar(embedType(parameter.resolvedReturnType))
        val operand = if (argument is FirVarargArgumentsExpression) {
            CallOperand(argument, convertVarargArray(delegation, argument, variable.type))
        } else operand(argument, parameter.resolvedReturnType)
        if (operand.deferredCheck == null) {
            passSuperArgument(parameter, variable, operand.value, actions, arguments)
            return@mapNotNull null
        }
        val evaluated = declareAnonVar(operand.value.type, operand.value)
        actions += evaluated
        arguments[SubstitutedArgument.ValueParameter(parameter)] = evaluated.variable
        parameter to evaluated.variable
    }

/** Declares [variable] as [value], the argument for [parameter], in [actions] and binds it in [arguments]. */
private fun ProgramConversionContext.passSuperArgument(
    parameter: FirValueParameterSymbol,
    variable: VariableEmbedding,
    value: ExpEmbedding,
    actions: MutableList<ExpEmbedding>,
    arguments: MutableMap<SubstitutedArgument, ExpEmbedding>,
) {
    actions += Declare(variable, value, targetOwned = context(checkerContext) { parameter.isUnique() })
    arguments[SubstitutedArgument.ValueParameter(parameter)] = variable
}

/**
 * Executes construction on one allocation, in delegation and declaration order. A library superclass constructor's
 * body is not converted: its classes' fields other than `lateinit` ones are assumed to hold values of their declared
 * types.
 */
@OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
context(program: ProgramConversionContext)
fun constructionBody(
    constructor: FirConstructorSymbol,
    signature: NonInlineFunctionSignature,
    target: ReturnTarget,
    onUniquenessError: () -> Unit,
): ConvertedMethodBody {
    val owner = constructor.resolvedReturnType.toRegularClassSymbol(program.session)!!
    val type = program.embedType(constructor.resolvedReturnType).pretype as ClassTypeEmbedding
    val receiver = target.variable
    val actions = mutableListOf<ExpEmbedding>()
    val initializers = mutableSetOf<FirDeclaration>()
    fun addInitializer(declaration: FirDeclaration, action: ExpEmbedding) {
        actions += action
        if (program.config.declarationReport?.sourceIndex != null) initializers += declaration
    }
    val captured = mutableMapOf<FirBasedSymbol<*>, PropertyAccessEmbedding>()
    val entered = mutableMapOf<CaptureScope, Map<FirBasedSymbol<*>, PropertyAccessEmbedding>>()

    /**
     * The captures that code of the class [declaring] reads: none when it is neither local nor inner, those of the
     * body scope when that scope contains [declaring], else those of [declaring] itself. A capture that an earlier
     * scope declared keeps its variable.
     */
    fun capturesOf(declaring: FirRegularClassSymbol): Map<FirBasedSymbol<*>, PropertyAccessEmbedding> {
        if (!declaring.classId.isLocal && !declaring.isInner) return emptyMap()
        val enclosing = generateSequence(declaring.classId) { it.outerClassId }.toSet()
        val scope = program.bodyScope?.takeIf { (it.declaration.symbol as? FirClassLikeSymbol<*>)?.classId in enclosing }
            ?: program.captureScope(declaring.fir)
        return entered.getOrPut(scope) {
            actions += scope.entry(program, declared = captured.keys.toSet())
            scope.accesses().mapValues { (symbol, access) -> captured.getOrPut(symbol) { access } }
        }
    }

    fun capturesOf(constructor: FirConstructorSymbol) =
        capturesOf(constructor.resolvedReturnType.toRegularClassSymbol(program.session)!!)
    capturesOf(owner)
    val escape = signature.exceptionEscape(constructor)
    actions += AllocateObject(receiver, type, constructionFields(owner))
    fun initialize(symbol: FirConstructorSymbol, arguments: Map<SubstitutedArgument, ExpEmbedding>, delegated: Boolean = false) {
        val declaring = symbol.resolvedReturnType.toRegularClassSymbol(program.session)!!
        val phases = ConstructionPhases(declaring)
        val boundary = EstablishConstruction(receiver, program.embedType(symbol.resolvedReturnType).pretype as ClassTypeEmbedding)
        val bodyTarget = if (symbol.isPrimary) target else program.returnTargetProducer.getFresh(buildType { unit() })
        val resolver = InlineParameterResolver(arguments + (SubstitutedArgument.DispatchThis to receiver), null, bodyTarget)
        val context = MethodConverter(program, signature, resolver, program.scopeIndexProducer.getFresh(),
            OwnershipFrame.root(program.uniquenessAnalysisOf(symbol), emptyList()), captures = capturesOf(declaring),
            constructionReceiver = receiver, constructionPhases = phases, constructionBoundary = boundary).statementCtxt(escape)
        if (delegated) symbol.fir.constructorSpecification()?.spec?.precond?.let { clauses ->
            actions += context.collectInvariants(clauses).withProofClause(
                org.jetbrains.kotlin.formver.common.KotlinCondition.ProofClause.PRECONDITION, symbol,
            ).map { Assert(it).withPosition(clauses.source) }
        }
        fun initializerContext(declaration: FirDeclaration): StmtConversionContext {
            val analysis = context(program.checkerContext) {
                program.session.uniquenessFacts.initializationAnalysis(declaration, declaring)
            }
            if (analysis == null) onUniquenessError()
            val frame = OwnershipFrame.withStorage(analysis, receiver) { facts, state ->
                OwnedShape(receiver, facts.movedBelow(state, declaring).map { path ->
                    path.map { part -> program.embedProperty(part as org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol).ownedStep
                        ?: error("A moved construction field has no owned storage.") }
                })
            }
            return MethodConverter(program, signature, resolver, program.scopeIndexProducer.getFresh(), frame,
                captures = capturesOf(declaring), constructionReceiver = receiver, constructionPhases = phases,
                constructionBoundary = boundary).statementCtxt(escape)
        }
        val delegation = symbol.fir.delegatedConstructor
        val parent = delegation?.calleeReference?.toResolvedConstructorSymbol()
        if (parent != null && parent.origin == FirDeclarationOrigin.Source) {
            val parentArguments = mutableMapOf<SubstitutedArgument, ExpEmbedding>()
            val deferredChecks = context.passSuperArguments(delegation, parent, actions, parentArguments)
            for (parameter in parent.signatureParameterSymbols) {
                val key = SubstitutedArgument.ValueParameter(parameter)
                if (key in parentArguments) continue
                val defaultContext = MethodConverter(program, signature,
                    InlineParameterResolver(parentArguments, null, target), program.scopeIndexProducer.getFresh(),
                    OwnershipFrame.root(program.uniquenessAnalysisOf(parent), emptyList()),
                    captures = capturesOf(parent), constructionReceiver = receiver).statementCtxt()
                val value = program.freshAnonVar(program.embedType(parameter.resolvedReturnType))
                val converted = parameter.fir.defaultValue?.let { defaultContext.convertExpecting(it, parameter.resolvedReturnType) }
                    ?: context.omittedArgument(delegation, parameter, value.type)
                actions += Declare(value, converted,
                    targetOwned = context(program.checkerContext) { parameter.isUnique() })
                parentArguments[key] = value
            }
            deferredChecks.forEach { (parameter, evaluated) ->
                val variable = program.freshAnonVar(program.embedType(parameter.resolvedReturnType))
                program.passSuperArgument(parameter, variable, context.checkedCast(evaluated, variable.type), actions, parentArguments)
            }
            initialize(parent, parentArguments, delegated = true)
        } else {
            actions += libraryFieldTypes(parent, receiver)
        }
        val delegatesWithinClass = parent?.resolvedReturnType?.toRegularClassSymbol(program.session) == declaring
        for (declaration in declaring.fir.declarations.takeUnless { delegatesWithinClass }.orEmpty()) {
            when (declaration) {
                is FirProperty -> declaration.initializer?.let { initializer ->
                    val value = initializerContext(declaration).convertExpecting(initializer, declaration.returnTypeRef.coneType)
                    val property = program.embedProperty(declaration.symbol)
                    val field = (property.getter as? BackingFieldGetter)?.field
                    if (field != null && field !is UserFieldEmbedding) throw UnsupportedFeatureException(
                        declaration.source, "initialization of a library-encoded field",
                    )
                    addInitializer(declaration, constructionStore(receiver, property, value).withPosition(declaration.source))
                }
                is FirAnonymousInitializer -> declaration.body?.let { addInitializer(declaration, initializerContext(declaration).convert(it)) }
                else -> Unit
            }
        }
        if (!symbol.isPrimary) symbol.fir.body?.let { body ->
            actions += Declare(bodyTarget.variable, null)
            actions += FunctionExp(null, context.convert(body), bodyTarget.label!!)
        }
        actions += boundary.withPosition(symbol.fir.source)
    }
    initialize(constructor, signature.parameterVariables(constructor).associate { (parameter, value) ->
        SubstitutedArgument.ValueParameter(parameter) to value
    })
    return ConvertedMethodBody(FunctionExp(signature, actions.toBlock(), target.label!!), target,
        constructionInitializers = initializers)
}
