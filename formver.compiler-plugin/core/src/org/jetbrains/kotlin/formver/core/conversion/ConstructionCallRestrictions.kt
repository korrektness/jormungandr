/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.references.toResolvedConstructorSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.scopes.ScopeFunctionRequiresPrewarm
import org.jetbrains.kotlin.fir.scopes.getDirectOverriddenFunctions
import org.jetbrains.kotlin.fir.scopes.getDirectOverriddenProperties
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.isLaw
import org.jetbrains.kotlin.formver.core.isUnique
import org.jetbrains.kotlin.formver.readonly.plugin.isSpecificationCall

/** Checks construction-call shapes described in SPECIFICATIONS.md. */
context(program: ProgramConversionContext)
fun checkConstructionCalls(constructor: FirConstructorSymbol) {
    val owner = constructor.resolvedReturnType.toRegularClassSymbol(program.session) ?: return
    if (owner.origin != FirDeclarationOrigin.Source) return
    ConstructionCallChecker(program, constructor, owner).check()
}

private data class ConstructionInspection(
    val roots: MutableSet<FirBasedSymbol<*>>,
    val initialized: MutableSet<FirPropertySymbol>,
    val helper: Boolean = false,
    val call: FirElement? = null,
) {
    fun fork(): ConstructionInspection = copy(roots = roots.toMutableSet(), initialized = initialized.toMutableSet())

    fun merge(outcomes: List<ConstructionInspection>) {
        if (outcomes.isEmpty()) return
        initialized.clear()
        initialized.addAll(outcomes.map { it.initialized }.reduce { a, b -> a.intersect(b).toMutableSet() })
        roots.clear()
        roots.addAll(outcomes.flatMap { it.roots })
    }
}

@OptIn(SymbolInternals::class, DirectDeclarationsAccess::class, ScopeFunctionRequiresPrewarm::class)
private class ConstructionCallChecker(
    private val program: ProgramConversionContext,
    private val constructor: FirConstructorSymbol,
    owner: FirRegularClassSymbol,
) {
    private val hierarchy = linkedSetOf<FirRegularClassSymbol>()

    init {
        collect(owner)
    }

    private val fields = hierarchy.flatMap { it.fir.declarations.filterIsInstance<FirProperty>() }.map { it.symbol }.toSet()
    private val immutable = fields.filter { it.isVal && (it.fir.hasBackingField || it.fir.delegate != null) }.toSet()
    private val ready = mutableSetOf<FirPropertySymbol>()
    private val seenTypes = mutableSetOf<FirRegularClassSymbol>()
    private val hasNestedLaws = fields.any { field ->
        context(program.checkerContext) { field.isUnique() } && classes(field.resolvedReturnType).any(::containsLaw)
    }
    private val scope = context(program.checkerContext) { owner.unsubstitutedScope() }
    private val returnedRoots = mutableSetOf<FirElement>()
    private val active = mutableSetOf<FirFunctionSymbol<*>>()
    private val visited = mutableSetOf<FirConstructorSymbol>()

    fun check() = initialize(constructor)

    private fun collect(cls: FirRegularClassSymbol) {
        if (!hierarchy.add(cls)) return
        cls.resolvedSuperTypes.mapNotNull { it.toRegularClassSymbol(program.session) }.forEach(::collect)
    }

    private fun classes(type: ConeKotlinType): List<FirRegularClassSymbol> =
        type.toRegularClassSymbol(program.session)?.let(::listOf) ?: (type as? ConeTypeParameterType)?.lookupTag
            ?.typeParameterSymbol?.fir?.bounds?.flatMap { classes(it.coneType) }.orEmpty()

    private fun containsLaw(cls: FirRegularClassSymbol): Boolean {
        if (!seenTypes.add(cls)) return false
        if (cls.declarationSymbols.filterIsInstance<FirNamedFunctionSymbol>().any { it.isLaw(program.session) }) return true
        return cls.resolvedSuperTypes.mapNotNull { it.toRegularClassSymbol(program.session) }.any(::containsLaw) ||
            cls.fir.declarations.filterIsInstance<FirProperty>().any { property ->
                context(program.checkerContext) { property.symbol.isUnique() } &&
                    classes(property.symbol.resolvedReturnType).any(::containsLaw)
            }
    }

    private fun actualProperty(symbol: FirPropertySymbol): FirPropertySymbol {
        if (symbol.isFinal) return symbol.unwrapFakeOverrides()
        val candidates = mutableListOf<FirPropertySymbol>()
        scope.processPropertiesByName(symbol.name) { (it as? FirPropertySymbol)?.let(candidates::add) }
        val visited = mutableSetOf<FirPropertySymbol>()
        fun overrides(member: FirPropertySymbol): Boolean = visited.add(member) &&
            (member.unwrapFakeOverrides() == symbol.unwrapFakeOverrides() || scope.getDirectOverriddenProperties(member).any(::overrides))
        return candidates.firstOrNull(::overrides)?.unwrapFakeOverrides() ?: symbol.unwrapFakeOverrides()
    }

    private fun actualFunction(symbol: FirNamedFunctionSymbol): FirNamedFunctionSymbol {
        if (symbol.isFinal) return symbol.unwrapFakeOverrides()
        val candidates = mutableListOf<FirNamedFunctionSymbol>()
        scope.processFunctionsByName(symbol.name) { candidates += it }
        val visited = mutableSetOf<FirNamedFunctionSymbol>()
        fun overrides(member: FirNamedFunctionSymbol): Boolean = visited.add(member) &&
            (member.unwrapFakeOverrides() == symbol.unwrapFakeOverrides() || scope.getDirectOverriddenFunctions(member).any(::overrides))
        return candidates.firstOrNull(::overrides)?.unwrapFakeOverrides() ?: symbol.unwrapFakeOverrides()
    }

    private fun isRoot(expression: FirExpression?, roots: Set<FirBasedSymbol<*>>): Boolean {
        val value = expression?.unwrapArgument() ?: return false
        return value in returnedRoots || when (value) {
            is FirThisReceiverExpression -> (value.calleeReference.symbol as? FirBasedSymbol<*>) in roots
            is FirPropertyAccessExpression -> (value.toResolvedCallableSymbol() as? FirBasedSymbol<*>) in roots
            is FirTypeOperatorCall -> value.argumentList.arguments.any { isRoot(it, roots) }
            is FirCheckNotNullCall -> value.argumentList.arguments.any { isRoot(it, roots) }
            else -> false
        }
    }

    private fun reject(detail: String): Nothing = throw UnsupportedFeatureException(constructor.source,
        "$detail; see SPECIFICATIONS.md#class-laws")

    private fun inspect(element: FirElement, state: ConstructionInspection) {
        if (element.isSpecificationCall() && !state.helper) return
        when (element) {
            is FirWhenExpression -> inspectWhen(element, state)
            is FirTryExpression -> inspectTry(element, state)
            is FirLoop -> inspectLoop(element, state)
            is FirVariableAssignment -> inspectAssignment(element, state)
            else -> inspectOrdinary(element, state)
        }
    }

    private fun inspectWhen(element: FirWhenExpression, state: ConstructionInspection) {
        element.subjectVariable?.let { inspect(it, state) }
        val outcomes = element.branches.map { branch ->
            state.fork().also { branchState ->
                inspect(branch.condition, branchState)
                inspect(branch.result, branchState)
            }
        }
        state.merge(outcomes)
    }

    private fun inspectTry(element: FirTryExpression, state: ConstructionInspection) {
        val outcomes = (listOf(element.tryBlock) + element.catches.map { it.block }).map { block ->
            state.fork().also { inspect(block, it) }
        }
        state.merge(outcomes)
        element.finallyBlock?.let { inspect(it, state) }
    }

    private fun inspectLoop(element: FirLoop, state: ConstructionInspection) {
        element.acceptChildren(object : FirVisitorVoid() {
            override fun visitElement(child: FirElement) = inspect(child, state.fork())
        })
    }

    private fun inspectAssignment(element: FirVariableAssignment, state: ConstructionInspection) {
        inspect(element.rValue, state)
        val target = (element.lValue as? FirDesugaredAssignmentValueReferenceExpression)?.expressionRef?.value ?: element.lValue
        val access = target as? FirPropertyAccessExpression
        val local = access?.toResolvedCallableSymbol() as? FirPropertySymbol
        if (local?.isLocal == true) {
            if (isRoot(element.rValue, state.roots)) state.roots += local else state.roots -= local
        }
        if (access != null && isRoot(access.dispatchReceiver, state.roots)) {
            (access.toResolvedCallableSymbol() as? FirPropertySymbol)?.let { state.initialized += actualProperty(it) }
        }
    }

    private fun inspectOrdinary(element: FirElement, state: ConstructionInspection) {
        when (element) {
            is FirReturnExpression -> if (state.call != null && isRoot(element.result, state.roots)) returnedRoots += state.call
            is FirQualifiedAccessExpression -> inspectAccess(element, state)
        }
        element.acceptChildren(object : FirVisitorVoid() {
            override fun visitElement(child: FirElement) = inspect(child, state)
        })
        if (element is FirProperty && element.isLocal && isRoot(element.initializer, state.roots)) state.roots += element.symbol
    }

    private fun inspectAccess(element: FirQualifiedAccessExpression, state: ConstructionInspection) {
        val receiverRoot = isRoot(element.dispatchReceiver, state.roots)
        val property = (element.toResolvedCallableSymbol() as? FirPropertySymbol)?.let { if (receiverRoot) actualProperty(it) else it }
        val uninitializedRead = receiverRoot && property in immutable && property !in state.initialized
        if (state.helper && uninitializedRead) reject("Construction helper reads uninitialized `${property!!.name}`")
        val function = when (val symbol = element.toResolvedCallableSymbol()) {
            is FirNamedFunctionSymbol -> if (receiverRoot) actualFunction(symbol) else symbol
            is FirConstructorSymbol -> symbol
            is FirPropertySymbol -> property?.getterSymbol?.takeUnless { it.fir is FirDefaultPropertyGetter }
            else -> null
        }
        val arguments = (element as? FirFunctionCall)?.resolvedArgumentMapping.orEmpty()
        val rootParameters = arguments.filterKeys { isRoot(it, state.roots) }.values.map { it.symbol }
        val extensionRoot = isRoot(element.extensionReceiver, state.roots)
        val passesRoot = receiverRoot || extensionRoot || rootParameters.isNotEmpty()
        if (function != null && passesRoot) {
            inspectHelper(function, rootParameters, receiverRoot, extensionRoot, state, element)
        }
    }

    private fun inspectHelper(
        function: FirFunctionSymbol<*>,
        rootParameters: List<FirValueParameterSymbol>,
        receiverRoot: Boolean,
        extensionRoot: Boolean,
        state: ConstructionInspection,
        element: FirElement,
    ) {
        if (hasNestedLaws) reject("Construction call passes an object with law-bearing owned children")
        if (!active.add(function)) return
        val bindings = rootParameters.toMutableSet<FirBasedSymbol<*>>()
        if (receiverRoot) bindings.addAll(hierarchy)
        if (extensionRoot) function.receiverParameterSymbol?.let(bindings::add)
        val body = function.fir.body
        if (body == null && !state.initialized.containsAll(immutable)) reject("Construction call has unavailable immutable-storage reads")
        body?.let { inspect(it, ConstructionInspection(bindings, state.initialized.toMutableSet(), true, element)) }
        active.remove(function)
    }

    private fun initialize(symbol: FirConstructorSymbol) {
        if (!visited.add(symbol)) return
        val cls = symbol.resolvedReturnType.toRegularClassSymbol(program.session) ?: return
        if (cls.origin != FirDeclarationOrigin.Source) return
        val state = ConstructionInspection(hierarchy.toMutableSet<FirBasedSymbol<*>>(), ready)
        symbol.fir.delegatedConstructor?.let { delegation ->
            inspect(delegation, state)
            delegation.calleeReference.toResolvedConstructorSymbol()?.let(::initialize)
        }
        if (symbol.isPrimary) cls.fir.declarations.forEach { initializeDeclaration(it, state) }
        symbol.fir.body?.let { inspect(it, state) }
    }

    private fun initializeDeclaration(declaration: FirDeclaration, state: ConstructionInspection) {
        when (declaration) {
            is FirProperty -> {
                declaration.initializer?.let { inspect(it, state); ready += declaration.symbol }
                declaration.delegate?.let { inspect(it, state); ready += declaration.symbol }
            }
            is FirAnonymousInitializer -> declaration.body?.let { inspect(it, state) }
            else -> {}
        }
    }
}
