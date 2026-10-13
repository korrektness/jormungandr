/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.formver.locality.plugin.ConstructionPhases

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.references.toResolvedConstructorSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.formver.core.isUnique
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.properties.BackingFieldGetter
import org.jetbrains.kotlin.formver.core.embeddings.properties.FieldEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.PropertyEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.UserFieldEmbedding
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
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
private fun constructionFields(owner: FirRegularClassSymbol): List<FieldEmbedding> {
    val fields = linkedSetOf<FieldEmbedding>()
    fun collect(symbol: FirRegularClassSymbol) {
        symbol.resolvedSuperTypes.mapNotNull { it.toRegularClassSymbol(program.session) }
            .filter { it.classKind == ClassKind.CLASS }.forEach(::collect)
        symbol.fir.declarations.filterIsInstance<FirProperty>().forEach { property ->
            (program.embedProperty(property.symbol).getter as? BackingFieldGetter)?.field?.let { fields += it }
        }
    }
    collect(owner)
    return fields.toList()
}

/** Executes construction on one allocation, in delegation and declaration order. */
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
    val actions = mutableListOf<ExpEmbedding>(AllocateObject(receiver, type, constructionFields(owner)))
    fun initialize(symbol: FirConstructorSymbol, arguments: Map<SubstitutedArgument, ExpEmbedding>) {
        val declaring = symbol.resolvedReturnType.toRegularClassSymbol(program.session)!!
        val phases = ConstructionPhases(declaring)
        val declaringType = program.embedType(symbol.resolvedReturnType).pretype as ClassTypeEmbedding
        val boundary = EstablishConstruction(receiver, declaringType)
        val bodyTarget = if (symbol.isPrimary) target else program.returnTargetProducer.getFresh(buildType { unit() })
        val resolver = InlineParameterResolver(arguments + (SubstitutedArgument.DispatchThis to receiver), null, bodyTarget)
        val context = MethodConverter(program, signature, resolver, program.scopeIndexProducer.getFresh(),
            OwnershipFrame.root(program.uniquenessAnalysisOf(symbol), emptyList()), constructionReceiver = receiver,
            constructionPhases = phases, constructionBoundary = boundary).statementCtxt()
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
                constructionReceiver = receiver, constructionPhases = phases, constructionBoundary = boundary).statementCtxt()
        }
        val delegation = symbol.fir.delegatedConstructor
        val parent = delegation?.calleeReference?.toResolvedConstructorSymbol()
        if (parent != null && parent.origin == FirDeclarationOrigin.Source) {
            val parentArguments = mutableMapOf<SubstitutedArgument, ExpEmbedding>()
            for ((argument, parameter) in delegation.resolvedArgumentMapping.orEmpty()) {
                val value = program.freshAnonVar(program.embedType(parameter.symbol.resolvedReturnType))
                actions += Declare(value, context.convert(argument),
                    targetOwned = context(program.checkerContext) { parameter.symbol.isUnique() })
                parentArguments[SubstitutedArgument.ValueParameter(parameter.symbol)] = value
            }
            for (parameter in parent.valueParameterSymbols) {
                val key = SubstitutedArgument.ValueParameter(parameter)
                if (key in parentArguments) continue
                val defaultContext = MethodConverter(program, signature,
                    InlineParameterResolver(parentArguments, null, target), program.scopeIndexProducer.getFresh(),
                    OwnershipFrame.root(program.uniquenessAnalysisOf(parent), emptyList()), constructionReceiver = receiver).statementCtxt()
                val value = program.freshAnonVar(program.embedType(parameter.resolvedReturnType))
                actions += Declare(value, defaultContext.convert(parameter.fir.defaultValue!!),
                    targetOwned = context(program.checkerContext) { parameter.isUnique() })
                parentArguments[key] = value
            }
            initialize(parent, parentArguments)
        }
        val delegatesWithinClass = parent?.resolvedReturnType?.toRegularClassSymbol(program.session) == declaring
        for (declaration in declaring.fir.declarations.takeUnless { delegatesWithinClass }.orEmpty()) {
            when (declaration) {
                is FirProperty -> declaration.initializer?.let { initializer ->
                    val value = initializerContext(declaration).convert(initializer)
                    val property = program.embedProperty(declaration.symbol)
                    val field = (property.getter as? BackingFieldGetter)?.field
                    if (field != null && field !is UserFieldEmbedding) throw UnsupportedFeatureException(
                        declaration.source, "initialization of a library-encoded field",
                    )
                    actions += constructionStore(receiver, property, value).withPosition(declaration.source)
                }
                is FirAnonymousInitializer -> declaration.body?.let { actions += initializerContext(declaration).convert(it) }
                else -> Unit
            }
        }
        if (!symbol.isPrimary) symbol.fir.body?.let { body ->
            actions += Declare(bodyTarget.variable, null)
            actions += FunctionExp(null, context.convert(body), bodyTarget.label!!)
        }
        actions += boundary.withPosition(symbol.fir.source)
    }
    initialize(constructor, constructor.valueParameterSymbols.zip(signature.params).associate { (parameter, value) ->
        SubstitutedArgument.ValueParameter(parameter) to value
    })
    return ConvertedMethodBody(FunctionExp(signature, actions.toBlock(), target.label!!), target)
}
