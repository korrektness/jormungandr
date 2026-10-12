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
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.formver.core.isLaw
import org.jetbrains.kotlin.formver.core.isPure
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
                    (it.className?.asString() == "String" && it.callableName.asString() == "length"))
} == true

private fun FirQualifiedAccessExpression.isAllowedInitializerAccess(
    parameters: Set<FirValueParameterSymbol>, session: FirSession,
): Boolean = when (val callee = toResolvedCallableSymbol()) {
    is FirValueParameterSymbol -> callee in parameters
    is FirPropertySymbol -> callee.resolvedStatus.isConst || isLawValueOperation()
    else -> isLawValueOperation() || (this is FirFunctionCall && callee?.isPure(session) == true)
}

@OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
fun classLawConstructionFailures(symbol: FirRegularClassSymbol, session: FirSession): List<String> {
    val failures = mutableListOf<String>()
    val constructors = symbol.declarationSymbols.filterIsInstance<FirConstructorSymbol>()
    val primary = constructors.singleOrNull { it.isPrimary }
    if (primary == null) failures += "Missing primary constructor"
    if (constructors.any { !it.isPrimary }) failures += "Secondary constructor in invariant class"
    if (symbol.fir.declarations.any { it is FirAnonymousInitializer }) failures += "Init block in invariant class"
    if (symbol.hasSuperclassLaws(session)) failures += "Superclass carries class laws"
    val parameters = primary?.valueParameterSymbols.orEmpty().toSet()
    symbol.fir.declarations.filterIsInstance<FirProperty>().forEach { property ->
        if (property.delegate != null) failures += "Delegated property in invariant class"
        val initializer = property.initializer
        if (initializer == null) {
            if (property.backingField != null && property.getter?.body == null) failures += "Uninitialized field in invariant class"
        } else {
            var valid = true
            initializer.accept(object : FirVisitorVoid() {
                override fun visitElement(element: FirElement) {
                    when (element) {
                        is FirVariableAssignment, is FirThisReceiverExpression, is FirAnonymousFunctionExpression, is FirSimpleFunction -> valid = false
                        is FirQualifiedAccessExpression -> {
                            if (!element.isAllowedInitializerAccess(parameters, session)) valid = false
                        }
                    }
                    element.acceptChildren(this)
                }
            })
            if (!valid) failures += "Property initializer is not a pure expression over constructor parameters"
        }
    }
    return failures.distinct()
}

/** The constructor's representation laws, evaluated on its field initializers. */
@OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
context(program: ProgramConversionContext)
fun classLawConstructorConditions(
    symbol: FirConstructorSymbol, signature: NonInlineCallable, result: ExpEmbedding,
): Pair<List<ExpEmbedding>, List<ExpEmbedding>> {
    val owner = symbol.resolvedReturnType.toRegularClassSymbol(program.session) ?: return emptyList<ExpEmbedding>() to emptyList()
    val laws = owner.ownClassLaws(program.session)
    if (laws.isEmpty()) return emptyList<ExpEmbedding>() to emptyList()
    val receiver = PlaceholderVariableEmbedding(DispatchReceiverName, program.embedType(symbol.resolvedReturnType), isUnique = true)
    val substitutions = symbol.valueParameterSymbols.zip(signature.params).associate { (symbol, value) ->
        SubstitutedArgument.ValueParameter(symbol) to value
    } + (SubstitutedArgument.DispatchThis to receiver)
    val target = program.returnTargetProducer.getFresh(signature.callableType.returnType)
    val resolver = InlineParameterResolver(substitutions, null, target)
    val context = MethodConverter(
        program, signature, resolver, program.scopeIndexProducer.getFresh(), OwnershipFrame.root(null, emptyList()),
    ).statementCtxt()
    val fields = owner.fir.declarations.filterIsInstance<FirProperty>().mapNotNull { property ->
        property.initializer?.let { property.symbol to context.convert(it) }
    }.toMap()
    val expansion = ClassLawExpansion(program, owner, fields)
    val preconditions = laws.map { law ->
        val resultType = with(program) { law.toFunctionSignature() }.signature.returns.type
        ExpWrapper(expansion.body(law, mapOf(SubstitutedArgument.DispatchThis to receiver)), resultType)
    }
    val postconditions = fields.mapNotNull { (property, initializer) ->
        val getter = program.embedProperty(property).getter ?: return@mapNotNull null
        val value = if (getter is BackingFieldGetter) PrimitiveFieldAccess(result, getter.field)
        else getter.getValueSimple(result, program.typeResolver)
        EqCmp(value, initializer)
    }
    return preconditions to postconditions
}
