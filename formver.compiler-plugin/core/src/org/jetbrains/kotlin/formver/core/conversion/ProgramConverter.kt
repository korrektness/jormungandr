/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.formver.intrinsics.plugin.stringBuilderClassIds
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.formver.common.PluginConfiguration
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.*
import org.jetbrains.kotlin.formver.core.diagnostics.ConversionErrors
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.domains.SeqMultisetDomain
import org.jetbrains.kotlin.formver.core.embeddings.callables.*
import org.jetbrains.kotlin.formver.core.embeddings.expression.AnonymousBuiltinVariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.AnonymousVariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.*
import org.jetbrains.kotlin.formver.core.embeddings.types.*
import org.jetbrains.kotlin.formver.core.names.*
import org.jetbrains.kotlin.formver.core.purity.checkValidity
import org.jetbrains.kotlin.formver.core.purity.isPure
import org.jetbrains.kotlin.formver.core.purity.checkReadOnlyVarReads
import org.jetbrains.kotlin.formver.core.purity.checkSpecificationVarReads
import org.jetbrains.kotlin.formver.core.purity.checkNoMultisetValues
import org.jetbrains.kotlin.formver.uniqueness.plugin.FunctionUniquenessAnalysis
import org.jetbrains.kotlin.formver.uniqueness.plugin.isGuaranteedDefault
import org.jetbrains.kotlin.formver.uniqueness.plugin.multisetClassId
import org.jetbrains.kotlin.formver.uniqueness.plugin.uniquenessFacts
import org.jetbrains.kotlin.formver.core.linearization.FoldStateException
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Program
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

    private fun emit(source: KtSourceElement?, factory: KtDiagnosticFactory1<String>, msg: String) {
        context(checkerContext) {
            reporter.reportOn(source, factory, msg)
        }
        hadConversionError = true
    }

    override fun reportPurityViolation(source: KtSourceElement?, msg: String) =
        emit(source, ConversionErrors.PURITY_VIOLATION, msg)

    override fun reportUnsupportedOwnership(source: KtSourceElement?, msg: String) =
        emit(source, ConversionErrors.UNSUPPORTED_OWNERSHIP, msg)

    override fun reportUntrackedWrite(source: KtSourceElement?) {
        context(checkerContext) {
            reporter.reportOn(source, ConversionErrors.UNTRACKED_WRITE)
        }
    }

    override fun reportUnsupportedFeature(source: KtSourceElement?, msg: String) =
        emit(source, ConversionErrors.UNSUPPORTED_FEATURE, msg)

    private fun reportVerificationSkipped(source: KtSourceElement?, msg: String) {
        context(checkerContext) {
            reporter.reportOn(source, ConversionErrors.VERIFICATION_SKIPPED, msg)
        }
    }

    private val specialFunctions: Map<SymbolicName, SpecialKotlinFunction> = buildMap {
        putAll(SpecialKotlinFunctions.byName)
        putAll(PartiallySpecialKotlinFunctions.generateAllByName())
    }

    private val fullSignatures: MutableMap<SymbolicName, CompleteFunctionSignature> = mutableMapOf()

    private val callable: MutableMap<SymbolicName, SignatureWithTarget<NamedCallableEmbedding>> = mutableMapOf()

    private data class RegisteredFunction(
        val declaration: FirSimpleFunction,
        val signature: CompleteFunctionSignature,
        val returnTarget: ReturnTarget,
        val hasUniquenessErrors: Boolean,
    )

    private val registered: MutableList<RegisteredFunction> = mutableListOf()

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

    val skipsVerification: Boolean
        get() = hadUniquenessError || hadOwnershipError

    override val typeResolver: TypeResolver = TypeResolver()

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

    private val usesStringBuilder: Boolean
        get() = typeResolver.lookupClassTypeEmbedding(StringBuilderEmbedding.classType.name) != null

    fun buildProgram(): Program = Program(
        domains = listOf(RuntimeTypeDomain(typeResolver)) +
                listOfNotNull(SeqMultisetDomain.takeIf { usesIntArray && typeResolver.usesMultiset }),
        // Public fields with the same name are represented differently at `FieldEmbedding` level
        // but map to the same Viper field, so we deduplicate before emitting.
        fields = typeResolver.backingFields().distinctBy { it.name }.map { it.toViper() } +
                listOfNotNull(contentsField.takeIf { usesIntArray || usesStringBuilder }),
        functions = SpecialFunctions.all + SpecialFunctions.multiset.takeIf { typeResolver.usesMultiset }.orEmpty() +
                linearizedBodyResolver.functions +
                listOfNotNull(IntArrayEmbedding.arraySizeFunction.takeIf { usesIntArray }),
        methods = SpecialMethods.all + linearizedBodyResolver.methods,
        predicates = typeResolver.classTypeEmbeddings().map {
            with(typeResolver) {
                it.uniquePredicate()
            }
        },
        adts = emptyList(),
    )


    // region Callable Conversion

    /** The declaration passed to [register]. */
    private var registeredSymbol: FirFunctionSymbol<*>? = null

    /**
     * Embed the declaration's signature and embeds the body.
     */
    fun register(declaration: FirSimpleFunction) {
        registeredSymbol = declaration.symbol
        val signature = embedCompleteSignature(declaration.symbol)
        embedFunctionBody(declaration.symbol, signature)
        val hasUniquenessErrors = uniquenessOutcomeOf(declaration).hasErrors
        registered += RegisteredFunction(declaration, signature.signature, signature.returnTarget, hasUniquenessErrors)
    }


    /**
     * Walk converted bodies to surface validity / purity errors via the diagnostic reporter. If any
     * conversion error has been reported by the time this returns, emit a [ConversionErrors.VERIFICATION_SKIPPED]
     * summary on every registered declaration so the user sees per-function attribution for the bail-out.
     * Otherwise, emit it on every registered declaration with uniqueness or locality errors.
     * Callers should inspect [hadConversionError] afterwards and skip [linearizeAll] when set, and skip verification
     * when [skipsVerification] is set after [linearizeAll].
     */
    fun validateAll() {
        convertedBodyResolver.forEachImpure { name, body ->
            val source = fullSignatures[name]?.declarationSource
            body.bodyExp.checkValidity(source, this)
        }
        convertedBodyResolver.forEachPure { name, body ->
            if (!body.isPure()) {
                val source = fullSignatures[name]?.declarationSource
                reportPurityViolation(source, "Impure function body detected in pure function")
            }
        }
        for ((_, signature) in fullSignatures) {
            val source = signature.declarationSource ?: continue
            for (condition in signature.preconditions + signature.postconditions) {
                condition.checkValidity(source, this)
            }
        }
        for (entry in registered) {
            val reason = when {
                hadConversionError -> "errors in its declaration"
                entry.hasUniquenessErrors -> "uniqueness or locality errors"
                else -> continue
            }
            reportVerificationSkipped(
                entry.declaration.source,
                "Function '${entry.declaration.name.asString()}' was not verified because of $reason",
            )
        }
    }


    private fun linearizePure(name: SymbolicName, signature: CompleteFunctionSignature) {
        val converted = convertedBodyResolver.lookupPure(name)
        val linearized = converted?.let { linearizePureBody(signature.declarationSource, it) }
        linearizedBodyResolver.storeFunction(name, signature.toViperFunction(typeResolver, linearized))
    }

    private fun linearizeImpure(name: SymbolicName, signature: CompleteFunctionSignature) {
        val source = signature.declarationSource
        val converted = convertedBodyResolver.lookupImpure(name)
        // The fold state follows the uniqueness checker's facts, which a function with uniqueness errors lacks.
        val tracksOwnership = signature.symbol?.let { uniquenessOutcomes[it]?.hasErrors } != true
        val body = converted?.let {
            try {
                linearizeImpureBody(source, it, tracksOwnership)
            } catch (e: FoldStateException) {
                hadOwnershipError = true
                context(checkerContext) {
                    reporter.reportOn(e.source ?: source, ConversionErrors.UNSUPPORTED_OWNERSHIP, e.message)
                }
                reportVerificationSkipped(
                    source,
                    "Function '${signature.symbol?.name?.asString()}' was not verified because of unsupported ownership",
                )
                null
            }
        }
        val method = body?.toViperMethod(signature, typeResolver) ?: signature.toViperMethod(typeResolver, null)
        linearizedBodyResolver.storeMethod(name, method)
    }

    /**
     * Build the finalized Viper `Method` / `Function` for every embedded callable, storing each in
     * [linearizedBodyResolver]. Should only be invoked when [hadConversionError] is false.
     *
     * Iterates the embedding maps directly (rather than the converted-body map) so that callables
     * without a body still get a Viper header.
     */
    fun linearizeAll() {
        val (pure, impure) = fullSignatures.entries.partition { it.value.isPure }
        pure.forEach { linearizePure(it.key, it.value) }
        impure.forEach { linearizeImpure(it.key, it.value) }
    }

    // endregion

    // region Conversion Context

    /**
     * The uniqueness checker's result for one function. [analysis] is `null` when the function has no body or
     * [hasErrors] is set.
     */
    private data class UniquenessOutcome(val analysis: FunctionUniquenessAnalysis?, val hasErrors: Boolean)

    private val uniquenessOutcomes: MutableMap<FirFunctionSymbol<*>, UniquenessOutcome> = mutableMapOf()

    private fun uniquenessOutcomeOf(declaration: FirFunction): UniquenessOutcome =
        uniquenessOutcomes.getOrPut(declaration.symbol) {
            // A function without a body, such as a library function, has nothing to analyse.
            if (declaration.body == null) {
                UniquenessOutcome(analysis = null, hasErrors = false)
            } else {
                val analysis = context(checkerContext) { session.uniquenessFacts.analysis(declaration) }
                UniquenessOutcome(analysis, hasErrors = analysis == null)
            }
        }

    /**
     * Runs [check] on the source of [declaration] when it is the registered declaration and the uniqueness checker
     * has a state for it. The callees it embeds are checked when they are registered themselves. Without a state
     * every receiver counts as not owned, and the function is not verified anyway.
     */
    private fun checkVarReads(declaration: FirFunction, check: (KtSourceElement) -> Unit) {
        if (declaration.symbol != registeredSymbol) return
        val source = declaration.source ?: return
        if (uniquenessOutcomeOf(declaration).analysis != null) check(source)
    }

    private fun createBodyConversionContext(
        symbol: FirFunctionSymbol<*>,
        signature: SignatureWithTarget<NamedFunctionSignature>,
        uniquenessAnalysis: FunctionUniquenessAnalysis?,
    ): StmtConversionContext {
        val paramResolver = RootParameterResolver(
            this@ProgramConverter,
            signature.signature,
            symbol.valueParameterSymbols,
            signature.signature.labelName,
            signature.returnTarget
        )
        val stmtCtx = MethodConverter(
            this@ProgramConverter,
            signature.signature,
            paramResolver,
            scopeIndexProducer.getFresh(),
            uniquenessAnalysis,
        ).statementCtxt()
        return stmtCtx
    }

    private fun createContractConversionContext(
        symbol: FirFunctionSymbol<*>,
        signature: NamedFunctionSignature,
        firSpec: FirSpecification,
        returnTarget: ReturnTarget,
        uniquenessAnalysis: FunctionUniquenessAnalysis?,
    ): Pair<StmtConversionContext, StmtConversionContext> {
        val rootResolver = RootParameterResolver(
            this@ProgramConverter,
            signature,
            symbol.valueParameterSymbols,
            signature.labelName,
            returnTarget,
        )

        val wrappedResolver = firSpec.returnVar?.let { ReturnVarSubstitutor(it, signature.returns) }
            ?.let { ctx -> SubstitutedReturnParameterResolver(rootResolver, ctx) } ?: rootResolver

        val preconditionContext = MethodConverter(
            this@ProgramConverter,
            signature,
            rootResolver,
            ScopeIndex.NoScope,
            uniquenessAnalysis,
        ).statementCtxt()

        val postconditionContext = MethodConverter(
            this@ProgramConverter,
            signature,
            wrappedResolver,
            ScopeIndex.NoScope,
            uniquenessAnalysis,
        ).statementCtxt()

        return Pair(preconditionContext, postconditionContext)
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
        val declaration = symbol.fir as? FirSimpleFunction ?: throw SnaktInternalException(
            symbol.source, "Expected FirSimpleFunction, got unexpected type ${symbol.fir.javaClass.simpleName}"
        )

        val context = createBodyConversionContext(symbol, signature, uniquenessOutcomeOf(declaration).analysis)
        if (signature.signature.isPure) {
            val body = context.convertPureBody(declaration)
            checkVarReads(declaration) { body.checkReadOnlyVarReads(it, this) }
            convertedBodyResolver.storePure(signature.signature.name, body)
        } else {
            val body = context.convertImpureBody(declaration, signature.signature, signature.returnTarget)
            checkVarReads(declaration) { body?.bodyExp?.checkSpecificationVarReads(it, this) }
            declaration.source?.let { source -> body?.bodyExp?.checkNoMultisetValues(source, this) }
            body?.let { convertedBodyResolver.storeImpure(signature.signature.name, it) }
        }
    }


    /**
     * Embeds the full function signature (with pre+post conditions).
     */
    private fun embedCompleteSignature(symbol: FirFunctionSymbol<*>): SignatureWithTarget<CompleteFunctionSignature> {
        val callable = with(this) {
            val namedSignature = symbol.toFunctionSignature().toNamedSignature(symbol)
            if (symbol.shouldBeInlined) {
                namedSignature.toInlineSignature(symbol).also {
                    callable.putIfAbsent(it.signature.name, it)
                }
            } else {
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
        // NOTE: The order of the embedSpecialFunction and the callable lookup does matter.

        // check if it must be handled specially
        embedSpecialFunction(symbol)?.let { return it }

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
        @OptIn(SymbolInternals::class) val declaration = symbol.fir
        val body = declaration.body

        /** Specifications are only allowed inside simple functions.
         * We are also unable to retrieve them when body is not visible,
         * although ideally we should be able to see preconditions and postconditions
         * from other modules.
         */
        if (declaration !is FirSimpleFunction || body == null) {
            return Pair(emptyList(), emptyList())
        }

        val firSpec = extractFirSpecification(body, declaration.symbol.resolvedReturnType)


        val (preconditionContext, postconditionContext) = createContractConversionContext(
            symbol, signature, firSpec, returnTarget, uniquenessOutcomeOf(declaration).analysis
        )

        val preconditions = firSpec.precond?.let { preconditionContext.collectInvariants(it) } ?: emptyList()
        val postconditions = firSpec.postcond?.let { postconditionContext.collectInvariants(it) } ?: emptyList()

        checkVarReads(declaration) { source ->
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
        return Pair(userContract.first, kotlinContractPostcondition + userContract.second)
    }

    // endregion

    // region Properties

    /**
     * This function embeds properties. Depending on the behavior of the property, it can be handled differently.
     *
     *
     * * Closed `val` (No Getter): Maps to a pure function. This can be done because the value cannot be changed.
     * * Closed `val` (With Getter): Maps to an impure function to execute the custom getter logic.
     * * Closed `var`: Maps to a backing field. This value needs permissions and can be changed.
     * * Open `val` / `var`: Maps to an impure method. Abstracted because subclasses can override the behavior, introduce side effects, or turn a `val` into a `var`.
     * * Extension properties are handled like open properties.
     */
    override fun embedProperty(symbol: FirPropertySymbol): PropertyEmbedding {
        if (symbol.receiverParameterSymbol != null) {
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
                    (isDefaultProperty && !isImmutable) || isManual -> {
                        // use a backing field
                        val field = embedBackingField(symbol, regularClass)
                        Pair(BackingFieldGetter(field), BackingFieldSetter(field))
                    }

                    isDefaultProperty -> {
                        // use pure function
                        val functionType = buildFunctionPretype {
                            withDispatchReceiver(embedType(symbol.dispatchReceiverType!!))
                            withReturnType(returnType)
                        }
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
                        val getterType = buildFunctionPretype {
                            withDispatchReceiver { any() }
                            withReturnType { nullableAny() }
                        }
                        val setterType = buildFunctionPretype {
                            withDispatchReceiver { any() }
                            withParam { nullableAny() }
                            withReturnType { nullableAny() }
                        }

                        Pair(
                            CustomGetter(embedAccessorFunction(symbol, getterType, defaultBehaviour = false)),
                            symbol.isVar.ifTrue {
                                CustomSetter(
                                    embedAccessorFunction(
                                        symbol, setterType, defaultBehaviour = false
                                    )
                                )
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
                }
            }
    }.also {
        fullSignatures.putIfAbsent(it.signature.name, it.signature)
    }.signature

    private fun embedExtensionProperty(symbol: FirPropertySymbol): PropertyEmbedding {
        val getterType = buildFunctionPretype {
            withExtensionReceiver { nullableAny() }
            withReturnType { nullableAny() }
        }

        val setterType = buildFunctionPretype {
            withExtensionReceiver { nullableAny() }
            withParam { nullableAny() }
            withReturnType { nullableAny() }
        }

        val getter = embedAccessorFunction(symbol, getterType, defaultBehaviour = false)
        val setter = symbol.isVar.ifTrue { embedAccessorFunction(symbol, setterType, defaultBehaviour = false) }

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

    override fun embedType(type: ConeKotlinType): TypeEmbedding = buildType { embedTypeWithBuilder(type) }

    /**
     * Returns an embedding of the class type, with details set.
     */
    private fun embedClass(symbol: FirRegularClassSymbol): ClassTypeEmbedding {
        if (symbol.classId == IntArrayEmbedding.classId) {
            typeResolver.register(IntArrayEmbedding.classType, isInterface = false)
            return IntArrayEmbedding.classType
        }
        if (symbol.classId in stringBuilderClassIds) {
            typeResolver.register(StringBuilderEmbedding.classType, isInterface = false)
            return StringBuilderEmbedding.classType
        }

        val className = symbol.classId.embedName()
        typeResolver.lookupClassTypeEmbedding(className)?.let { return it }

        val embedding = typeResolver.getEmbeddingOrExecute(className) {
            val classEmbedding = buildClassPretype {
                withName(className)
            }

            typeResolver.register(classEmbedding, symbol.classKind.isInterface)
            if (symbol.isManual(session)) {
                typeResolver.markManual(className)
            }

            symbol.resolvedSuperTypes.forEach {
                val superTypeName = embedType(it).pretype.name
                typeResolver.addSubtypeRelation(className, superTypeName)
            }

            classEmbedding
        }
        symbol.propertySymbols.forEach {
            embedProperty(it)
        }
        return embedding
    }

    // Note: keep in mind that this function is necessary to resolve the name of the function!
    override fun embedFunctionPretype(symbol: FirFunctionSymbol<*>): FunctionTypeEmbedding = buildFunctionPretype {
        embedFunctionPretypeWithBuilder(symbol)
    }


    private fun TypeBuilder.embedTypeWithBuilder(type: ConeKotlinType): PretypeBuilder = when {
        type is ConeErrorType -> error("Encountered an erroneous type: $type")
        type is ConeTypeParameterType -> {
            isNullable = true; any()
        }

        type.isString -> {
            val stringClassSymbol = type.toClassSymbol(session) as FirRegularClassSymbol
            stringClassSymbol.propertySymbols.forEach {
                embedProperty(it)
            }
            string()
        }

        type.isUnit -> unit()
        type.isChar -> char()
        type.isInt -> int()
        type.isBoolean -> boolean()
        type.isNothing -> nothing()
        type.isSomeFunctionType(session) -> function {
            check(type is ConeClassLikeType) { "Expected a ConeClassLikeType for a function type, got $type" }
            type.receiverType(session)?.let { withDispatchReceiver { embedTypeWithBuilder(it) } }
            type.valueParameterTypesWithoutReceivers(session).forEach { param ->
                withParam { embedTypeWithBuilder(param) }
            }
            withReturnType { embedTypeWithBuilder(type.returnType(session)) }
        }

        type.canBeNull(session) -> {
            isNullable = true
            embedTypeWithBuilder(type.withNullability(false, session.typeContext))
        }

        type.isAny -> any()
        type is ConeClassLikeType && type.lookupTag.classId == multisetClassId -> {
            val element = type.typeArguments.single().type
            if (element !is ConeTypeParameterType && element?.isInt != true) {
                throw UnsupportedFeatureException(null, "type `$type`, a `Multiset` whose element type is not `Int`")
            }
            typeResolver.markMultisetUsed()
            multiset()
        }
        type is ConeClassLikeType -> {
            val classLikeSymbol = type.toClassSymbol(session)
            if (classLikeSymbol is FirRegularClassSymbol) {
                existing(embedClass(classLikeSymbol))
            } else {
                unimplementedTypeEmbedding(type)
            }
        }

        else -> unimplementedTypeEmbedding(type)
    }

    private fun FunctionPretypeBuilder.embedFunctionPretypeWithBuilder(symbol: FirFunctionSymbol<*>) {
        symbol.receiverType?.let {
            withDispatchReceiver { embedTypeWithBuilder(it) }
        }
        symbol.extensionReceiverType?.let {
            withExtensionReceiver { embedTypeWithBuilder(it) }
        }
        symbol.valueParameterSymbols.forEach { param ->
            withParam {
                embedTypeWithBuilder(param.resolvedReturnType)
            }
        }
        withReturnType { embedTypeWithBuilder(symbol.resolvedReturnType) }
        returnsUnique = symbol.returnsUnique()
    }

    private fun unimplementedTypeEmbedding(type: ConeKotlinType): Nothing =
        throw UnsupportedFeatureException(null, "type `$type`")

    // endregion
}
