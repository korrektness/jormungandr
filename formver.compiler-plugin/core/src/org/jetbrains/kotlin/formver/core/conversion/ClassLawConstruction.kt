/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.references.toResolvedConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.core.isLaw
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.common.KotlinCondition
import org.jetbrains.kotlin.formver.common.LawRule
import org.jetbrains.kotlin.formver.core.isPure
import org.jetbrains.kotlin.formver.core.isUnique
import org.jetbrains.kotlin.formver.core.embeddings.callables.NonInlineCallable
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.properties.BackingFieldGetter
import org.jetbrains.kotlin.formver.core.names.DispatchReceiverName

@OptIn(DirectDeclarationsAccess::class)
fun FirRegularClassSymbol.ownClassLaws(session: FirSession): List<FirNamedFunctionSymbol> =
    if (classKind.isInterface) emptyList() else declarationSymbols.filterIsInstance<FirNamedFunctionSymbol>().filter { it.isLaw(session) }

/** Whether a superclass contributes a representation law. */
fun FirRegularClassSymbol.hasSuperclassLaws(session: FirSession): Boolean {
    val visited = mutableSetOf<FirRegularClassSymbol>()
    fun visit(symbol: FirRegularClassSymbol): Boolean = visited.add(symbol) &&
            (symbol.ownClassLaws(session).isNotEmpty() || symbol.resolvedSuperTypes.any {
                it.toRegularClassSymbol(session)?.let(::visit) == true
            })
    return resolvedSuperTypes.any { it.toRegularClassSymbol(session)?.let(::visit) == true }
}

fun FirQualifiedAccessExpression.isLawValueOperation(): Boolean = toResolvedCallableSymbol()?.callableId?.let {
    val classes = setOf("Boolean", "Byte", "Short", "Int", "Long", "Float", "Double", "Char", "String")
    val operations = setOf(
        "plus", "minus", "times", "div", "rem", "unaryMinus", "unaryPlus", "not", "compareTo", "equals", "get",
        "and", "or", "xor", "inv", "shl", "shr", "ushr",
        "toByte", "toShort", "toInt", "toLong", "toFloat", "toDouble", "toChar",
    )
    it.packageName.asString() == "kotlin" &&
            ((this is FirFunctionCall && it.className?.asString() in classes && it.callableName.asString() in operations) ||
                    (it.className?.asString() == "String" && it.callableName.asString() == "length") ||
                    (it.className?.asString() == "IntArray" &&
                            it.callableName.asString() == "size"))
} == true

private fun FirQualifiedAccessExpression.isAllowedInitializerAccess(
    parameters: Set<FirValueParameterSymbol>, session: FirSession,
): Boolean = when (val callee = toResolvedCallableSymbol()) {
    is FirValueParameterSymbol -> callee in parameters
    is FirPropertySymbol -> callee.resolvedStatus.isConst || isLawValueOperation()
    else -> isLawValueOperation() || (this is FirFunctionCall && callee?.isPure(session) == true)
}

@OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
private fun ownClassLawConstructionFailures(symbol: FirRegularClassSymbol): List<KotlinCondition.Law> {
    val failures = mutableListOf<KotlinCondition.Law>()
    val constructors = symbol.declarationSymbols.filterIsInstance<FirConstructorSymbol>()
    val primary = constructors.singleOrNull { it.isPrimary }
    if (primary == null) failures += KotlinCondition.Law(LawRule.PRIMARY_CONSTRUCTOR, symbol)
    constructors.filter { !it.isPrimary }.forEach {
        failures += KotlinCondition.Law(LawRule.SECONDARY_CONSTRUCTOR, it)
    }
    symbol.fir.declarations.filterIsInstance<FirProperty>().forEach { property ->
        if (property.delegate != null) failures += KotlinCondition.Law(LawRule.DELEGATED_PROPERTY, property.symbol)
    }
    return failures
}

fun classLawConstructionFailures(symbol: FirRegularClassSymbol, session: FirSession): List<KotlinCondition.Law> {
    val failures = mutableListOf<KotlinCondition.Law>()
    val visited = mutableSetOf<FirRegularClassSymbol>()
    fun visit(current: FirRegularClassSymbol) {
        if (!visited.add(current)) return
        if (current.ownClassLaws(session).isNotEmpty() || current.hasSuperclassLaws(session)) {
            failures += ownClassLawConstructionFailures(current)
        }
        current.resolvedSuperTypes.mapNotNull { it.toRegularClassSymbol(session) }
            .filter { !it.classKind.isInterface && it.origin == FirDeclarationOrigin.Source }
            .forEach(::visit)
    }
    visit(symbol)
    return failures.distinct()
}

context(program: ProgramConversionContext)
private fun isStableInitializer(initializer: FirExpression, parameters: Set<FirValueParameterSymbol>): Boolean {
    var stable = true
    initializer.accept(object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            val hasClosure = element is FirAnonymousFunctionExpression || element is FirSimpleFunction
            val unsupportedAccess = element is FirQualifiedAccessExpression &&
                !element.isAllowedInitializerAccess(parameters, program.session)
            val hasStateAccess = element is FirVariableAssignment || element is FirThisReceiverExpression
            if (hasStateAccess || hasClosure || unsupportedAccess) stable = false
            element.acceptChildren(this)
        }
    })
    return stable
}

/** Stable initializer equalities and checks for inherited law-field substitution. */
@OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
context(program: ProgramConversionContext)
fun constructorInitializerConditions(
    symbol: FirConstructorSymbol, signature: NonInlineCallable, result: ExpEmbedding,
): List<ExpEmbedding> {
    val owner = symbol.resolvedReturnType.toRegularClassSymbol(program.session) ?: return emptyList()
    if (owner.ownClassLaws(program.session).isEmpty() && !owner.hasSuperclassLaws(program.session)) {
        return emptyList()
    }
    val receiver = PlaceholderVariableEmbedding(DispatchReceiverName, program.embedType(symbol.resolvedReturnType), isUnique = true)
    val substitutions = symbol.valueParameterSymbols.zip(signature.params).associate { (symbol, value) ->
        SubstitutedArgument.ValueParameter(symbol) to value
    } + (SubstitutedArgument.DispatchThis to receiver)
    val fields = linkedMapOf<FirPropertySymbol, ExpEmbedding>()
    val initializers = linkedMapOf<FirPropertySymbol, () -> ExpEmbedding>()
    val writes = owner.constructionWrites(program.session)
    val lawOwners = mutableListOf<FirRegularClassSymbol>()
    fun collect(constructor: FirConstructorSymbol, arguments: Map<SubstitutedArgument, ExpEmbedding>) {
        val declaringClass = constructor.resolvedReturnType.toRegularClassSymbol(program.session) ?: return
        val target = program.returnTargetProducer.getFresh(signature.callableType.returnType)
        val resolver = InlineParameterResolver(arguments, null, target)
        val context = MethodConverter(
            program, signature, resolver, program.scopeIndexProducer.getFresh(), OwnershipFrame.root(null, emptyList()),
        ).statementCtxt()
        val delegation = constructor.fir.delegatedConstructor
        val superConstructor = delegation?.calleeReference?.toResolvedConstructorSymbol()
        if (superConstructor != null) {
            val superArguments = delegation.resolvedArgumentMapping.orEmpty().map { (argument, parameter) ->
                SubstitutedArgument.ValueParameter(parameter.symbol) to context.convert(argument)
            }.toMap().toMutableMap<SubstitutedArgument, ExpEmbedding>()
            superArguments[SubstitutedArgument.DispatchThis] = receiver
            for (parameter in superConstructor.valueParameterSymbols) {
                val key = SubstitutedArgument.ValueParameter(parameter)
                if (key in superArguments) continue
                val defaultValue = parameter.fir.defaultValue ?: error("Missing superclass constructor argument")
                val defaultContext = MethodConverter(program, signature,
                    InlineParameterResolver(superArguments.toMap(), null, target),
                    program.scopeIndexProducer.getFresh(), OwnershipFrame.root(null, emptyList()),
                ).statementCtxt()
                superArguments[key] = defaultContext.convert(defaultValue)
            }
            collect(superConstructor, superArguments)
        }
        declaringClass.fir.declarations.filterIsInstance<FirProperty>().forEach { property ->
            property.initializer?.let { initializer ->
                if (!isStableInitializer(initializer, constructor.valueParameterSymbols.toSet())) return@let
                initializers[property.symbol] = {
                    val nestedStateMayChange = context(program.checkerContext) { property.symbol.isUnique() } &&
                        writes.mayReach(property.symbol)
                    if (property.symbol.getContainingClassSymbol() != owner &&
                        (writes.mayChange(property.symbol) || nestedStateMayChange)) {
                        throw UnsupportedFeatureException(property.source, "a class-law field changed during construction")
                    }
                    context.convert(initializer)
                }
            }
        }
        if (declaringClass.ownClassLaws(program.session).isNotEmpty()) lawOwners += declaringClass
    }
    collect(symbol, substitutions)
    fun initializedField(property: FirPropertySymbol): ExpEmbedding? =
        fields[property] ?: initializers[property]?.invoke()?.also { fields[property] = it }
    owner.fir.declarations.filterIsInstance<FirProperty>().forEach { initializedField(it.symbol) }
    for (declaringClass in lawOwners) {
        val expansion = ClassLawExpansion(program, declaringClass, ::initializedField)
        for (law in declaringClass.ownClassLaws(program.session)) {
            expansion.body(law, mapOf(SubstitutedArgument.DispatchThis to receiver))
        }
    }
    val postconditions = fields.mapNotNull { (property, initializer) ->
        if (writes.mayChange(property)) return@mapNotNull null
        val readsOwnedState = initializer.subtree().any { expression ->
            expression is FieldAccess || expression is PrimitiveFieldAccess || expression is IntArrayGet ||
                expression is FunctionCall && expression.function.formalArgs.any { it.isUnique }
        }
        if (readsOwnedState) return@mapNotNull null
        val embedded = program.embedProperty(property)
        if (!embedded.hasDefaultBehaviour) return@mapNotNull null
        val getter = embedded.getter ?: return@mapNotNull null
        val value = if (getter is BackingFieldGetter) PrimitiveFieldAccess(result, getter.field)
        else getter.getValueSimple(result, program.typeResolver)
        EqCmp(value, initializer)
    }
    return postconditions
}
