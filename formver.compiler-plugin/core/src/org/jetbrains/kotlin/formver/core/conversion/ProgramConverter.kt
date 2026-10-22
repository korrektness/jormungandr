/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.declarations.utils.isAbstract

import org.jetbrains.kotlin.formver.intrinsics.plugin.stringBuilderClassIds
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.formver.common.Failure
import org.jetbrains.kotlin.formver.common.FailureKind
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.common.UnsupportedReason
import org.jetbrains.kotlin.formver.common.PurityCondition
import org.jetbrains.kotlin.formver.common.TerminationCondition
import org.jetbrains.kotlin.formver.common.TerminationRule
import org.jetbrains.kotlin.formver.common.ConversionOwnershipCondition
import org.jetbrains.kotlin.formver.common.purityFailure
import org.jetbrains.kotlin.formver.common.unsupportedOwnershipFailure
import org.jetbrains.kotlin.formver.common.reportFailure
import org.jetbrains.kotlin.formver.common.reportTranslation
import org.jetbrains.kotlin.fir.unwrapUseSiteSubstitutionOverrides
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.directOverriddenSymbolsSafe
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirEnumEntrySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.formver.common.PluginConfiguration
import org.jetbrains.kotlin.formver.common.externalSpecifications
import org.jetbrains.kotlin.formver.common.externalSpecificationSymbol
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.*
import org.jetbrains.kotlin.formver.locality.plugin.neverConvert
import org.jetbrains.kotlin.formver.locality.plugin.neverVerify
import org.jetbrains.kotlin.formver.core.diagnostics.ConversionErrors
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.domains.SeqMultisetDomain
import org.jetbrains.kotlin.formver.core.embeddings.callables.*
import org.jetbrains.kotlin.formver.core.embeddings.expression.AnonymousBuiltinVariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.AnonymousVariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.Declare
import org.jetbrains.kotlin.formver.core.embeddings.expression.InhaleDirect
import org.jetbrains.kotlin.formver.core.embeddings.expression.toConjunction
import org.jetbrains.kotlin.formver.core.embeddings.expression.withProofClause
import org.jetbrains.kotlin.formver.core.embeddings.properties.*
import org.jetbrains.kotlin.formver.core.embeddings.types.*
import org.jetbrains.kotlin.formver.core.names.*
import org.jetbrains.kotlin.formver.core.purity.checkReadOnlyVarReads
import org.jetbrains.kotlin.formver.core.purity.checkSpecificationVarReads
import org.jetbrains.kotlin.formver.core.purity.checkNoSpecificationValues
import org.jetbrains.kotlin.formver.uniqueness.plugin.FunctionUniquenessAnalysis
import org.jetbrains.kotlin.formver.uniqueness.plugin.isGuaranteedDefault
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Program
import org.jetbrains.kotlin.formver.viper.ast.withoutUncalledFunctions
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue


/**
 * Tracks the top-level information about the program.
 * Conversions for global entities like types should generally be
 * performed via this context to ensure they can be deduplicated.
 * We need the FirSession to get access to the TypeContext.
 */
class ProgramConverter(
    override val session: FirSession,
    override val config: PluginConfiguration,
    override val checkerContext: CheckerContext,
    private val reporter: DiagnosticReporter,
) : ProgramConversionContext {

    /**
     * Whether any primary diagnostic has been reported during conversion.
     *
     * Excludes the derived [ConversionErrors.VERIFICATION_SKIPPED] summary, which is itself fired
     * in reaction to primary diagnostics.
     */
    var hadConversionError: Boolean = false
        private set

    override var provenConstruction: FirRegularClassSymbol? = null

    private fun emit(failure: Failure) {
        context(checkerContext) { reporter.reportFailure(failure) }
        hadConversionError = true
    }

    override fun reportPurityViolation(source: KtSourceElement?, condition: PurityCondition) =
        emit(purityFailure(source, condition))

    override fun reportUnsupportedOwnership(source: KtSourceElement?, condition: ConversionOwnershipCondition) =
        emit(unsupportedOwnershipFailure(source, condition))

    override fun reportUntrackedWrite(source: KtSourceElement?) = context(checkerContext) {
        reporter.reportTranslation(source, ConversionErrors.UNTRACKED_WRITE)
    }

    override fun reportUncheckedLibraryCall(source: KtSourceElement?, member: String) = context(checkerContext) {
        reporter.reportFailure(Failure(FailureKind.UNCHECKED_LIBRARY_CALL, source, KotlinCondition.UncheckedLibraryCall(member)))
    }

    override fun reportUnsupportedFeature(source: KtSourceElement?, msg: String, reason: UnsupportedReason) =
        emit(Failure(ConversionErrors.UNSUPPORTED_FEATURE, source, KotlinCondition.Translation(reason.name, msg)))

    val skips = VerificationSkipReporter(session, checkerContext, reporter)

    private val specialFunctions: Map<SymbolicName, SpecialKotlinFunction> = buildMap {
        putAll(SpecialKotlinFunctions.byName)
        putAll(PartiallySpecialKotlinFunctions.generateAllByName())
    }

    private val fullSignatures: MutableMap<SymbolicName, CompleteFunctionSignature> = mutableMapOf()

    private val callable: MutableMap<SymbolicName, SignatureWithTarget<NamedCallableEmbedding>> = mutableMapOf()

    private data class RegisteredFunction(
        val declaration: FirFunction,
        val signature: CompleteFunctionSignature,
        val returnTarget: ReturnTarget,
        val hasUniquenessErrors: Boolean,
    )

    private val registered: MutableList<RegisteredFunction> = mutableListOf()

    private val refinements: MutableList<Refinement> = mutableListOf()

    /** The return target of each header [embedAccessorFunction] creates, by header name. */
    private val accessorReturnTargets: MutableMap<SymbolicName, ReturnTarget> = mutableMapOf()


    /**
     * Whether a registered function has uniqueness or locality errors. Such a function is converted, but not verified.
     */
    val hadUniquenessError: Boolean
        get() = registered.any { it.hasUniquenessErrors }

    /**
     * Whether linearization found a unique predicate it needs but does not hold. Such a function is linearized
     * without its body, and not verified.
     */
    var hadOwnershipError: Boolean = false
        private set

    /** Whether a registered function is recursive without a termination measure. It is not verified. */
    private var hadMissingMeasure: Boolean = false

    val skipsVerification: Boolean
        get() = hadUniquenessError || hadOwnershipError || hadMissingMeasure

    override val typeResolver: TypeResolver = TypeResolver()

    override val trust = TrustedAssumptionCollector(session, typeResolver) { it == registeredSymbol }

    private val terminationMeasures = TerminationMeasures(typeResolver)

    val debugExpEmbeddings: Map<SymbolicName, ExpEmbedding>
        get() = buildMap {
            convertedBodyResolver.forEachImpure { name, body -> put(name, body.bodyExp) }
        }


    override val whileIndexProducer = indexProducer()
    override val catchLabelNameProducer = simpleFreshEntityProducer(::CatchLabelName)
    override val tryExitLabelNameProducer = simpleFreshEntityProducer(::TryExitLabelName)
    override val scopeIndexProducer = scopeIndexProducer()

    // The type annotation is necessary for the code to compile.
    override val anonVarProducer = FreshEntityProducer(::AnonymousVariableEmbedding)
    override val anonBuiltinVarProducer = FreshEntityProducer(::AnonymousBuiltinVariableEmbedding)
    override val returnTargetProducer = FreshEntityProducer(ReturnTarget::createForDepth)
    override val nameResolver = ShortNameResolver()
    override val convertedBodyResolver = ConvertedBodyResolver()
    override val linearizedBodyResolver = LinearizedBodyResolver()


    private val usesIntArray: Boolean
        get() = typeResolver.lookupClassTypeEmbedding(IntArrayEmbedding.classType.name) != null

    private val usesGenericArray: Boolean
        get() = typeResolver.lookupClassTypeEmbedding(GenericArrayEmbedding.classType.name) != null

    private val usesStringBuilder: Boolean
        get() = typeResolver.lookupClassTypeEmbedding(StringBuilderEmbedding.classType.name) != null

    // Arguments are evaluated in the order written, and the domains go last: converting the other members
    // may find that a specification states the relation of an `==`, which needs its domain.
    fun buildProgram(): Program = Program(
        functions = TerminationMeasures.withTerminationSpecifications(
            SpecialFunctions.all + SpecialFunctions.multiset.takeIf { typeResolver.usesMultiset }.orEmpty() +
                    SpecialFunctions.iterationSequence.takeIf { typeResolver.usesIterationSequence }.orEmpty() +
                linearizedBodyResolver.functions + typeResolver.observerViewFunctions() + typeResolver.stringLiteralFunctions +
                typeResolver.unmatchedWhenFunctions +
                listOfNotNull(IntArrayEmbedding.arraySizeFunction.takeIf { usesIntArray },
                    GenericArrayEmbedding.arraySizeFunction.takeIf { usesGenericArray })
        ),
        methods = SpecialMethods.all + linearizedBodyResolver.methods + typeResolver.familyTransferMethods() + lawEncoding.methods +
            vacuityProbes.methods(linearizedBodyResolver.methods, linearizedBodyResolver.functions),
        predicates = typeResolver.predicates(),
        // Public fields with the same name are represented differently at `FieldEmbedding` level
        // but map to the same Viper field, so we deduplicate before emitting.
        fields = typeResolver.backingFields().distinctBy { it.name }.map { it.toViper() } +
                listOfNotNull(contentsField.takeIf { usesIntArray || usesStringBuilder },
                    GenericArrayEmbedding.contentsField.takeIf { usesGenericArray }),
        adts = emptyList(),
        domains = listOf(RuntimeTypeDomain(typeResolver)) +
                listOfNotNull(SeqMultisetDomain.takeIf { usesIntArray && typeResolver.usesMultiset }) +
                listOfNotNull(typeResolver.equalsRelationDomain()) +
                TerminationMeasures.domains(linearizedBodyResolver.functions),
    ).withoutUncalledFunctions(SpecialFunctions.allNames)


    // region Callable Conversion

    /** The declaration passed to [register]. */
    private var registeredSymbol: FirFunctionSymbol<*>? = null

    private val lambdaRegistry = LambdaRegistry(this, fullSignatures)

    val specifiedLambdas: List<SpecifiedLambda>
        get() = lambdaRegistry.sources

    override fun liftedLambda(expression: FirExpression): LiftedLambda? = lambdaRegistry[expression]
    override fun lookupFunctionSignature(name: SymbolicName): CompleteFunctionSignature? = fullSignatures[name]

    fun registerLambda(lambda: SpecifiedLambda, lambdas: List<SpecifiedLambda>) {
        registeredSymbol = lambda.function.symbol
        lambdaRegistry.prepare(lambdas)
        // Each lifted body has its own verification unit; lower its creation path in that unit's symbol context.
        val owner = lambda.owner as? FirSimpleFunction
        if (owner != null) {
            val ownerSignature = embedCompleteSignature(owner.symbol)
            val ownerContext = createBodyConversionContext(owner.symbol, ownerSignature, uniquenessOutcomeOf(owner).analysis)
            if (ownerSignature.signature.isPure) ownerContext.convertPureBody(owner)
            else ownerContext.convertImpureBody(owner, ownerSignature.signature, ownerSignature.returnTarget)
        }
        for (parent in generateSequence(lambda.parent) { it.parent }.toList().asReversed()) {
            val lifted = lambdaRegistry[parent.expression]!!
            val (signature, target) = with(this) { lifted.invokeSignature(embedCompleteSignature(parent.invoke).signature) }
            with(this) { lifted.invokeBody(signature, target, uniquenessAnalysisOf(parent.function.symbol)) }
        }
        val result = lambdaRegistry.register(lambda, lambdas) { embedCompleteSignature(lambda.invoke).signature }
        lawEncoding.registerClass(lambdaRegistry[lambda.expression]!!.type,
            lambda.expression.resolvedType.toRegularClassSymbol(session)!!, lambda.function.source)
        registered += RegisteredFunction(lambda.function, result.signature, result.target, uniquenessOutcomeOf(lambda.function).hasErrors)
        result.refinement?.let { refinements += it }
    }

    /** Embeds the signature and, unless `@NeverVerify`, the body of [declaration]. */
    fun register(declaration: FirFunction) {
        registeredSymbol = declaration.symbol
        context(checkerContext) { declaration.rejectLocalCaptures() }
        val signature = embedCompleteSignature(declaration.symbol)
        if (!declaration.symbol.neverVerify(session) &&
            (!signature.signature.isPure || convertedBodyResolver.lookupPure(signature.signature.name) == null)) {
            embedFunctionBody(declaration.symbol, signature)
        }
        val hasUniquenessErrors = uniquenessOutcomeOf(declaration).hasErrors
        registered += RegisteredFunction(declaration, signature.signature, signature.returnTarget, hasUniquenessErrors)
        if (declaration is FirSimpleFunction) registerRefinements(declaration, signature.signature)
    }

    /**
     * Registers the body of the accessor [declaration] against the method header its callers call. The header types
     * the receivers and parameters as `Any` or `Any?`. The body reads them with their Kotlin types, which it assumes on
     * entry: the JVM enforces them on every call.
     */
    fun registerAccessor(declaration: FirPropertyAccessor) {
        registeredSymbol = declaration.symbol
        val property = declaration.propertySymbol
        if (property.receiverParameterSymbol == null && property.dispatchReceiverType == null) {
            throw UnsupportedFeatureException(declaration.source, "a custom accessor of a top-level property")
        }
        val embedding = embedProperty(property)
        val header = if (declaration.isGetter) (embedding.getter as? CustomGetter)?.getterMethod
            else (embedding.setter as? CustomSetter)?.setterMethod
        val returnTarget = header?.let { accessorReturnTargets[it.name] } ?: throw UnsupportedFeatureException(
            declaration.source, "a custom accessor that its callers do not call"
        )
        if (declaration.symbol.neverVerify(session)) return
        val typed = with(this) { declaration.symbol.toFunctionSignature().toNamedSignature(declaration.symbol).signature }
        val entry = buildList {
            // The header and the body name the receivers alike, so only the parameters need copying.
            listOfNotNull(typed.dispatchReceiver, typed.extensionReceiver).forEach {
                add(InhaleDirect(it.provenInvariants().toConjunction()))
            }
            require(typed.params.size == header.params.size)
            typed.params.zip(header.params).forEach { (parameter, argument) ->
                add(Declare(parameter, argument))
                add(InhaleDirect(parameter.provenInvariants().toConjunction()))
            }
        }
        val context = createBodyConversionContext(
            declaration.symbol, SignatureWithTarget(typed, returnTarget), uniquenessOutcomeOf(declaration).analysis,
            labelName = property.name.asString(),
        )
        val body = context.convertImpureBody(declaration, header, returnTarget, entry)
        checkVarReads(declaration) { body?.bodyExp?.checkSpecificationVarReads(it, this) }
        declaration.source?.let { source -> body?.bodyExp?.checkNoSpecificationValues(source, this) }
        body?.let { convertedBodyResolver.storeImpure(header.name, it) }
        registered += RegisteredFunction(declaration, header, returnTarget, uniquenessOutcomeOf(declaration).hasErrors)
    }

    internal val vacuityProbes = SpecVacuityProbes()

    /** Registers the proof of a source constructor against its exported contract. */
    @OptIn(SymbolInternals::class)
    fun registerConstructor(symbol: FirConstructorSymbol) {
        registeredSymbol = symbol
        val signature = embedCompleteSignature(symbol)
        val contract = signature.signature as NonInlineFunctionSignature
        val owner = symbol.resolvedReturnType.toRegularClassSymbol(session)!!
        lambdaRegistry.prepare(with(this) { SpecifiedLambda.collect(owner.fir, owner.classId.embedName()) })
        var hasInitializationErrors = false
        val body = with(this) { constructionBody(symbol, contract, signature.returnTarget) { hasInitializationErrors = true } }
        convertedBodyResolver.storeImpure(contract.name, body)
        registered += RegisteredFunction(symbol.fir, contract, signature.returnTarget,
            hasInitializationErrors || uniquenessOutcomeOf(symbol.fir).hasErrors)
    }

    /**
     * Records a refinement check of [declaration] against each declaration it directly overrides that has a
     * specification, its own or inherited.
     */
    private fun registerRefinements(declaration: FirSimpleFunction, signature: CompleteFunctionSignature) {
        val overriddenWithSpec = context(checkerContext) { declaration.symbol.directOverriddenSymbolsSafe() }
            .filterIsInstance<FirNamedFunctionSymbol>()
            .filter { context(checkerContext) { it.specificationOwner() } != null }
        if (overriddenWithSpec.isEmpty()) return
        val override = signature as? NonInlineFunctionSignature ?: throw UnsupportedFeatureException(
            declaration.source, "an inline override of a function with a specification"
        )
        for (overridden in overriddenWithSpec) {
            val overriddenSignature = embedCompleteSignature(overridden).signature
            refinements += Refinement(override, overriddenSignature, overridden.refinementSpelling, declaration.source)
        }
    }



    /** Checks inherited bodies and their contracts in the receiving class's representation. */
    @OptIn(SymbolInternals::class)
    fun registerInheritedImplementation(
        owner: FirRegularClassSymbol,
        implementation: FirNamedFunctionSymbol,
        contracts: List<FirNamedFunctionSymbol>,
    ) {
        registeredSymbol = implementation
        val ownContract = context(checkerContext) { implementation.specificationOwner() }
        val bodies = mutableMapOf<FirNamedFunctionSymbol, SignatureWithTarget<NonInlineFunctionSignature>>()
        for (contract in contracts) {
            val bodySignature = bodies.getOrPut(ownContract ?: contract) {
                specializeInheritedMember(owner, implementation, "inherited_body", ownContract ?: contract).also { specialized ->
                    fullSignatures[specialized.signature.name] = specialized.signature
                    embedFunctionBody(implementation, specialized)
                    registered += RegisteredFunction(implementation.fir, specialized.signature, specialized.returnTarget,
                        uniquenessOutcomeOf(implementation.fir).hasErrors)
                }
            }
            val base = specializeInheritedMember(owner, contract, "inherited_contract")
            fullSignatures[base.signature.name] = base.signature
            refinements += Refinement(bodySignature.signature, base.signature, contract.refinementSpelling, owner.source)
        }
    }

    /**
     * Walk converted bodies to surface validity / purity errors via the diagnostic reporter. If any
     * conversion error has been reported by the time this returns, emit a [ConversionErrors.VERIFICATION_SKIPPED]
     * summary on every registered declaration so the user sees per-function attribution for the bail-out.
     * Otherwise, emit it on every registered declaration with uniqueness or locality errors.
     * A `@NeverVerify` declaration never receives it.
     * Callers should inspect [hadConversionError] afterwards and skip [linearizeAll] when set, and skip verification
     * when [skipsVerification] is set after [linearizeAll].
     */
    fun validateAll() {
        observerBindingRegistry.registerBindings(this) { fullSignatures.getValue(it) as NonInlineFunctionSignature }
        lawEncoding.prepare()
        lambdaRegistry.finalizeCreationFacts()
        bodyProcessor.validate()
        for (entry in registered) {
            if (entry.declaration.symbol.neverVerify(session)) continue
            val reason = when {
                hadConversionError -> KotlinCondition.SkipReason.CONVERSION_ERRORS
                entry.hasUniquenessErrors -> KotlinCondition.SkipReason.UNIQUENESS_OR_LOCALITY_ERRORS
                else -> continue
            }
            skips.report(
                entry.declaration.source,
                reason, entry.declaration.symbol,
            )
        }
    }


    private val bodyProcessor by lazy {
        ProgramBodyProcessor(this, uniquenessOutcomes, fullSignatures, refinements, terminationMeasures.measures,
            onOwnershipError = { error, signature ->
                hadOwnershipError = true
                context(checkerContext) {
                    reporter.reportFailure(unsupportedOwnershipFailure(error.source ?: signature.declarationSource, error.condition))
                }
                skips.report(signature.declarationSource,
                    KotlinCondition.SkipReason.UNSUPPORTED_OWNERSHIP, signature.symbol)
            },
            onMissingMeasure = { signature, cycle ->
                val entry = registered.firstOrNull { it.declaration.symbol == signature.symbol } ?: return@ProgramBodyProcessor
                hadMissingMeasure = true
                val names = cycle.map { it.labelName ?: "<anonymous>" }
                context(checkerContext) {
                    reporter.reportFailure(Failure(FailureKind.MISSING_TERMINATION_MEASURE, signature.declarationSource,
                        TerminationCondition(TerminationRule.MISSING_MEASURE, names)))
                }
                if (!entry.declaration.symbol.neverVerify(session)) skips.report(signature.declarationSource,
                    KotlinCondition.SkipReason.MISSING_TERMINATION_MEASURE, signature.symbol)
            },
        )
    }

    /**
     * Build the finalized Viper `Method` / `Function` for every embedded callable, storing each in
     * [linearizedBodyResolver]. Should only be invoked when [hadConversionError] is false.
     *
     * Iterates the embedding maps directly (rather than the converted-body map) so that callables
     * without a body still get a Viper header.
     */
    fun linearizeAll() = bodyProcessor.linearize()

    // endregion

    // region Conversion Context

    private val uniquenessOutcomes = UniquenessOutcomes(session, checkerContext)

    private fun uniquenessOutcomeOf(declaration: FirFunction): UniquenessOutcome = uniquenessOutcomes[declaration]

    @OptIn(SymbolInternals::class)
    override fun uniquenessAnalysisOf(symbol: FirFunctionSymbol<*>): FunctionUniquenessAnalysis? =
        uniquenessOutcomeOf(symbol.fir).analysis

    /**
     * Runs [check] on the source of [declaration] when it is the registered declaration and [analysis], the
     * uniqueness analysis the checked code was converted with, exists. The callees it embeds are checked when they
     * are registered themselves. Without an analysis every receiver counts as not owned, and the function is not
     * verified anyway.
     */
    private fun checkVarReads(
        declaration: FirFunction,
        analysis: FunctionUniquenessAnalysis? = uniquenessOutcomeOf(declaration).analysis,
        check: (KtSourceElement) -> Unit,
    ) {
        if (declaration.symbol != registeredSymbol) return
        val source = declaration.source ?: return
        if (analysis != null) check(source)
    }

    private fun createBodyConversionContext(
        symbol: FirFunctionSymbol<*>,
        signature: SignatureWithTarget<NamedFunctionSignature>,
        uniquenessAnalysis: FunctionUniquenessAnalysis?,
        labelName: String? = signature.signature.labelName,
    ): StmtConversionContext {
        val paramResolver = RootParameterResolver(
            this@ProgramConverter,
            signature.signature,
            symbol.valueParameterSymbols,
            labelName,
            signature.returnTarget
        )
        val stmtCtx = MethodConverter(
            this@ProgramConverter,
            signature.signature,
            paramResolver,
            scopeIndexProducer.getFresh(),
            OwnershipFrame.root(uniquenessAnalysis, symbol.receiverRoots(signature.signature)),
        ).statementCtxt()
        return stmtCtx
    }

    // endregion


    // region Functions

    /**
     * Embeds the body of a function. This must be called at most once per function.
     */
    @OptIn(SymbolInternals::class)
    private fun embedFunctionBody(
        symbol: FirFunctionSymbol<*>, signature: SignatureWithTarget<CompleteFunctionSignature>
    ) {
        val declaration = symbol.fir
        if (session.externalSpecifications?.isModel(symbol) == true) return

        val abstractObserverOverride = context(checkerContext) { symbol.isAbstract && symbol.isObserverOrOverride() }
        val observerDeclaration = context(checkerContext) { symbol.isObserver() }
        if (signature.signature.isPure && (observerDeclaration || abstractObserverOverride)) return
        if (declaration is FirSimpleFunction) {
            lambdaRegistry.prepare(with(this) { SpecifiedLambda.collect(declaration) })
        }

        val context = createBodyConversionContext(symbol, signature, uniquenessOutcomeOf(declaration).analysis)
        if (signature.signature.isPure) {
            val body = context.convertPureBody(declaration)
            checkVarReads(declaration) { body.checkReadOnlyVarReads(it, this) }
            convertedBodyResolver.storePure(signature.signature.name, body)
        } else {
            val body = context.convertImpureBody(declaration as FirSimpleFunction, signature.signature, signature.returnTarget)
            checkVarReads(declaration) { body?.bodyExp?.checkSpecificationVarReads(it, this) }
            declaration.source?.let { source -> body?.bodyExp?.checkNoSpecificationValues(source, this) }
            body?.let { convertedBodyResolver.storeImpure(signature.signature.name, it) }
        }
    }


    /**
     * Embeds the full function signature (with pre+post conditions).
     */
    private fun embedCompleteSignature(symbol: FirFunctionSymbol<*>): SignatureWithTarget<CompleteFunctionSignature> {
        if (symbol.isOpenOrOverride && symbol.isPure(session) &&
            !context(checkerContext) { symbol.isObserverOrOverride() }) {
            // A call to such a function is reported at the call.
            val source = symbol.source.takeIf { symbol == registeredSymbol }
            throw UnsupportedFeatureException(source, "a `@Pure` function that is open or overrides another")
        }
        val callable = with(this) {
            val namedSignature = symbol.toFunctionSignature().toNamedSignature(symbol)
            if (symbol.shouldBeInlined && session.externalSpecifications?.isModel(symbol) != true) {
                namedSignature.toInlineSignature(symbol).also {
                    callable.putIfAbsent(it.signature.name, it)
                }
            } else {
                trust.recordLibraryContract(symbol, namedSignature.signature)
                namedSignature.toNonInlineSignature(symbol).also {
                    callable.putIfAbsent(it.signature.name, it)
                }.toCompleteSignature(symbol)
            }
        }

        fullSignatures.putIfAbsent(callable.signature.name, callable.signature)

        return callable
    }

    /**
     * Returns the callable embedding if a matching special function exists.
     */
    fun embedSpecialFunction(symbol: FirFunctionSymbol<*>): CallableEmbedding? {
        val name = symbol.embedName(this)
        return specialFunctions[name]?.also { existing ->
            if (existing !is PartiallySpecialKotlinFunction) return@also
            if (existing.baseEmbedding != null) return@also
            val signature = embedCompleteSignature(symbol)
            embedFunctionBody(symbol, signature)
            existing.initBaseEmbedding(signature.signature)
        }
    }

    /**
     * This function is the public interface to embed a function symbol.
     * If during the conversion of a function body a function call is made, then [embedAnyFunction] must be used to embed/lookup the function.
     *
     * It will return the callable embedding, if the function was seen the first time, it will embed the full function signature and body (if needed).
     */
    override fun embedAnyFunction(symbol: FirFunctionSymbol<*>): CallableEmbedding {
        val model = symbol.externalSpecificationSymbol(session)
        if (model != symbol) return embedAnyFunction(model).also { trust.recordBinding(symbol) }
        context(checkerContext) { symbol.observerDeclaration() }?.let { return embedAnyFunction(it) }
        trust.recordOptOut(symbol)
        // NOTE: The order of the embedSpecialFunction and the callable lookup does matter.

        // check if it must be handled specially
        embedSpecialFunction(symbol)?.let { return it }
        rejectUnsupportedStdlibMember(symbol, session)

        // check if it already was embedded
        callable[symbol.embedName(this)]?.let { return it.signature }

        // first embed the complete signature
        val signature = embedCompleteSignature(symbol)

        // If the function is pure, we add the body.
        if (signature.signature.isPure && !symbol.neverConvert(session)) {
            embedFunctionBody(symbol, signature)
        }

        return signature.signature
    }

    // endregion


    // region contracts

    private fun embedFormverContract(
        symbol: FirFunctionSymbol<*>, signature: NamedFunctionSignature, returnTarget: ReturnTarget
    ): Pair<List<ExpEmbedding>, List<ExpEmbedding>> {
        @OptIn(SymbolInternals::class) val declaration = symbol.unwrapUseSiteSubstitutionOverrides().fir

        // Constructor requirements are parameter-only; property models carry their getter contracts.
        val specification = when {
            declaration is FirConstructor -> declaration.constructorSpecification()
            declaration is FirSimpleFunction -> context(checkerContext) { declaration.symbol.specificationOwner() }
                ?.let { @OptIn(SymbolInternals::class) it.fir.userSpecification(session) }
            declaration is FirPropertyAccessor && declaration.isGetter -> declaration.propertySymbol.userSpecification(session)
            else -> null
        }
        val (holder, firSpec) = specification ?: return Pair(emptyList(), emptyList())
        val holderAnalysis = uniquenessOutcomeOf(holder).analysis

        // The holder's parameters resolve to this function's parameters by position.
        val (preconditionContext, postconditionContext) = createContractConversionContext(
            holder.symbol, signature, firSpec, returnTarget, holderAnalysis
        )

        val (measureCalls, preconditionStatements) = firSpec.precond?.statements.orEmpty().partition { it.decreasesCall() != null }
        terminationMeasures.embed(signature, measureCalls.mapNotNull { it.decreasesCall() }, preconditionContext) { source, rule ->
            if (symbol == registeredSymbol) emit(Failure(FailureKind.INVALID_TERMINATION_MEASURE, source, TerminationCondition(rule)))
        }
        val preconditions = firSpec.precond?.let { preconditionContext.collectInvariants(it, preconditionStatements) } ?: emptyList()
        val postconditions = firSpec.postcond?.let { postconditionContext.collectInvariants(it) } ?: emptyList()

        checkVarReads(declaration, holderAnalysis) { source ->
            val consumed = if (signature.isPure) emptySet() else
                signature.formalArgs.filter { it.isUnique && !it.isBorrowed }.map { it.name }.toSet()
            preconditions.forEach { it.checkReadOnlyVarReads(source, this) }
            postconditions.forEach { it.checkReadOnlyVarReads(source, this, consumed) }
        }

        return Pair(preconditions, postconditions)
    }

    private fun embedKotlinContract(
        symbol: FirFunctionSymbol<*>, signature: NamedFunctionSignature
    ): List<ExpEmbedding> {
        val contractVisitor = ContractDescriptionConversionVisitor(this@ProgramConverter, signature, symbol)
        return contractVisitor.getPostconditions()
    }

    override fun embedContract(
        symbol: FirFunctionSymbol<*>, signature: NamedFunctionSignature, returnTarget: ReturnTarget
    ): Pair<List<ExpEmbedding>, List<ExpEmbedding>> {
        val kotlinContractPostcondition = embedKotlinContract(symbol, signature)
        val userContract = embedFormverContract(symbol, signature, returnTarget)
        return Pair(userContract.first.withProofClause(KotlinCondition.ProofClause.PRECONDITION, symbol),
            kotlinContractPostcondition + userContract.second.withProofClause(KotlinCondition.ProofClause.POSTCONDITION, symbol))
    }

    // endregion

    // region Properties

    /**
     * This function embeds properties. Depending on the behavior of the property, it can be handled differently.
     *
     *
     * * Closed `val` (No Getter): Maps to a pure function. This can be done because the value cannot be changed.
     * * `@Pure val`: Maps to a pure function with the getter's body when present.
     * * Closed `val` (With Getter): Maps to an impure function for an unannotated custom getter.
     * * Closed `var`: Maps to a backing field. This value needs permissions and can be changed.
     * * Open `val` / `var`: Maps to an impure method. Abstracted because subclasses can override the behavior, introduce side effects, or turn a `val` into a `var`.
     * * Extension properties are handled like open properties.
     */
    override fun embedProperty(symbol: FirPropertySymbol): PropertyEmbedding {
        val model = symbol.externalSpecificationSymbol(session)
        if (model != symbol) return embedProperty(model).also { trust.recordBinding(symbol) }
        trust.recordAccessorOptOuts(symbol)
        if (symbol.receiverParameterSymbol != null) {
            rejectUnsupportedStdlibMember(symbol, session)
            return embedExtensionProperty(symbol)
        } else {
            val name = symbol.embedMemberPropertyName(this)
            return typeResolver.getOrPutProperty(name) {
                // Check if the symbol should receive a special treatment
                with(typeResolver) {
                    with(session) {
                        SpecialProperties.lookup(symbol)?.let { return@getOrPutProperty it }
                    }
                }

                rejectUnsupportedStdlibMember(symbol, session)
                val classSymbol = symbol.dispatchReceiverType?.toClassSymbol(session)

                val regularClass = classSymbol as? FirRegularClassSymbol ?: throw SnaktInternalException(
                    symbol.source, "Properties dispatch receiver is not a regular class"
                )

                val isDefaultProperty = isGuaranteedDefaultProperty(symbol)
                val isImmutable = symbol.isVal
                val isManual = symbol.isManual(session)
                val isUnique = context(checkerContext) { symbol.isUnique() }

                val returnType = embedType(symbol.resolvedReturnType)

                val (getter, setter) = when {
                    symbol.isMapEntryValueObserver(session) -> Pair(
                        CustomGetter(embedAccessorFunction(symbol, propertyGetterType(buildType { nullableAny() }, returnType), true)), null
                    )
                    symbol.isPure(session) && !isDefaultProperty -> {
                        val accessor = symbol.getterSymbol ?: error("Pure property has no getter")
                        val callable = embedAnyFunction(accessor) as NamedCallableEmbedding
                        Pair(CustomGetter(fullSignatures.getValue(callable.name) as NonInlineFunctionSignature), null)
                    }

                    (isDefaultProperty && !isImmutable) || isManual -> {
                        // use a backing field
                        val field = embedBackingField(symbol, regularClass)
                        Pair(BackingFieldGetter(field), BackingFieldSetter(field))
                    }

                    isDefaultProperty -> {
                        // use pure function
                        val functionType = propertyGetterType(embedType(symbol.dispatchReceiverType!!), returnType)
                        val getter = embedAccessorFunction(symbol, functionType, defaultBehaviour = true)
                        Pair(
                            if (isUnique) UniqueValGetter(UniqueValStep(symbol, returnType, embedClass(regularClass), getter))
                            else CustomGetter(getter),
                            null,
                        )
                    }

                    else -> {
                        // The property could be overridden by anything, or has a custom getter/setter.
                        // use impure function
                        Pair(
                            CustomGetter(embedAccessorFunction(symbol, memberGetterType(), defaultBehaviour = false)),
                            symbol.isVar.ifTrue {
                                CustomSetter(embedAccessorFunction(symbol, memberSetterType(), defaultBehaviour = false))
                            })
                    }
                }

                return@getOrPutProperty PropertyEmbedding(
                    getter, setter, isDefaultProperty || isManual, isUnique, isImmutable, returnType
                )

            }
        }
    }

    override fun isGuaranteedDefaultProperty(symbol: FirPropertySymbol): Boolean {
        return symbol.isGuaranteedDefault(session)
    }

    override fun embedBackingField(symbol: FirPropertySymbol): FieldEmbedding {
        val classSymbol = symbol.dispatchReceiverType?.toRegularClassSymbol(session) ?: throw SnaktInternalException(
            symbol.source, "A backing field outside a class"
        )
        return embedBackingField(symbol, classSymbol)
    }

    /**
     * Construct and register the field embedding for this property's backing field, if any exists.
     */
    private fun embedBackingField(
        symbol: FirPropertySymbol, classSymbol: FirRegularClassSymbol
    ): FieldEmbedding {
        val embedding = embedClass(classSymbol)
        val scopedName = symbol.callableId!!.embedMemberBackingFieldName(scopePolicy(symbol, this))
        val backingField = UserFieldEmbedding(
            scopedName,
            embedType(symbol.resolvedReturnType),
            symbol,
            context(checkerContext) { symbol.isUnique() },
            embedding,
            symbol.isManual(session)
        )
        return backingField
    }

    private fun embedAccessorFunction(
        symbol: FirPropertySymbol, functionType: FunctionTypeEmbedding, defaultBehaviour: Boolean
    ): NonInlineFunctionSignature = with(this) {
        val name = if (functionType.paramTypes.isEmpty()) symbol.embedGetterName(this) else symbol.embedSetterName(this)
        functionType.toGenericAccessorSignature(defaultBehaviour).toNamedSignature(name)
            .also { trust.recordLibraryContract(symbol, it.signature) }
            .toNonInlineSignature(symbol = null).toCompleteSignature(symbol.source) {
                preconditions {
                    args {
                        provenInvariants()
                    }
                }
                postconditions {
                    returns {
                        provenInvariants()
                    }
                    stdLib()
                }
            }
    }.also {
        fullSignatures.putIfAbsent(it.signature.name, it.signature)
        accessorReturnTargets.putIfAbsent(it.signature.name, it.returnTarget)
    }.signature

    override fun embedEnumEntry(symbol: FirEnumEntrySymbol): ExpEmbedding {
        val functionType = buildFunctionPretype {
            withReturnType(embedType(symbol.resolvedReturnType))
        }
        val name = symbol.callableId.embedMemberGetterName(MemberEmbeddingPolicy.FUNCTION)
        val signature = functionType.toGenericAccessorSignature(isPure = true).toNamedSignature(name)
            .toNonInlineSignature(symbol = null).toCompleteSignature(symbol.source) {
                postconditions {
                    returns {
                        provenInvariants()
                    }
                }
            }
        fullSignatures.putIfAbsent(signature.signature.name, signature.signature)
        val call = signature.signature.insertCall(emptyList())
        return symbol.source?.let {
            org.jetbrains.kotlin.formver.core.embeddings.expression.WithPosition(
                call, it, org.jetbrains.kotlin.formver.core.embeddings.SourceRole.FirSymbolHolder(symbol),
            )
        } ?: call
    }

    private fun embedExtensionProperty(symbol: FirPropertySymbol): PropertyEmbedding {
        if (symbol.isPure(session)) {
            val callable = embedAnyFunction(symbol.getterSymbol ?: error("Pure property has no getter")) as NamedCallableEmbedding
            return PropertyEmbedding(
                CustomGetter(fullSignatures.getValue(callable.name) as NonInlineFunctionSignature),
                setter = null, hasDefaultBehaviour = false, isUnique = false,
                isVal = symbol.isVal, type = embedType(symbol.resolvedReturnType),
            )
        }
        val getter = embedAccessorFunction(symbol, extensionGetterType(), defaultBehaviour = false)
        val setter = symbol.isVar.ifTrue { embedAccessorFunction(symbol, extensionSetterType(), defaultBehaviour = false) }

        return PropertyEmbedding(
            CustomGetter(getter),
            setter?.let { CustomSetter(it) },
            hasDefaultBehaviour = false,
            isUnique = false,
            isVal = symbol.isVal,
            type = embedType(symbol.resolvedReturnType)
        )
    }

    // endregion

    // region types (also classes)

    override fun embedType(type: ConeKotlinType): TypeEmbedding = buildType { with(this@ProgramConverter) { embedTypeWithBuilder(type) } }

    private val observerBindingRegistry = ObserverBindingRegistry()
    internal val lawEncoding = LawEncoding(this)

    /** Returns an embedding of the class type, with details set. */
    internal fun embedClass(symbol: FirRegularClassSymbol): ClassTypeEmbedding {
        session.externalSpecifications?.modelClass(symbol)?.let { return embedClass(it).also { trust.recordBinding(symbol) } }
        observerBindingRegistry.registerClass(this, symbol)
        if (symbol.classId == GenericArrayEmbedding.classId) {
            registerBuiltinHierarchy(symbol, GenericArrayEmbedding.classType)
            return GenericArrayEmbedding.classType
        }
        if (symbol.classId == IntArrayEmbedding.classId) {
            registerBuiltinHierarchy(symbol, IntArrayEmbedding.classType)
            return IntArrayEmbedding.classType
        }
        if (symbol.classId in stringBuilderClassIds) {
            registerBuiltinHierarchy(symbol, StringBuilderEmbedding.classType)
            return StringBuilderEmbedding.classType
        }

        val className = symbol.classId.embedName()
        typeResolver.lookupClassTypeEmbedding(className)?.let { return it }

        val embedding = typeResolver.getEmbeddingOrExecute(className) {
            val classEmbedding = buildClassPretype {
                withName(className)
            }

            typeResolver.register(classEmbedding, symbol.classKind.isInterface)
            lawEncoding.registerType(classEmbedding, symbol)
            if (symbol.isManual(session)) {
                typeResolver.markManual(className)
            }

            symbol.resolvedSuperTypes.forEach {
                val superTypeName = embedType(it).pretype.name
                typeResolver.addSubtypeRelation(className, superTypeName)
            }

            classEmbedding
        }
        // A static property, such as an enum's `entries`, is not a property of the class's instances.
        symbol.propertySymbols.filter { it.dispatchReceiverType != null }.forEach {
            embedProperty(it)
        }
        return embedding
    }

    // Note: keep in mind that this function is necessary to resolve the name of the function!
    override fun embedFunctionPretype(symbol: FirFunctionSymbol<*>): FunctionTypeEmbedding = buildFunctionPretype {
        with(this@ProgramConverter) { embedFunctionPretypeWithBuilder(symbol) }
    }

    // endregion
}
