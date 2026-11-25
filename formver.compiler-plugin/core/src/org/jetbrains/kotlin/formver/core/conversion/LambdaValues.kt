/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.formver.core.names.*
import org.jetbrains.kotlin.fir.resolve.dfa.controlFlowGraph
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.uniqueness.plugin.extendsLocalFlow
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirSamConversionExpression
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.InlineStatus
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReceiverParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineFunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.callables.parameterVariables
import org.jetbrains.kotlin.formver.core.embeddings.expression.Declare
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.If
import org.jetbrains.kotlin.formver.core.embeddings.expression.LambdaExp
import org.jetbrains.kotlin.formver.core.embeddings.expression.UnitLit
import org.jetbrains.kotlin.formver.core.embeddings.expression.BooleanLit
import org.jetbrains.kotlin.formver.core.embeddings.expression.VariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.blockOf
import org.jetbrains.kotlin.formver.core.embeddings.expression.toBlock
import org.jetbrains.kotlin.formver.core.embeddings.properties.PropertyAccessEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.buildType
import org.jetbrains.kotlin.formver.uniqueness.plugin.FunctionUniquenessAnalysis

/**
 * Whether Kotlin may keep this lambda as a value: neither a calls-in-place contract nor inlining runs it during the
 * call it is passed to.
 */
val FirAnonymousFunction.mayEscape: Boolean
    get() = invocationKind == null && inlineStatus != InlineStatus.Inline

/** The locals and parameters declared outside this lambda that its body references, assignment targets included. */
val FirAnonymousFunction.capturedLocals: Set<FirVariableSymbol<*>>
    get() {
        val referenced = linkedSetOf<FirVariableSymbol<*>>()
        val declared = mutableSetOf<FirVariableSymbol<*>>()
        accept(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                when (element) {
                    is FirProperty -> declared.add(element.symbol)
                    is FirValueParameter -> declared.add(element.symbol)
                    is FirQualifiedAccessExpression -> when (val symbol = element.calleeReference.symbol) {
                        is FirPropertySymbol -> if (symbol.isLocal) referenced.add(symbol)
                        is FirValueParameterSymbol -> referenced.add(symbol)
                        else -> {}
                    }
                }
                element.acceptChildren(this)
            }
        })
        return referenced - declared
    }

/** The local `var`s declared outside this lambda that its body reads or assigns. */
val FirAnonymousFunction.capturedLocalVars: List<FirPropertySymbol>
    get() = capturedLocals.filterIsInstance<FirPropertySymbol>().filter { it.isVar }

/**
 * Collects the local `var`s captured by lambdas that may escape and by local functions and classes. A lambda argument of a call that the conversion runs
 * in place, a specification or an array initializer, does not escape as a lambda value. Nor does a lambda that
 * [isLifted] finds lifted to a class with explicit capture storage.
 */
private class EscapingCaptureCollector(private val isLifted: (FirExpression) -> Boolean) : FirVisitorVoid() {
    val captured = mutableSetOf<FirPropertySymbol>()
    private val ranInPlace = mutableSetOf<FirAnonymousFunction>()

    override fun visitElement(element: FirElement) {
        when (element) {
            is FirFunctionCall ->
                if (element.runsLambdaArguments || isLifted(element)) element.arguments.forEach(::markRanInPlace)
            is FirSamConversionExpression -> if (isLifted(element)) markRanInPlace(element.expression)
            is FirAnonymousFunction ->
                if (element !in ranInPlace && element.mayEscape) captured.addAll(element.capturedLocalVars)
            is FirSimpleFunction, is FirRegularClass ->
                captured.addAll((element as FirDeclaration).capturedLocals().filterIsInstance<FirPropertySymbol>().filter { it.isVar })
            else -> {}
        }
        element.acceptChildren(this)
    }

    private fun markRanInPlace(argument: FirExpression) {
        (argument.unwrapArgument() as? FirAnonymousFunctionExpression)?.let { ranInPlace.add(it.anonymousFunction) }
    }
}

private val FirFunctionCall.runsLambdaArguments: Boolean
    get() {
        val symbol = calleeReference.symbol as? FirCallableSymbol<*> ?: return false
        val id = symbol.callableId ?: return false
        return id.packageName == formverPluginPackage ||
                symbol is FirConstructorSymbol && id.packageName == StandardNames.BUILT_INS_PACKAGE_FQ_NAME &&
                id.className?.asString()?.endsWith("Array") == true
    }

private val formverPluginPackage = FqName("org.jetbrains.kotlin.formver.plugin")

/**
 * The local `var`s declared directly among these statements that a lambda in them that may escape, or a local function
 * or class in them, captures.
 */
private fun List<FirStatement>.varsCapturedByEscapingCode(isLifted: (FirExpression) -> Boolean): List<FirPropertySymbol> {
    val vars = filterIsInstance<FirProperty>().filter { it.isVar }.map { it.symbol }
    if (vars.isEmpty()) return emptyList()
    val collector = EscapingCaptureCollector(isLifted)
    forEach { it.accept(collector) }
    return vars.filter { it in collector.captured }
}

/** Embeds the lambda [function] as a [LambdaExp], with the flags and roots it shares if created as a value. */
fun StmtConversionContext.convertLambda(function: FirAnonymousFunction, samType: TypeEmbedding? = null): LambdaExp {
    val (signature, _) = function.symbol.toFunctionSignature()
    val captured = function.capturedLocals.filter { capturedAccess(it) == null }
    val vars = function.capturedLocalVars.filter { capturedAccess(it) == null }
    val roots = linkedMapOf<FirBasedSymbol<*>, ExpEmbedding>()
    captured.associateWithTo(roots) { embedLocalSymbol(it) }
    if (samType != null) {
        function.capturedReceivers.forEach { (symbol, receiver) ->
            if (capturedAccess(symbol) == null) roots[symbol] = convert(receiver)
        }
    }
    return LambdaExp(signature, function, this, function.symbol.label?.name, vars.associateWith { sharedLocalFlag(it) }, roots, samType)
}

/**
 * Converts [statements] with [convertStatement], after declaring, for each local `var` among them that a lambda value
 * or a local function or class may capture, a flag that is false until such a lambda or declaration is created. A read of the `var` gives an arbitrary value once
 * its flag holds.
 */
fun StmtConversionContext.convertStatements(
    statements: List<FirStatement>,
    convertStatement: StmtConversionContext.(FirStatement) -> ExpEmbedding = { convertReportingUnsupported(it) },
): ExpEmbedding {
    val flags = statements.varsCapturedByEscapingCode { liftedLambda(it) != null }.map { symbol ->
        declareAnonVar(buildType { boolean() }, BooleanLit(false)).also { registerSharedLocalFlag(symbol, it.variable) }
    }
    return (flags + statements.map { convertStatement(it) }).toBlock()
}

/** A local `var` that a lambda value may capture: its holder may write it whenever [flag] holds. */
class SharedLocalAccess(private val variable: VariableEmbedding, private val flag: VariableEmbedding) :
    PropertyAccessEmbedding {
    override fun getValue(ctx: StmtConversionContext): ExpEmbedding =
        If(flag, ctx.unconstrainedValue(variable.type), variable, variable.type)

    override fun setValue(value: ExpEmbedding, ctx: StmtConversionContext): ExpEmbedding = variable.setValue(value, ctx)
}

/**
 * A local `var` as the body of a lambda value or local declaration that captures it sees it: each read is arbitrary,
 * and a write goes to [sink], which no read sees. [sink] is declared by the body's entry.
 */
class DetachedVarAccess(private val sink: VariableEmbedding) : PropertyAccessEmbedding {
    override fun getValue(ctx: StmtConversionContext): ExpEmbedding = ctx.unconstrainedValue(sink.type)

    override fun setValue(value: ExpEmbedding, ctx: StmtConversionContext): ExpEmbedding = blockOf(sink.setValue(value, ctx), UnitLit)
}

/** References to enclosing receivers, excluding receivers supplied to this lambda or its nested lambdas. */
private val FirAnonymousFunction.capturedReceivers: Map<FirBasedSymbol<*>, FirThisReceiverExpression>
    get() {
        val captures = linkedMapOf<FirBasedSymbol<*>, FirThisReceiverExpression>()
        val ownReceivers = mutableSetOf<FirBasedSymbol<*>>()
        accept(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                when (element) {
                    is FirFunction -> {
                        element.receiverParameter?.symbol?.let(ownReceivers::add)
                        element.symbol.contextParameterSymbols.forEach(ownReceivers::add)
                    }
                    is FirRegularClass -> ownReceivers.add(element.symbol)
                    else -> {}
                }
                element.acceptChildren(this)
            }
        })
        accept(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                if (element is FirThisReceiverExpression) {
                    val symbol = element.calleeReference.boundSymbol as? FirBasedSymbol<*>
                    val receiver = symbol is FirReceiverParameterSymbol || symbol is FirClassSymbol<*> && symbol.classKind != ClassKind.OBJECT
                    if (symbol != null && receiver && symbol !in ownReceivers) {
                        captures.putIfAbsent(symbol, element)
                    }
                }
                element.acceptChildren(this)
            }
        })
        return captures
    }

/**
 * The method that verifies the body of this lambda value, named [name]. Each captured `val`, parameter or receiver is
 * an arbitrary shared value of its type, and each captured local `var` an arbitrary value at each read, whose writes
 * are lost.
 */
context(converter: ProgramConversionContext)
fun FirAnonymousFunction.valueMethod(name: ScopedName, analysis: FunctionUniquenessAnalysis?): LambdaValueMethod {
    val (signature, target) = symbol.toFunctionSignature().toNamedSignature(name).toNonInlineSignature(null)
        .toCompleteSignature(source) { userFunctionContract() }
    val shared = mutableListOf<VariableEmbedding>()
    val sinks = mutableListOf<VariableEmbedding>()
    val captureTypes = linkedMapOf<FirBasedSymbol<*>, ConeKotlinType>()
    capturedLocals.associateWithTo(captureTypes) { it.resolvedReturnType }
    captureTypes.putAll(capturedReceivers.mapValues { it.value.resolvedType })
    val captures = captureTypes.mapValues { (symbol, coneType) ->
        val type = converter.embedType(coneType)
        if (symbol is FirPropertySymbol && symbol.isVar) DetachedVarAccess(converter.freshAnonVar(type).also(sinks::add))
        else converter.freshAnonVar(type).also(shared::add)
    }
    val resolver = RootParameterResolver(converter, signature, signature.parameterVariables(symbol), symbol.label?.name, target)
    val context = MethodConverter(converter, signature, resolver, converter.scopeIndexProducer.getFresh(),
        OwnershipFrame.lambdaValue(analysis, captures.keys), captures = captures).statementCtxt()
    val entry = shared.map { Declare(it, context.unconstrainedValue(it.type)) } + sinks.map { Declare(it, null) }
    val body = context.convertImpureBody(this, signature, target, entry)
        ?: throw SnaktInternalException(source, "Lambda value has no body")
    return LambdaValueMethod(signature, target, body)
}

/** The lambdas a program creates as function values, whose bodies are verified separately. */
interface LambdaValueRegistry {
    /** The lambdas [registerLambdaValue] recorded, in the order it met them. */
    val lambdaValues: Set<FirAnonymousFunction>

    /** Sets the lambda bodies the checker can schedule separately for this program. */
    fun setSchedulableLambdaValues(functions: Set<FirAnonymousFunction>)

    /** Whether this program's checker can schedule the body of [function] separately. */
    fun schedulesLambdaValue(function: FirAnonymousFunction): Boolean

    /** Records that the conversion creates [function] as a function value. */
    fun registerLambdaValue(function: FirAnonymousFunction)
}

class LambdaValueSet : LambdaValueRegistry {
    override val lambdaValues = linkedSetOf<FirAnonymousFunction>()
    private var schedulable = emptySet<FirAnonymousFunction>()

    override fun setSchedulableLambdaValues(functions: Set<FirAnonymousFunction>) {
        schedulable = functions
    }

    override fun schedulesLambdaValue(function: FirAnonymousFunction): Boolean = function in schedulable

    override fun registerLambdaValue(function: FirAnonymousFunction) {
        lambdaValues += function
    }
}

data class LambdaValueMethod(val signature: NonInlineFunctionSignature, val target: ReturnTarget, val body: ConvertedMethodBody)

/**
 * Embeds the body of the lambda value [function], written in the declaration named [ownerName], as a method of its
 * own. [index] tells it apart from the other lambdas of that declaration. A lambda whose control flow extends its
 * creator's, one Kotlin marks as called in place, has no uniqueness analysis, and its body owns nothing.
 */
fun ProgramConverter.registerLambdaValueBody(function: FirAnonymousFunction, ownerName: ScopedName, index: Int) {
    val name = ScopedName(
        ClassScope(CallableScope(ownerName.scope, ownerName.name), ClassKotlinName(FqName("Lambda$index"))),
        PretypeName("invoke"),
    )
    val graph = function.controlFlowGraphReference?.controlFlowGraph
        ?: throw SnaktInternalException(function.source, "Lambda value has no control-flow graph")
    val outcome = if (graph.extendsLocalFlow) UniquenessOutcome(null, hasErrors = false) else uniquenessOutcomeOf(function)
    registerMethod(function, outcome.hasErrors) {
        with(this) { function.valueMethod(name, outcome.analysis) }
    }
}
