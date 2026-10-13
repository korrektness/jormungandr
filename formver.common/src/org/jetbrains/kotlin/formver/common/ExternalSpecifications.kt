/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.common

import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.scopes.ScopeFunctionRequiresPrewarm
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.fir.unwrapUseSiteSubstitutionOverrides
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private fun annotationId(name: String) = ClassId.topLevel(FqName("org.jetbrains.kotlin.formver.plugin.$name"))
val externalSpecId = annotationId("ExternalSpec")
val externalFunctionId = annotationId("ExternalFunction")
val externalMemberId = annotationId("ExternalMember")

/**
 * A binding failure attached to [symbol], a source declaration of the session whose registry found it, so that the
 * checkers of that session reach it.
 */
data class ExternalSpecificationError(val symbol: FirBasedSymbol<*>, val condition: KotlinCondition.ExternalSpec)

/** Session-local bindings; all analyses see the same resolved declarations. */
@OptIn(DirectDeclarationsAccess::class, SymbolInternals::class, ScopeFunctionRequiresPrewarm::class)
class ExternalSpecificationRegistry(session: FirSession, private val sources: ExternalSpecSources) : FirExtensionSessionComponent(session) {
    override val componentClass = ExternalSpecificationRegistry::class

    private val predicate = LookupPredicate.create {
        annotated(externalSpecId.asSingleFqName()) or annotated(externalFunctionId.asSingleFqName()) or
            annotated(externalMemberId.asSingleFqName())
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(predicate)
    }

    private val classes = linkedMapOf<FirRegularClassSymbol, FirRegularClassSymbol>()
    private val callables = linkedMapOf<FirCallableSymbol<*>, FirCallableSymbol<*>>()
    private val models = mutableSetOf<FirBasedSymbol<*>>()
    private val failures = mutableListOf<ExternalSpecificationError>()
    private var initialized = false

    val errors: List<ExternalSpecificationError>
        get() { initialize(); return failures }

    private val errorsBySymbol by lazy { errors.groupBy { it.symbol } }

    fun errorsOf(symbol: FirBasedSymbol<*>): List<ExternalSpecificationError> = errorsBySymbol[symbol].orEmpty()

    val assumptions: List<String>
        get() {
            initialize()
            return (classes.keys.map { it.classId.asSingleFqName().asString() } + callables.keys.map { symbol ->
                val parameters = (symbol as? FirFunctionSymbol<*>)?.valueParameterSymbols.orEmpty()
                    .joinToString(",") { it.resolvedReturnType.toString() }
                "${symbol.callableId}($parameters):${symbol.resolvedReturnType}"
            }).sorted()
        }

    fun modelClass(symbol: FirRegularClassSymbol): FirRegularClassSymbol? {
        initialize()
        return classes[symbol]
    }

    /** Whether a dependency receiver class has a bound extension contract. */
    fun hasExtensionModel(receiver: ConeKotlinType): Boolean {
        initialize()
        val receiverClass = receiver.classId ?: return false
        return callables.keys.any { it.resolvedReceiverTypeRef?.coneType?.classId == receiverClass }
    }

    fun isSpecificationFile(path: String?): Boolean = sources.isExternalSpec(path)

    fun isModel(symbol: FirBasedSymbol<*>): Boolean {
        initialize()
        return symbol in models
    }

    /**
     * The model bound to [symbol], or null when [symbol] is not bound. Binding matches callable kind, parameter count
     * and receiver presence, so a model has the same symbol kind as its target.
     */
    fun modelSymbol(symbol: FirBasedSymbol<*>): FirBasedSymbol<*>? {
        initialize()
        return when (symbol) {
            is FirValueParameterSymbol -> {
                val owner = symbol.containingDeclarationSymbol as? FirCallableSymbol<*> ?: return null
                val model = modelCallable(owner) ?: return null
                val index = (owner as FirFunctionSymbol<*>).valueParameterSymbols.indexOf(symbol)
                (model as FirFunctionSymbol<*>).valueParameterSymbols[index]
            }
            is FirReceiverParameterSymbol -> {
                val owner = symbol.containingDeclarationSymbol as? FirCallableSymbol<*> ?: return null
                modelCallable(owner)?.let { checkNotNull(it.receiverParameterSymbol) { "Bound model $it has no receiver." } }
            }
            is FirCallableSymbol<*> -> modelCallable(symbol)
            // Classes are bound through modelClass, whose models are interfaces rather than class substitutes.
            else -> null
        }
    }

    private fun modelCallable(symbol: FirCallableSymbol<*>): FirCallableSymbol<*>? =
        callables[symbol.unwrapUseSiteSubstitutionOverrides().unwrapFakeOverrides()]

    private fun FirBasedSymbol<*>.bindingName(id: ClassId): String? {
        // This lookup is shared by declarations carrying different binding annotations.
        val annotation = getAnnotationByClassId(id, session) ?: return null
        val name = annotation.getStringArgument(Name.identifier("target"), session)
        if (name == null) {
            fail(this, ExternalSpecRule.MISSING_TARGET_ARGUMENT, id.shortClassName.asString())
            return null
        }
        if (name.split('.').any { !Name.isValidIdentifier(it) }) {
            fail(this, ExternalSpecRule.INVALID_TARGET_NAME, name)
            return null
        }
        return name
    }

    private fun containerFile(symbol: FirBasedSymbol<*>): FirFile? = when (symbol) {
        is FirClassLikeSymbol<*> -> session.firProvider.getFirClassifierContainerFileIfAny(symbol)
        is FirCallableSymbol<*> -> session.firProvider.getFirCallableContainerFile(symbol)
        else -> null
    }

    private fun fail(symbol: FirBasedSymbol<*>, rule: ExternalSpecRule, target: String? = null) {
        val condition = KotlinCondition.ExternalSpec(rule, target)
        // Every failing symbol comes from this session's sources; one without a source would have no checker to report it.
        check(symbol.origin == FirDeclarationOrigin.Source && symbol.source != null) {
            "External specification error on a non-source declaration $symbol: $condition"
        }
        failures += ExternalSpecificationError(symbol, condition)
    }

    private fun initialize() {
        if (initialized) return
        initialized = true
        val declarations = session.predicateBasedProvider.getSymbolsByPredicate(predicate).filter { symbol ->
            val file = containerFile(symbol)
            if (file != null && sources.isExternalSpec(file)) true
            else { fail(symbol, ExternalSpecRule.OUTSIDE_SPECIFICATION_INPUTS, symbol.toString()); false }
        }
        bindClasses(declarations.filterIsInstance<FirRegularClassSymbol>())
        for (model in declarations.filterIsInstance<FirNamedFunctionSymbol>()) {
            val name = model.bindingName(externalFunctionId) ?: continue
            if (model.dispatchReceiverType != null) {
                fail(model, ExternalSpecRule.FUNCTION_NOT_TOP_LEVEL)
                continue
            }
            val fqName = FqName(name)
            val candidates = session.symbolProvider.getTopLevelFunctionSymbols(fqName.parent(), fqName.shortName())
                .filter { matches(model, it) }
            bind(model, candidates, name)
        }
        for (member in declarations.filterIsInstance<FirCallableSymbol<*>>()) {
            val owner = member.getContainingClassSymbol()
            if (member.getAnnotationByClassId(externalMemberId, session) != null &&
                owner?.getAnnotationByClassId(externalSpecId, session) == null) {
                fail(member, ExternalSpecRule.MEMBER_OUTSIDE_CLASS_MODEL)
            }
        }
        if (failures.isNotEmpty()) {
            classes.clear()
            callables.clear()
            models.clear()
        }
    }

    /** Installs a class binding only once it passes every check, so a rejected model binds no members. */
    private fun bindClasses(holders: List<FirRegularClassSymbol>) {
        val pending = linkedMapOf<FirRegularClassSymbol, FirRegularClassSymbol>()
        for (model in holders) {
            val name = model.bindingName(externalSpecId) ?: continue
            val candidates = resolveClasses(name)
            val target = candidates.singleOrNull()
            when {
                model.classKind != ClassKind.INTERFACE -> fail(model, ExternalSpecRule.CLASS_MODEL_NOT_INTERFACE)
                candidates.size > 1 -> fail(model, ExternalSpecRule.AMBIGUOUS_CLASS, name)
                target == null -> fail(model, ExternalSpecRule.UNRESOLVED_CLASS, name)
                target.origin == FirDeclarationOrigin.Source -> fail(model, ExternalSpecRule.SOURCE_TARGET, name)
                pending.containsKey(target) -> fail(model, ExternalSpecRule.DUPLICATE_CLASS, name)
                model.typeParameterSymbols.size != target.typeParameterSymbols.size -> fail(model, ExternalSpecRule.TYPE_PARAMETERS, name)
                else -> pending[target] = model
            }
        }
        // Bounds may mention any pending model, so they are compared once every candidate binding is known.
        classes.putAll(pending)
        val valid = pending.filter { (target, model) ->
            sameTypeParameters(model.typeParameterSymbols, target.typeParameterSymbols).also { same ->
                if (!same) fail(model, ExternalSpecRule.TYPE_PARAMETER_BOUNDS, target.classId.asString())
            }
        }
        classes.clear()
        classes.putAll(valid)
        models += valid.values
        for ((target, model) in valid) bindMembers(target, model)
    }

    private fun resolveClasses(name: String): List<FirRegularClassSymbol> {
        val candidates = mutableListOf<FirRegularClassSymbol>()
        val parts = name.split('.')
        for (split in parts.size - 1 downTo 0) {
            val id = ClassId(FqName(parts.take(split).joinToString(".")), FqName(parts.drop(split).joinToString(".")), false)
            // Each split probes a package/nested-class interpretation; the caller diagnoses the final candidate set.
            (session.symbolProvider.getClassLikeSymbolByClassId(id) as? FirRegularClassSymbol)?.let { candidates += it }
        }
        return candidates.distinct()
    }

    private fun bindMembers(target: FirRegularClassSymbol, model: FirRegularClassSymbol) {
        val scope = target.unsubstitutedScope(session, ScopeSession(), false, org.jetbrains.kotlin.fir.declarations.FirResolvePhase.BODY_RESOLVE)
        for (member in model.declarationSymbols) {
            if (member !is FirCallableSymbol<*>) {
                fail(member, ExternalSpecRule.UNSUPPORTED_MEMBER)
                continue
            }
            // Ghost observers belong to the model's verification view and have no dependency member.
            if (member.getAnnotationByClassId(annotationId("GhostObserver"), session) != null) {
                models += member
                continue
            }
            // Laws and contract holders are specification declarations, not dependency members.
            if (member.getAnnotationByClassId(annotationId("Law"), session) != null ||
                member.getAnnotationByClassId(annotationId("SpecOf"), session) != null) continue
            val name = Name.identifier(member.bindingName(externalMemberId) ?: member.name.asString())
            val candidates = mutableListOf<FirCallableSymbol<*>>()
            when (member) {
                is FirNamedFunctionSymbol -> scope.processFunctionsByName(name) { if (matches(member, it)) candidates += it }
                is FirPropertySymbol -> scope.processPropertiesByName(name) { if (it is FirPropertySymbol && matches(member, it)) candidates += it }
                else -> { fail(member, ExternalSpecRule.UNSUPPORTED_MEMBER); continue }
            }
            bind(member, candidates, "${target.classId}.$name")
        }
    }

    private fun bind(model: FirCallableSymbol<*>, candidates: List<FirCallableSymbol<*>>, name: String) {
        val target = candidates.singleOrNull()?.unwrapFakeOverrides()
        when {
            target == null -> fail(model, ExternalSpecRule.NO_COMPATIBLE_TARGET, name)
            target.origin == FirDeclarationOrigin.Source -> fail(model, ExternalSpecRule.SOURCE_TARGET, name)
            callables.containsKey(target) -> fail(model, ExternalSpecRule.DUPLICATE_MEMBER, name)
            else -> {
                callables[target] = model
                models += model
                if (target is FirPropertySymbol && model is FirPropertySymbol) {
                    // Field-only dependency properties have no accessor symbols; the property binding supplies metadata.
                    target.getterSymbol?.let { getter ->
                        val sourceGetter = checkNotNull(model.getterSymbol)
                        callables[getter] = sourceGetter
                        models += sourceGetter
                    }
                    target.setterSymbol?.let { setter ->
                        val sourceSetter = checkNotNull(model.setterSymbol)
                        callables[setter] = sourceSetter
                        models += sourceSetter
                    }
                }
            }
        }
    }

    private fun matches(model: FirCallableSymbol<*>, target: FirCallableSymbol<*>): Boolean {
        // Consumers substitute a model for its target symbol, so both must be the same kind of declaration.
        if (model::class != target::class) return false
        if (!sameTypeParameters(model.typeParameterSymbols, target.typeParameterSymbols)) return false
        if (model.contextParameterSymbols.isNotEmpty() || target.contextParameterSymbols.isNotEmpty()) return false
        val params = model.typeParameterSymbols.zip(target.typeParameterSymbols).toMap()
        if (!sameType(model.resolvedReturnType, target.resolvedReturnType, params)) return false
        val receiver = model.resolvedReceiverTypeRef?.coneType
        val targetReceiver = target.resolvedReceiverTypeRef?.coneType
        if ((receiver == null) != (targetReceiver == null)) return false
        if (receiver != null && !sameType(receiver, targetReceiver!!, params)) return false
        return when {
            model is FirNamedFunctionSymbol && target is FirNamedFunctionSymbol ->
                model.resolvedStatus.isSuspend == target.resolvedStatus.isSuspend &&
                    model.valueParameterSymbols.size == target.valueParameterSymbols.size &&
                    model.valueParameterSymbols.zip(target.valueParameterSymbols).all { (a, b) ->
                        a.isVararg == b.isVararg && sameType(a.resolvedReturnType, b.resolvedReturnType, params)
                    }
            model is FirPropertySymbol && target is FirPropertySymbol ->
                model.isVar == target.isVar &&
                    sameAccessorKind(model.getterSymbol, target.getterSymbol) &&
                    sameAccessorKind(model.setterSymbol, target.setterSymbol)
            else -> false
        }
    }

    // A field-only target has no accessor to bind; any target accessor needs a model accessor of the same kind.
    private fun sameAccessorKind(model: FirPropertyAccessorSymbol?, target: FirPropertyAccessorSymbol?): Boolean =
        target == null || model != null && model::class == target::class

    private fun sameTypeParameters(own: List<FirTypeParameterSymbol>, target: List<FirTypeParameterSymbol>): Boolean {
        if (own.size != target.size) return false
        val params = own.zip(target).toMap()
        return own.zip(target).all { (a, b) ->
            a.variance == b.variance && a.isReified == b.isReified &&
                a.resolvedBounds.size == b.resolvedBounds.size &&
                a.resolvedBounds.all { bound -> b.resolvedBounds.any { sameType(bound.coneType, it.coneType, params) } }
        }
    }

    // A failed signature comparison excludes a candidate; bind reports an empty or ambiguous candidate set.
    private fun sameType(a: ConeKotlinType, b: ConeKotlinType, params: Map<FirTypeParameterSymbol, FirTypeParameterSymbol>): Boolean {
        if (a.isMarkedNullable != b.isMarkedNullable) return false
        if (a is ConeTypeParameterType && b is ConeTypeParameterType) {
            val own = a.lookupTag.typeParameterSymbol
            val theirs = b.lookupTag.typeParameterSymbol
            if (params[own] == theirs || own == theirs) return true
            return classes.any { (target, model) ->
                model.typeParameterSymbols.indexOf(own).let { index -> index >= 0 && target.typeParameterSymbols.getOrNull(index) == theirs }
            }
        }
        val ownId = a.classId ?: return false
        val targetId = classes.entries.firstOrNull { it.value.classId == ownId }?.key?.classId ?: ownId
        if (targetId != b.classId || a.typeArguments.size != b.typeArguments.size) return false
        return a.typeArguments.zip(b.typeArguments).all { (own, theirs) ->
            if (own === ConeStarProjection || theirs === ConeStarProjection) own === theirs
            else own.kind == theirs.kind && sameType(own.type!!, theirs.type!!, params)
        }
    }
}

private val externalRegistryAccessor = FirSession.nullableSessionComponentAccessor<ExternalSpecificationRegistry>()
/** Sessions without this component have no external bindings to consume or diagnose. */
val FirSession.externalSpecifications: ExternalSpecificationRegistry? by externalRegistryAccessor

/** Loaded specification bodies describe trusted contracts and carry no implementation-checking obligations. */
fun FirSession.isExternalSpecificationFile(path: String?): Boolean = externalSpecifications?.isSpecificationFile(path) == true

/** The source declaration whose verification metadata applies to this dependency symbol; it has the same kind. */
fun <S : FirBasedSymbol<*>> S.externalSpecificationSymbol(session: FirSession): S {
    val model = session.externalSpecifications?.modelSymbol(this) ?: return this
    check(model::class == this::class) { "External model $model bound to $this is a different kind of declaration." }
    return this::class.java.cast(model)
}
