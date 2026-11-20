/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirDelegatedConstructorCall
import org.jetbrains.kotlin.fir.expressions.FirDoWhileLoop
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.FirWhileLoop
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.toResolvedConstructorSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.isSomeFunctionType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.common.TerminationRule
import org.jetbrains.kotlin.formver.common.libraryOrigins
import org.jetbrains.kotlin.formver.core.isPure
import org.jetbrains.kotlin.formver.core.shouldBeInlined
import org.jetbrains.kotlin.fir.declarations.FirBackingField
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.isInt
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirEnumEntrySymbol
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirArrayLiteral
import org.jetbrains.kotlin.fir.expressions.FirBooleanOperatorExpression
import org.jetbrains.kotlin.fir.expressions.FirBreakExpression
import org.jetbrains.kotlin.fir.expressions.FirCheckNotNullCall
import org.jetbrains.kotlin.fir.expressions.FirCheckedSafeCallSubject
import org.jetbrains.kotlin.fir.expressions.FirClassReferenceExpression
import org.jetbrains.kotlin.fir.expressions.FirComparisonExpression
import org.jetbrains.kotlin.fir.expressions.FirContinueExpression
import org.jetbrains.kotlin.fir.expressions.FirDesugaredAssignmentValueReferenceExpression
import org.jetbrains.kotlin.fir.expressions.FirElvisExpression
import org.jetbrains.kotlin.fir.expressions.FirEqualityOperatorCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirOperation
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirSafeCallExpression
import org.jetbrains.kotlin.fir.expressions.FirSamConversionExpression
import org.jetbrains.kotlin.fir.expressions.FirSmartCastExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirStringConcatenationCall
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.FirThrowExpression
import org.jetbrains.kotlin.fir.expressions.FirTryExpression
import org.jetbrains.kotlin.fir.expressions.FirTypeOperatorCall
import org.jetbrains.kotlin.fir.expressions.impl.FirUnitExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.expressions.FirWhenSubjectExpression
import org.jetbrains.kotlin.fir.expressions.FirWrappedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.isPrimitiveOrNullablePrimitive
import org.jetbrains.kotlin.fir.types.isString
import org.jetbrains.kotlin.fir.types.isNullableString
import org.jetbrains.kotlin.formver.core.names.SpecialPackages
import org.jetbrains.kotlin.types.ConstantValueKind

/**
 * Whether source functions terminate, judged from their FIR (SPECIFICATIONS.md, "Termination of functions and loops").
 * The traversal visits every element of a body, and of the initializers a constructor runs. An element that can run code
 * and is not among the kinds it handles is rejected, as is a source function without analysable facts. [assume] is the
 * only place that assumes a library callee terminates; it records the assumption through [assumed]. [rely] is the only
 * place that relies on the verification of a measured source callee; it records it through [relied], which the trust
 * report lists when that verification is skipped.
 */
@OptIn(SymbolInternals::class)
internal class TerminationGraph(
    private val session: FirSession,
    private val assumed: (FirCallableSymbol<*>) -> Unit,
    private val relied: (FirCallableSymbol<*>) -> Unit,
) {
    /** A call of [callee] in a body; [inLambda] when a lambda makes it. */
    data class Call(val callee: FirFunctionSymbol<*>, val source: KtSourceElement?, val inLambda: Boolean = false)

    /** An element of kind [kind] that the analysis does not handle. */
    data class Rejection(val kind: String, val source: KtSourceElement?)

    private data class Facts(val calls: List<Call>, val loops: Boolean, val rejections: List<Rejection>)

    private val facts = mutableMapOf<FirFunctionSymbol<*>, Facts?>()
    private val terminating = mutableMapOf<FirFunctionSymbol<*>, Boolean>()
    private val containerFacts = mutableMapOf<FirElement, Facts>()

    /** The calls in the body of [symbol], or `null` when it has no analysable source body. */
    fun calls(symbol: FirFunctionSymbol<*>): List<Call>? = facts(symbol)?.calls

    /**
     * Whether [symbol] has a well-formed measure: one `decreases` among its preconditions, whose measures are `Int`s
     * unless it is `@Pure`.
     */
    fun hasMeasure(symbol: FirFunctionSymbol<*>): Boolean {
        val calls = (symbol.original().fir as? FirSimpleFunction)?.userSpecification(session)?.spec?.precond?.statements
            ?.mapNotNull { it.decreasesCall() }.orEmpty()
        val measures = (calls.singleOrNull() ?: return false).argumentList.arguments
            .flatMap { (it as? FirVarargArgumentsExpression)?.arguments ?: listOf(it) }
        return symbol.isPure(session) || measures.all { it.resolvedType.isInt }
    }

    /** Whether [from] reaches [to] through calls of source functions. */
    fun reaches(from: FirFunctionSymbol<*>, to: FirFunctionSymbol<*>): Boolean {
        val target = to.original()
        val seen = mutableSetOf<FirFunctionSymbol<*>>()
        val queue = ArrayDeque(listOf(from.original()))
        while (queue.isNotEmpty()) {
            for (call in calls(queue.removeFirst()).orEmpty()) {
                val callee = call.callee.original()
                if (callee == target) return true
                if (seen.add(callee)) queue.addLast(callee)
            }
        }
        return false
    }

    private fun assume(symbol: FirCallableSymbol<*>): Boolean {
        assumed(symbol)
        return true
    }

    private fun rely(symbol: FirCallableSymbol<*>): Boolean {
        relied(symbol)
        return true
    }

    /**
     * Whether [symbol] is known to terminate. A library function is assumed to. A source member that may be overridden
     * is not, nor is a source function without analysable facts, nor one whose file or object initializers, which
     * running it may trigger, are not known to terminate. Any other source function terminates when it is `@Pure`, has
     * a measure, which its own verification checks, or is not recursive, has no loop other than a `for` over a range,
     * contains no rejected element and calls only functions known to terminate.
     */
    fun terminates(symbol: FirFunctionSymbol<*>): Boolean = terminates(symbol.original(), mutableSetOf())

    private fun terminates(symbol: FirFunctionSymbol<*>, open: MutableSet<FirFunctionSymbol<*>>): Boolean {
        terminating[symbol]?.let { return it }
        if (symbol.origin in libraryOrigins) return assume(symbol)
        if (symbol.origin != FirDeclarationOrigin.Source) return false
        if (symbol.isOverridable()) return false
        val facts = facts(symbol) ?: return false
        if (!open.add(symbol)) return false
        val result = container(symbol)?.let { holds(it, open) } != false &&
            (symbol.isPure(session) || hasMeasure(symbol) && rely(symbol) || holds(facts, open))
        open.remove(symbol)
        return result.also { terminating[symbol] = it }
    }

    private fun holds(facts: Facts, open: MutableSet<FirFunctionSymbol<*>>): Boolean =
        !facts.loops && facts.rejections.isEmpty() && facts.calls.all { terminates(it.callee.original(), open) }

    /**
     * The functions of the recursion cycle of [symbol], a termination-checked function, whose calls must decrease its
     * measure. Each call in its body that it cannot show to terminate is passed to [report] with the broken rule, and
     * each rejected element with [TerminationRule.UNHANDLED].
     */
    fun cycleOf(symbol: FirFunctionSymbol<*>, report: (KtSourceElement?, TerminationRule, String?) -> Unit): Set<FirFunctionSymbol<*>> =
        buildSet {
            facts(symbol)?.rejections.orEmpty().forEach { report(it.source, TerminationRule.UNHANDLED, it.kind) }
            if (container(symbol.original())?.let { holds(it, mutableSetOf(symbol.original())) } == false) {
                report(symbol.source, TerminationRule.INITIALIZERS, null)
            }
            for (call in calls(symbol).orEmpty()) {
                val callee = call.callee
                val name = ((callee as? FirPropertyAccessorSymbol)?.propertySymbol?.name ?: callee.name).asString()
                when {
                    callee.original().isOverridable() -> report(call.source, TerminationRule.CALLEE, name)
                    reaches(callee, symbol) -> when {
                        call.inLambda -> report(call.source, TerminationRule.CYCLE_IN_LAMBDA, name)
                        !hasMeasure(callee) -> report(call.source, TerminationRule.UNMEASURED_CYCLE, name)
                        callee.shouldBeInlined -> report(call.source, TerminationRule.CALLEE, name)
                        container(callee.original())?.let { holds(it, mutableSetOf(symbol.original())) } == false ->
                            report(call.source, TerminationRule.CALLEE, name)
                        else -> add(callee.original()).also { rely(callee) }
                    }
                    !terminates(callee) -> report(call.source, TerminationRule.CALLEE, name)
                }
            }
        }

    private fun FirFunctionSymbol<*>.original(): FirFunctionSymbol<*> = unwrapFakeOverrides()

    private fun FirFunctionSymbol<*>.isOverridable(): Boolean =
        origin == FirDeclarationOrigin.Source && dispatchReceiverType != null && modality != Modality.FINAL

    private fun facts(symbol: FirFunctionSymbol<*>): Facts? = facts.getOrPut(symbol) {
        val declaration = symbol.fir
        when {
            declaration.origin != FirDeclarationOrigin.Source -> null
            declaration is FirDefaultPropertyAccessor -> Facts(emptyList(), false, emptyList())
            declaration.body == null && declaration !is FirConstructor -> null
            else -> collect(listOfNotNull(declaration.body, (declaration as? FirConstructor)?.delegatedConstructor) +
                initializers(declaration) + declaration.valueParameters.mapNotNull { it.defaultValue })
        }
    }

    /**
     * The facts of the initializers that running [symbol] may trigger: those of its file when it is top-level, of its
     * object when it is an object member, or of its class's companion object when it is a constructor; `null` when
     * there are none.
     */
    @OptIn(DirectDeclarationsAccess::class)
    private fun container(symbol: FirFunctionSymbol<*>): Facts? {
        val owner = (symbol as? FirPropertyAccessorSymbol)?.propertySymbol ?: symbol
        val declarations = when {
            owner is FirConstructorSymbol -> owner.resolvedReturnType.toRegularClassSymbol(session)?.companionObjectSymbol?.fir?.declarations
            owner.callableId?.classId == null -> session.firProvider.getFirCallableContainerFile(owner)?.declarations
            else -> owner.dispatchReceiverType?.toRegularClassSymbol(session)?.takeIf { it.classKind == ClassKind.OBJECT }?.fir?.declarations
        } ?: return null
        val container = declarations.firstOrNull() ?: return null
        return containerFacts.getOrPut(container) { collect(declarations.mapNotNull { it.initialization() }) }
    }

    private fun FirDeclaration.initialization(): FirElement? = when (this) {
        is FirAnonymousInitializer -> body
        is FirProperty -> initializer ?: delegate
        else -> null
    }

    @OptIn(DirectDeclarationsAccess::class)
    private fun initializers(declaration: FirFunction): List<FirElement> {
        if (declaration !is FirConstructor) return emptyList()
        val owner = declaration.symbol.resolvedReturnType.toRegularClassSymbol(session)?.fir ?: return emptyList()
        return owner.declarations.mapNotNull { it.initialization() }
    }

    private val ghostBlocks = setOf("preconditions", "postconditions", "loopInvariants", "decreases")

    private fun FirExpression.isClosed(): Boolean {
        val value = (this as? FirWrappedArgumentExpression)?.expression ?: this
        if (value is FirAnonymousFunctionExpression || value.isBuiltinValue()) return true
        return value.resolvedType.isClosed()
    }

    private fun ConeKotlinType.isClosed(): Boolean {
        val type = lowerBoundIfFlexible()
        if (type.isPrimitiveOrNullablePrimitive || type.isString || type.isNullableString) return true
        val symbol = type.toRegularClassSymbol(session) ?: return false
        return symbol.origin in libraryOrigins && symbol.modality == Modality.FINAL && symbol.classKind != ClassKind.INTERFACE &&
            type.typeArguments.all { it.type?.isClosed() == true }
    }

    private fun FirFunctionSymbol<*>.isSpecification(): Boolean =
        callableId?.packageName?.pathSegments()?.map { it.asString() } == SpecialPackages.formver

    private fun FirExpression.isNullLiteral(): Boolean = this is FirLiteralExpression && kind == ConstantValueKind.Null

    private fun FirExpression.isBuiltinValue(): Boolean =
        isNullLiteral() || resolvedType.isPrimitiveOrNullablePrimitive || resolvedType.isString || resolvedType.isNullableString

    private fun FirElement.isInert(): Boolean = this !is FirStatement || when (this) {
        is FirLiteralExpression, is FirBlock, is FirWhenExpression, is FirWhenSubjectExpression, is FirReturnExpression,
        is FirBreakExpression, is FirContinueExpression, is FirThrowExpression, is FirTryExpression, is FirElvisExpression,
        is FirSafeCallExpression, is FirCheckedSafeCallSubject, is FirSmartCastExpression, is FirTypeOperatorCall,
        is FirCheckNotNullCall, is FirBooleanOperatorExpression, is FirComparisonExpression, is FirValueParameter,
        is FirThisReceiverExpression, is FirWrappedArgumentExpression,
        is FirVarargArgumentsExpression, is FirDesugaredAssignmentValueReferenceExpression, is FirArrayLiteral,
        is FirGetClassCall, is FirClassReferenceExpression, is FirAnonymousInitializer, is FirAnnotation,
        is FirSimpleFunction, is FirAnonymousFunction, is FirAnonymousFunctionExpression, is FirSamConversionExpression,
        is FirUnitExpression, is FirBackingField -> true
        else -> false
    }

    private fun collect(roots: List<FirElement>): Facts {
        val calls = mutableListOf<Call>()
        val rejections = mutableListOf<Rejection>()
        val rangeLoops = mutableSetOf<FirWhileLoop>()
        var loops = false
        var lambdas = 0
        fun reject(kind: String, element: FirElement) {
            rejections += Rejection(kind, element.source)
        }
        val rangeIterators = mutableSetOf<FirBasedSymbol<*>>()
        fun FirExpression.isRangeIterator(): Boolean {
            val symbol: FirBasedSymbol<*> = (this as? FirQualifiedAccessExpression)?.toResolvedCallableSymbol() ?: return false
            return symbol in rangeIterators
        }
        fun callOf(symbol: FirFunctionSymbol<*>?, element: FirElement, values: List<FirExpression>, constructsSource: Boolean = false) {
            if (symbol == null) return
            if (symbol.original().origin in libraryOrigins) {
                if (constructsSource && symbol.callableId?.classId != StandardClassIds.Any) {
                    return reject("library superclass constructor", element)
                }
                values.firstOrNull { !it.isClosed() && !it.isRangeIterator() }
                    ?.let { return reject("library call with a value of type ${it.resolvedType}", element) }
            }
            calls += Call(symbol, element.source, lambdas > 0)
        }
        val visitor = object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                when (element) {
                    is FirBlock -> element.asForRangeLoop()?.let {
                        rangeLoops += it.loop
                        rangeIterators += (element.statements.first() as FirProperty).symbol
                    }
                    is FirWhileLoop, is FirDoWhileLoop -> if (element !in rangeLoops) {
                        loops = true
                        if (lambdas > 0) reject("loop in a lambda", element)
                    }
                    is FirFunctionCall -> {
                        val symbol = element.toResolvedCallableSymbol() as? FirFunctionSymbol<*>
                        when {
                            symbol == null -> reject("unresolved call", element)
                            symbol.isSpecification() -> if (symbol.name.asString() in ghostBlocks) return
                            element.resolvedType.isSomeFunctionType(session) -> reject("function value", element)
                            else -> callOf(symbol, element, element.values() + element.argumentList.arguments)
                        }
                    }
                    is FirResolvedQualifier -> element.symbol?.let { qualified ->
                        if (qualified is FirRegularClassSymbol && qualified.classKind == ClassKind.OBJECT) objectAccess(qualified, element)
                    }
                    is FirDelegatedConstructorCall -> {
                        val constructor = element.calleeReference.toResolvedConstructorSymbol()
                        if (constructor == null) reject("unresolved call", element)
                        callOf(
                            constructor, element,
                            listOfNotNull(element.dispatchReceiver) + element.contextArguments + element.argumentList.arguments,
                            constructsSource = true,
                        )
                    }
                    is FirPropertyAccessExpression -> {
                        val accessed = element.toResolvedCallableSymbol()
                        when {
                            element.resolvedType.isSomeFunctionType(session) -> reject("function value", element)
                            accessed is FirEnumEntrySymbol -> if (accessed.origin == FirDeclarationOrigin.Source) reject("enum entry", element)
                            else -> accessor(accessed as? FirPropertySymbol, element, element.values()) { it.getterSymbol }
                        }
                    }
                    is FirVariableAssignment -> {
                        val target = element.lValue as? FirQualifiedAccessExpression
                        accessor(
                            element.lValue.toResolvedCallableSymbol(session) as? FirPropertySymbol, element,
                            target?.values().orEmpty() + element.rValue,
                        ) { it.setterSymbol }
                        if (target != null) {
                            target.values().forEach { it.accept(this) }
                            element.rValue.accept(this)
                            return
                        }
                    }
                    is FirProperty -> if (element.delegate != null) reject("delegated property", element)
                    is FirCallableReferenceAccess -> reject("callable reference", element)
                    is FirEqualityOperatorCall -> if (element.operation in setOf(FirOperation.EQ, FirOperation.NOT_EQ) &&
                        !element.argumentList.arguments.any { it.isNullLiteral() } && !element.argumentList.arguments.all { it.isBuiltinValue() }
                    ) reject("equality of class instances", element)
                    is FirStringConcatenationCall ->
                        if (!element.argumentList.arguments.all { it.isBuiltinValue() }) reject("string template of class instances", element)
                    else -> if (!element.isInert()) reject(element::class.simpleName.orEmpty().removePrefix("Fir").removeSuffix("Impl"), element)
                }
                if (element is FirAnonymousFunction) lambdas++
                element.acceptChildren(this)
                if (element is FirAnonymousFunction) lambdas--
            }

            private fun objectAccess(objectClass: FirRegularClassSymbol, element: FirElement) {
                @OptIn(DirectDeclarationsAccess::class)
                if (objectClass.origin == FirDeclarationOrigin.Source && objectClass.fir.declarations.any { it.initialization() != null }) {
                    reject("access of an object with initializers", element)
                }
            }

            private fun accessor(
                property: FirPropertySymbol?, element: FirElement, values: List<FirExpression>, select: (FirPropertySymbol) -> FirFunctionSymbol<*>?,
            ) {
                if (property == null) return
                if (property.fir.delegate != null) return reject("delegated property", element)
                callOf(select(property), element, values)
            }

            private fun FirQualifiedAccessExpression.values(): List<FirExpression> =
                listOfNotNull(dispatchReceiver, extensionReceiver) + contextArguments
        }
        roots.forEach { it.accept(visitor) }
        return Facts(calls, loops, rejections)
    }
}
