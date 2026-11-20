/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.types.isInt
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.formver.common.Failure
import org.jetbrains.kotlin.formver.common.FailureKind
import org.jetbrains.kotlin.formver.common.TerminationCondition
import org.jetbrains.kotlin.formver.common.TerminationRule
import org.jetbrains.kotlin.formver.core.embeddings.callables.NamedFunctionSignature
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.AssumedTerminating
import org.jetbrains.kotlin.formver.viper.ast.Domain
import org.jetbrains.kotlin.formver.viper.ast.Function
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.domains.TerminationDomains
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.isFormverFunctionNamed
import org.jetbrains.kotlin.formver.core.linearization.pureToViper
import org.jetbrains.kotlin.formver.viper.ast.Exp

/** The measures a `decreases` call gives a function or a loop, compared lexicographically. */
data class TerminationMeasure(val parts: List<Part>, val source: KtSourceElement?) {
    /** An `Int` is ordered as a natural number; an object by the nesting of its unique predicate. */
    enum class Kind { INT, OBJECT }

    data class Part(val measure: ExpEmbedding, val kind: Kind)

    /**
     * A nullable object is preceded by whether it is non-null. A call on `null` then decreases the measure without
     * reading the predicate instance, which needs permission to the predicate.
     */
    fun toViper(typeResolver: TypeResolver): Exp.DecreasesTuple = Exp.DecreasesTuple(parts.flatMap { part ->
        when (part.kind) {
            Kind.INT -> listOf(part.measure.pureToViper(toBuiltin = true, typeResolver))
            Kind.OBJECT -> {
                val reference = part.measure.pureToViper(toBuiltin = false, typeResolver)
                val instance = Exp.PredicateInstance((part.measure.type.pretype as ClassTypeEmbedding).uniquePredicateName,
                    listOf(reference), TerminationDomains.PredicateInstance.type)
                if (part.measure.type.isNullable) listOf(Exp.NeCmp(reference, RuntimeTypeDomain.nullValue()), instance)
                else listOf(instance)
            }
        }
    })

    /** The parts, all of kind [Kind.INT], as builtin integers. */
    fun intValues(typeResolver: TypeResolver): List<Exp> = parts.map { it.measure.pureToViper(toBuiltin = true, typeResolver) }
}

/**
 * Whether the integer measures [new] are lexicographically smaller than [old], bounded below by zero, over the
 * positions both have. Measures equal on those positions are not smaller.
 */
fun lexicographicDecrease(new: List<Exp>, old: List<Exp>): Exp =
    new.zip(old).foldRight<Pair<Exp, Exp>, Exp>(Exp.BoolLit(false)) { (n, o), rest ->
        Exp.Or(Exp.And(Exp.GeCmp(o, Exp.IntLit(0)), Exp.LtCmp(n, o)), Exp.And(Exp.EqCmp(n, o), rest))
    }

/**
 * The measure that [call], a `decreases` call, gives, converted in [context]. With [intOnly], every measure must be an
 * `Int`. An ill-formed measure is passed to [report], and the result is `null`.
 */
fun TypeResolver.convertMeasure(
    call: FirFunctionCall, context: StmtConversionContext, intOnly: Boolean,
    report: (KtSourceElement?, TerminationRule) -> Unit,
): TerminationMeasure? {
    val arguments = (call.arguments.singleOrNull() as? FirVarargArgumentsExpression)?.arguments.orEmpty()
    val parts = arguments.map { argument ->
        val measure = argument.accept(StmtConversionVisitor, context)
        val kind = when {
            argument.resolvedType.isInt -> TerminationMeasure.Kind.INT
            intOnly -> return null.also { report(argument.source, TerminationRule.INT_MEASURE) }
            (measure.type.pretype as? ClassTypeEmbedding)?.let { !isInterface(it) } == true -> TerminationMeasure.Kind.OBJECT
            else -> return null.also { report(argument.source, TerminationRule.MEASURE_TYPE) }
        }
        TerminationMeasure.Part(measure, kind)
    }
    return TerminationMeasure(parts, call.source)
}

/** The `decreases` call this statement makes, also as the result of a lambda. */
fun FirStatement.decreasesCall(): FirFunctionCall? {
    val call = (this as? FirReturnExpression)?.result ?: this
    if (call !is FirFunctionCall) return null
    val symbol = call.toResolvedCallableSymbol() as? FirFunctionSymbol<*> ?: return null
    return call.takeIf { symbol.isFormverFunctionNamed("decreases") }
}

/** The measures of a program's functions, and the Viper declarations the measures of `@Pure` functions need. */
internal class TerminationMeasures(private val typeResolver: TypeResolver) {
    private val byFunction = mutableMapOf<SymbolicName, TerminationMeasure>()

    val measures: Map<SymbolicName, TerminationMeasure>
        get() = byFunction

    private val cycles = mutableMapOf<SymbolicName, Set<FirFunctionSymbol<*>>>()

    /** The functions of the recursion cycle of the termination-checked function [name], whose calls must decrease its measure. */
    fun cycleOf(name: SymbolicName): Set<FirFunctionSymbol<*>> = cycles[name].orEmpty()

    private val checked = mutableSetOf<FirFunctionSymbol<*>>()

    /** Whether [symbol] is termination-checked, as [checkCalls] found. */
    fun isChecked(symbol: FirFunctionSymbol<*>?): Boolean = symbol in checked

    /**
     * Makes [symbol], with [signature], termination-checked when it has a measure and is not `@Pure`: records its
     * recursion cycle, and passes each call it cannot show to terminate to [report].
     */
    fun checkCalls(
        signature: NamedFunctionSignature, symbol: FirFunctionSymbol<*>, session: FirSession, trust: TrustedAssumptionCollector,
        report: (Failure) -> Unit,
    ) {
        if (signature.name !in byFunction || signature.isPure) return
        checked += symbol
        cycles[signature.name] = TerminationGraph(session, trust::recordAssumedTermination) { trust.recordOptOut(it) }.cycleOf(symbol) { source, rule, name ->
            report(Failure(FailureKind.TERMINATION_NOT_ESTABLISHED, source, TerminationCondition(rule, listOfNotNull(name))))
        }
    }

    /**
     * Records the measure of [calls], the `decreases` calls among the preconditions of [signature]. A function that is
     * not `@Pure` takes only `Int` measures. An ill-formed measure is passed to [report] and treated as absent.
     */
    fun embed(
        signature: NamedFunctionSignature, calls: List<FirFunctionCall>, context: StmtConversionContext,
        report: (KtSourceElement?, TerminationRule) -> Unit,
    ) {
        val call = calls.firstOrNull() ?: return
        if (calls.size > 1) return report(calls[1].source, TerminationRule.DUPLICATE)
        typeResolver.convertMeasure(call, context, intOnly = !signature.isPure, report)?.let { byFunction[signature.name] = it }
    }

    companion object {
        /** The orders that the measures of [functions] use. */
        fun domains(functions: Collection<Function>): List<Domain> {
            val types = functions.flatMap { it.pres }.filterIsInstance<Exp.DecreasesTuple>()
                .flatMap { it.measures }.map { it.type }.toSet()
            return TerminationDomains.required(types)
        }

        /**
         * When a function has a measure, Silver's termination plugin fails every call it makes to a function without
         * a termination specification. The others are assumed to terminate: none of them is recursive with a
         * definition.
         */
        fun withTerminationSpecifications(functions: List<Function>): List<Function> {
            fun Function.measured() = pres.any { it is Exp.DecreasesTuple }
            if (functions.none { it.measured() }) return functions
            return functions.map { if (it.measured()) it else AssumedTerminating(it) }
        }
    }
}
