package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.analysis.checkers.isPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.references.toResolvedConstructorSymbol
import org.jetbrains.kotlin.fir.references.toResolvedValueParameterSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.isInt
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.core.embeddings.callables.*
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.expression.FirVariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.PlaceholderVariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.BackingFieldGetter
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.FunctionTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.IntArrayEmbedding
import org.jetbrains.kotlin.formver.core.isBorrowed
import org.jetbrains.kotlin.formver.core.isPure
import org.jetbrains.kotlin.formver.core.isUnique
import org.jetbrains.kotlin.formver.core.names.*
import org.jetbrains.kotlin.formver.intrinsics.plugin.isStringBuilder
import org.jetbrains.kotlin.formver.viper.SymbolicName

data class SignatureWithTarget<out S : FunctionSignature>(
    val signature: S, val returnTarget: ReturnTarget
) {
    /**
     * Refines the signature by applying the given action to the current signature.
     * The return target is preserved.
     *
     * It should be used to make a signature more specific, e.g., to add pre+post conditions to an existing signature
     */
    fun <T : FunctionSignature> refineSignature(action: (SignatureWithTarget<S>) -> T): SignatureWithTarget<T> =
        SignatureWithTarget(action(this), returnTarget)
}

@OptIn(DirectDeclarationsAccess::class)
val FirRegularClassSymbol.propertySymbols: List<FirPropertySymbol>
    get() = declarationSymbols.filterIsInstance<FirPropertySymbol>()

private val FirFunctionSymbol<*>.containingPropertyOrSelf
    get() = when (this) {
        is FirPropertyAccessorSymbol -> propertySymbol
        else -> this
    }

val FirFunctionSymbol<*>.receiverType: ConeKotlinType?
    get() = containingPropertyOrSelf.dispatchReceiverType

val FirFunctionSymbol<*>.extensionReceiverType: ConeKotlinType?
    get() = containingPropertyOrSelf.resolvedReceiverTypeRef?.coneType

context(converter: ProgramConversionContext)
fun FirFunctionSymbol<*>.toFunctionSignature(): SignatureWithTarget<FunctionSignature> {
    val dispatchReceiverType = this.receiverType
    val extensionReceiverType = this.extensionReceiverType

    val returnType = converter.embedType(this.resolvedReturnType)

    val returnTarget = when {
        this.isPure(converter.session) -> ReturnTarget.createForPureFunction(returnType)
        else -> converter.returnTargetProducer.getFresh(returnType)
    }

    val dispatchVariable = dispatchReceiverType?.let {
        PlaceholderVariableEmbedding(
            DispatchReceiverName,
            converter.embedType(it),
            isUnique = false,
            isBorrowed = false,
        )
    }
    val extensionVariable = extensionReceiverType?.let {
        PlaceholderVariableEmbedding(
            ExtensionReceiverName,
            converter.embedType(it),
            context(converter.checkerContext) { receiverParameterSymbol?.isUnique() ?: false },
            context(converter.checkerContext) { receiverParameterSymbol?.isBorrowed() ?: false },
        )
    }

    val parameterVariables = this.valueParameterSymbols.map {
        FirVariableEmbedding(
            it.embedName(),
            converter.embedType(it.resolvedReturnType),
            it,
            context(converter.checkerContext) { it.isUnique() },
            context(converter.checkerContext) { it.isBorrowed() },
        )
    }

    val signature = FunctionSignatureImpl(
        converter.embedFunctionPretype(this),
        dispatchVariable,
        extensionVariable,
        parameterVariables,
        returns = returnTarget.variable,
        isPure = this.isPure(converter.session)
    )

    return SignatureWithTarget(signature, returnTarget)
}

context(converter: ProgramConversionContext)
fun FunctionTypeEmbedding.toGenericAccessorSignature(isPure: Boolean): SignatureWithTarget<FunctionSignature> {
    val returnTarget = when (isPure) {
        true -> ReturnTarget.createForPureFunction(returnType)
        false -> converter.returnTargetProducer.getFresh(returnType)
    }
    val extensionVariable = extensionReceiverType?.let { PlaceholderVariableEmbedding(ExtensionReceiverName, it) }
    val dispatchVariable = dispatchReceiverType?.let { PlaceholderVariableEmbedding(DispatchReceiverName, it) }
    val parameterVariables =
        paramTypes.mapIndexed { index, embedding -> PlaceholderVariableEmbedding(AnonymousName(index), embedding) }
    val signature = FunctionSignatureImpl(
        this,
        dispatchVariable,
        extensionVariable,
        parameterVariables,
        returnTarget.variable,
        isPure
    )
    return SignatureWithTarget(signature, returnTarget)
}


context(converter: ProgramConversionContext)
fun SignatureWithTarget<FunctionSignature>.toNamedSignature(symbol: FirFunctionSymbol<*>): SignatureWithTarget<NamedFunctionSignature> =
    this.refineSignature { current ->
        NamedFunctionSignatureImpl(current.signature, symbol.embedName(converter), symbol)
    }

fun SignatureWithTarget<FunctionSignature>.toNamedSignature(name: SymbolicName): SignatureWithTarget<NamedFunctionSignature> =
    this.refineSignature { current ->
        NamedFunctionSignatureImpl(current.signature, name, null)
    }


fun SignatureWithTarget<NamedFunctionSignature>.toNonInlineSignature(symbol: FirFunctionSymbol<*>?): SignatureWithTarget<NonInlineCallable> =
    this.refineSignature { current ->
        NonInlineCallableImpl(current.signature, symbol)
    }


context(converter: ProgramConversionContext)
fun SignatureWithTarget<NonInlineCallable>.toCompleteSignature(symbol: FirFunctionSymbol<*>): SignatureWithTarget<NonInlineFunctionSignature> =
    when {
        symbol.isPrimaryConstructor() -> this.toConstructorSignature(symbol)
        symbol is FirConstructorSymbol && symbol.resolvedReturnType.isStringBuilder(converter.session) ->
            this.toStringBuilderConstructorSignature(symbol)
        else -> this.toNormalSignature(symbol)
    }

context(converter: ProgramConversionContext)
fun SignatureWithTarget<NonInlineCallable>.toCompleteSignature(
    declarationSource: KtSourceElement?, action: FunctionConditionBuilder.() -> Unit
): SignatureWithTarget<NonInlineFunctionSignature> = refineSignature { current ->
    val (preconditions, postconditions) = current.signature.buildConditions(converter.typeResolver, action)
    NonInlineFunctionSignature(current.signature, preconditions, postconditions, declarationSource)
}


/**
 * The class whose object a call of this function returns open: this is a primary constructor of a class whose predicate
 * is built from its properties. The caller folds the predicates of the object, so that the predicate of each argument
 * the object stores in a `@Unique` property moves into it intact. `null` for every other function.
 */
val NamedFunctionSignature.constructedOpen: ClassTypeEmbedding?
    get() = (returns.type.pretype as? ClassTypeEmbedding)
        ?.takeIf { symbol?.isPrimaryConstructor() == true && it.hasPropertyPredicate }

/**
 * The properties that this primary constructor initializes with each of its parameters: those the parameter declares,
 * and those it initializes as an argument of the superclass constructor.
 */
@OptIn(SymbolInternals::class)
private fun FirConstructorSymbol.initializedProperties(session: FirSession): Map<FirValueParameterSymbol, List<FirPropertySymbol>> {
    val declared = resolvedReturnType.toRegularClassSymbol(session)?.propertySymbols.orEmpty().mapNotNull { property ->
        property.correspondingValueParameterFromPrimaryConstructor?.let { it to property }
    }
    val delegation = fir.delegatedConstructor
    val inherited = delegation?.calleeReference?.toResolvedConstructorSymbol()
        ?.takeIf { it.isPrimary }?.initializedProperties(session).orEmpty()
    val passedOn = delegation?.resolvedArgumentMapping.orEmpty().flatMap { (argument, superParameter) ->
        val parameter = (argument as? FirPropertyAccessExpression)?.calleeReference?.toResolvedValueParameterSymbol()
            ?: return@flatMap emptyList()
        inherited[superParameter.symbol].orEmpty().map { parameter to it }
    }
    return (declared + passedOn).groupBy({ it.first }, { it.second })
}

/**
 * The signature of a primary constructor. It ensures that each property a parameter initializes equals the parameter.
 * When the class is [constructedOpen], the object is returned with its predicates unfolded, and a `@Unique` parameter
 * stored in a `@Unique` property keeps its predicate: the caller's fold moves it into the object.
 */
context(converter: ProgramConversionContext)
fun SignatureWithTarget<NonInlineCallable>.toConstructorSignature(symbol: FirFunctionSymbol<*>): SignatureWithTarget<NonInlineFunctionSignature> =
    refineSignature { current ->
        require(symbol is FirConstructorSymbol) { "Primary constructors are constructor symbols" }
        val typeResolver = converter.typeResolver
        val result = returnTarget.variable
        val constructed = current.signature.constructedOpen
        val initialized = symbol.initializedProperties(converter.session)
        val parameterProperties = current.signature.params.flatMap { param ->
            require(param is FirVariableEmbedding) { "Constructor parameters must be represented by FirVariableEmbeddings" }
            initialized[param.symbol].orEmpty().mapNotNull { property ->
                typeResolver.lookupDefaultBehavingProperties(property.embedMemberPropertyName(converter))?.let { param to it }
            }
        }
        val stored = if (constructed == null) emptyList() else parameterProperties.mapNotNull { (param, property) ->
            property.ownedStep?.takeIf { param.isUnique }?.let { param to it }
        }

        val fieldPostconditions = parameterProperties.map { (param, property) ->
            val getter = property.getter!!
            val value = if (constructed != null && getter is BackingFieldGetter) PrimitiveFieldAccess(result, getter.field)
            else getter.getValueSimple(result, typeResolver)
            EqCmp(value, param)
        }

        val contract = current.signature.buildConditions(typeResolver) {
            userFunctionPreconditions(consumes = { param -> stored.none { it.first == param } })
            userFunctionPostcondition(resultFolded = constructed == null)
            if (constructed != null) {
                val omitted = stored.mapTo(mutableSetOf()) { it.second.name }
                addPostconditions(listOf(with(typeResolver) { constructed.openPredicateChain(result, omitted) }))
            }
            addPostconditions(fieldPostconditions)
            if (symbol.resolvedReturnType.toRegularClassSymbol(converter.session)?.classId == IntArrayEmbedding.classId) {
                val size = current.signature.params.single()
                addPreconditions(listOf(OperatorExpEmbeddings.GeIntInt(size, IntLit(0))))
                addPostconditions(listOf(EqCmp(IntArraySize(result), size), IntArrayAllZero(result)))
            }
        }

        // The open predicate bodies restate the result's type.
        NonInlineFunctionSignature(current.signature, contract.preconditions, contract.postconditions.distinct(), symbol.source)
    }

/**
 * The signature of a `StringBuilder` constructor. The builder is empty unless the constructor takes initial contents:
 * every parameter is an `Int` capacity.
 */
context(converter: ProgramConversionContext)
fun SignatureWithTarget<NonInlineCallable>.toStringBuilderConstructorSignature(symbol: FirConstructorSymbol): SignatureWithTarget<NonInlineFunctionSignature> =
    refineSignature { current ->
        val contract = current.signature.buildConditions(converter.typeResolver) {
            userFunctionContract()
            if (symbol.valueParameterSymbols.all { it.resolvedReturnType.isInt }) {
                addPostconditions(listOf(EqCmp(StringBuilderToString(returnTarget.variable, receiverOwned = true), StringLit(""))))
            }
        }
        NonInlineFunctionSignature(current.signature, contract.preconditions, contract.postconditions, symbol.source)
    }

context(converter: ProgramConversionContext)
fun SignatureWithTarget<NonInlineCallable>.toNormalSignature(symbol: FirFunctionSymbol<*>): SignatureWithTarget<NonInlineFunctionSignature> =
    refineSignature { current ->
        val contract = current.signature.buildConditions(converter.typeResolver) {
            userFunctionContract()
            val (preconditions, postconditions) = converter.embedContract(
                symbol, current.signature, returnTarget
            )
            addPreconditions(preconditions)
            addPostconditions(postconditions)
        }
        NonInlineFunctionSignature(current.signature, contract.preconditions, contract.postconditions, symbol.source)
    }

@OptIn(SymbolInternals::class)
context(converter: ProgramConversionContext)
fun SignatureWithTarget<NamedFunctionSignature>.toInlineSignature(symbol: FirFunctionSymbol<*>): SignatureWithTarget<InlineNamedFunction> =
    this.refineSignature { current ->
        val body = symbol.fir.body ?: throw SnaktInternalException(symbol.source, "Expected function body, got null")
        val contract = current.signature.buildConditions(converter.typeResolver) {
            val (precondition, postcondition) = converter.embedContract(
                symbol, current.signature, current.returnTarget
            )
            userFunctionContract()
            addPreconditions(precondition)
            addPostconditions(postcondition)
        }

        val fullSignature = InlineNamedFunction(
            current.signature, body, contract.preconditions, contract.postconditions, symbol
        )
        fullSignature
    }
