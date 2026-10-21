/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.common.externalSpecifications
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.getSealedClassInheritors
import org.jetbrains.kotlin.fir.declarations.utils.isData
import org.jetbrains.kotlin.fir.declarations.utils.isExpect
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.declarations.utils.isInlineOrValue
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.isSealed
import org.jetbrains.kotlin.fir.expressions.FirEqualityOperatorCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.scopes.ScopeFunctionRequiresPrewarm
import org.jetbrains.kotlin.fir.scopes.processAllFunctions
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.formver.core.embeddings.callables.FunctionSignature
import org.jetbrains.kotlin.formver.core.embeddings.callables.insertCall
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.properties.ClassPropertyAccess
import org.jetbrains.kotlin.formver.core.embeddings.types.*
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.AbstractTypeChecker
import org.jetbrains.kotlin.types.ConstantValueKind
import org.jetbrains.kotlin.util.OperatorNameConventions

/** What Kotlin `a == b` does, given the static type of `a`. */
private sealed interface EqualityKind {
    /** The values are injected into `Ref` one-to-one, so equal values are equal references. */
    data object Value : EqualityKind

    /** `equals` is `Any.equals` or `Enum.equals` for every value of the type. */
    data object Identity : EqualityKind

    /** The generated `equals` of the source data class [cls]. */
    data class Structural(val cls: FirRegularClassSymbol) : EqualityKind

    /** The source member [equals], called through its specification. */
    data class Declared(val equals: FirNamedFunctionSymbol) : EqualityKind

    /** An `equals` the conversion does not know. */
    data object Dynamic : EqualityKind
}

private val valuePretypes = setOf(
    UnitTypeEmbedding, NothingTypeEmbedding, IntTypeEmbedding, LongTypeEmbedding, ShortTypeEmbedding,
    ByteTypeEmbedding, BooleanTypeEmbedding, CharTypeEmbedding, StringTypeEmbedding, MultisetTypeEmbedding,
    IterationSequenceTypeEmbedding,
)

/** The classes whose values are injected into `Ref` and boxed on the JVM. */
private val injectedClassIds = listOf(
    StandardClassIds.Int, StandardClassIds.Long, StandardClassIds.Short, StandardClassIds.Byte,
    StandardClassIds.Char, StandardClassIds.Boolean, StandardClassIds.String,
)

/**
 * Kotlin `a == b` for the [call] that compares [left] and [right]: a call of `a.equals(b)`, or `b === null` when `a`
 * is null. See the equality section of SPECIFICATIONS.md.
 */
internal fun StmtConversionContext.convertEquality(
    call: FirEqualityOperatorCall, left: ExpEmbedding, right: ExpEmbedding,
): ExpEmbedding {
    val (leftArg, rightArg) = call.arguments
    if (leftArg.isNullLiteral() || rightArg.isNullLiteral()) return EqCmp(left, right)
    val leftType = leftArg.resolvedType
    val kind = equalityKind(leftType)
    if (kind == EqualityKind.Value || kind == EqualityKind.Identity) return EqCmp(left, right)
    val owned = ownsBefore(call, leftArg) || ownsBefore(call, rightArg)
    val elementType = leftType.lowerBoundIfFlexible().let {
        if (it is ConeDefinitelyNotNullType) it.original else it
    }
    val trustedDynamic = trustsElementEquality && kind == EqualityKind.Dynamic && elementType is ConeTypeParameterType
    return EqualityLowering(this, call).lower(kind, left, right, leftType.isMarkedOrFlexiblyNullable, owned, trustedDynamic)
}

/**
 * Rejects Kotlin `a === b` when both operands may hold a value of the same injected type. The JVM boxes such values,
 * and whether two boxes of one value are one object depends on the value and on how each box was made, so no
 * reference model of `===` on them is sound. Operands typed by type parameters of external specification declarations
 * are exempt: there `===` states that the model holds the reference a collection stores.
 */
internal fun StmtConversionContext.checkIdentityComparable(call: FirEqualityOperatorCall) {
    val (left, right) = call.arguments.map { it.resolvedType }
    if (isModelElement(left) && isModelElement(right)) return
    val shared = injectedClassIds.firstOrNull { id ->
        val injected = id.constructClassLikeType()
        mayHold(left, injected) && mayHold(right, injected)
    } ?: return
    throw UnsupportedFeatureException(
        call.source, "`===` on operands that may both hold a boxed ${shared.shortClassName}",
    )
}

private fun StmtConversionContext.isModelElement(type: ConeKotlinType): Boolean {
    val parameter = type.lowerBoundIfFlexible() as? ConeTypeParameterType ?: return false
    val owner = parameter.lookupTag.typeParameterSymbol.containingDeclarationSymbol
    return session.externalSpecifications?.isSpecificationDeclaration(owner) == true
}

private fun StmtConversionContext.mayHold(type: ConeKotlinType, injected: ConeKotlinType): Boolean = when (type) {
    is ConeFlexibleType -> mayHold(type.upperBound, injected)
    is ConeDefinitelyNotNullType -> mayHold(type.original, injected)
    is ConeIntersectionType -> type.intersectedTypes.all { mayHold(it, injected) }
    is ConeTypeParameterType ->
        type.lookupTag.typeParameterSymbol.resolvedBounds.all { mayHold(it.coneType, injected) }
    else -> AbstractTypeChecker.isSubtypeOf(
        session.typeContext, injected, type.fullyExpandedType(session).withNullability(true, session.typeContext),
    )
}

private fun FirExpression.isNullLiteral(): Boolean = this is FirLiteralExpression && kind == ConstantValueKind.Null

private fun StmtConversionContext.equalityKind(type: ConeKotlinType): EqualityKind {
    val expanded = type.fullyExpandedType(session).lowerBoundIfFlexible()
    if (expanded is ConeClassLikeType && embedType(expanded).pretype in valuePretypes) return EqualityKind.Value
    // Every value of a type parameter is a value of each bound, so a bound with value or identity equality decides it.
    val bounds = when (expanded) {
        is ConeTypeParameterType -> expanded.lookupTag.typeParameterSymbol.resolvedBounds.map { it.coneType }
        is ConeDefinitelyNotNullType -> listOf(expanded.original)
        is ConeIntersectionType -> expanded.intersectedTypes.toList()
        else -> null
    }
    if (bounds != null) {
        return bounds.map { equalityKind(it) }
            .firstOrNull { it == EqualityKind.Value || it == EqualityKind.Identity } ?: EqualityKind.Dynamic
    }
    val cls = expanded.toRegularClassSymbol(session) ?: return EqualityKind.Dynamic
    return classEqualityKind(cls)
}

@OptIn(ScopeFunctionRequiresPrewarm::class)
private fun StmtConversionContext.equalsImplementation(cls: FirRegularClassSymbol): FirNamedFunctionSymbol {
    val candidates = mutableListOf<FirNamedFunctionSymbol>()
    context(checkerContext) {
        cls.unsubstitutedScope().processFunctionsByName(OperatorNameConventions.EQUALS) { function ->
            if (function is FirNamedFunctionSymbol && function.valueParameterSymbols.size == 1 &&
                function.valueParameterSymbols.single().resolvedReturnType.isNullableAny
            ) candidates += function
        }
    }
    return candidates.single().unwrapFakeOverrides()
}

private fun StmtConversionContext.classEqualityKind(cls: FirRegularClassSymbol): EqualityKind {
    // The `actual` class may override `equals`.
    if (cls.isExpect) return EqualityKind.Dynamic
    val implementation = equalsImplementation(cls)
    val declaringClass = implementation.callableId.classId
    val isSource = cls.origin == FirDeclarationOrigin.Source
    return when {
        cls.isData && (declaringClass == StandardClassIds.Any || declaringClass == cls.classId &&
                implementation.origin != FirDeclarationOrigin.Source) ->
            if (isSource) EqualityKind.Structural(cls) else EqualityKind.Dynamic
        declaringClass == StandardClassIds.Any || declaringClass == StandardClassIds.Enum ->
            if (hasIdentityEquality(cls, mutableSetOf())) EqualityKind.Identity.also { registerIdentityClass(cls) }
            else EqualityKind.Dynamic
        implementation.origin == FirDeclarationOrigin.Source -> EqualityKind.Declared(implementation)
        else -> EqualityKind.Dynamic
    }
}

private fun StmtConversionContext.registerIdentityClass(cls: FirRegularClassSymbol) {
    val embedding = embedType(cls.defaultType()).pretype as? ClassTypeEmbedding ?: return
    typeResolver.identityEqualityClasses.add(embedding)
}

/** Whether every value of [cls], including values of its subclasses, has `Any.equals` or `Enum.equals`. */
private fun StmtConversionContext.hasIdentityEquality(
    cls: FirRegularClassSymbol, visited: MutableSet<FirRegularClassSymbol>,
): Boolean {
    if (!visited.add(cls)) return true
    if (cls.isInlineOrValue || cls.isData || cls.isExpect) return false
    val declaringClass = equalsImplementation(cls).callableId.classId
    if (declaringClass != StandardClassIds.Any && declaringClass != StandardClassIds.Enum) return false
    return when {
        cls.classKind == ClassKind.ENUM_CLASS || cls.classKind == ClassKind.OBJECT -> true
        cls.isSealed -> {
            @OptIn(SymbolInternals::class)
            val inheritors = cls.fir.getSealedClassInheritors(session)
                .map { session.symbolProvider.getClassLikeSymbolByClassId(it) as? FirRegularClassSymbol }
            inheritors.all { it != null && hasIdentityEquality(it, visited) }
        }
        else -> cls.isFinal && cls.classKind == ClassKind.CLASS
    }
}

/** Builds the two forms of an `==` at [call] whose `equals` is neither identity nor value equality. */
private class EqualityLowering(val ctx: StmtConversionContext, val call: FirEqualityOperatorCall) {
    /** The data classes whose structural comparison is being built, to stop at a recursive component. */
    private val expanding = mutableSetOf<FirRegularClassSymbol>()

    fun lower(
        kind: EqualityKind, left: ExpEmbedding, right: ExpEmbedding, leftNullable: Boolean, owned: Boolean,
        trustedDynamic: Boolean,
    ): ExpEmbedding {
        val leftVar = ctx.freshAnonVar(left.type)
        val rightVar = ctx.freshAnonVar(right.type)
        val executed = if (owned) null else blockOf(
            Declare(leftVar, left),
            Declare(rightVar, right),
            if (trustedDynamic) nullGuarded(leftVar, rightVar, leftNullable) { l, r ->
                dynamic(l, r, trusted = true)
            } else executedGuarded(kind, leftVar, rightVar, leftNullable),
        )
        return ObjectEquality(
            left, right, executed,
            specified = specifiedGuarded(kind, left, right, leftNullable),
            unsupportedExecution = "`==` on an owned value whose `equals` is not identity or value equality"
                .takeIf { owned },
        )
    }

    private fun executedGuarded(
        kind: EqualityKind, left: ExpEmbedding, right: ExpEmbedding, leftNullable: Boolean,
    ): ExpEmbedding = nullGuarded(left, right, leftNullable) { l, r -> executed(kind, l, r) }

    /** The relation's axioms give its value for a null left operand, so only an expanded comparison needs a guard. */
    private fun specifiedGuarded(
        kind: EqualityKind, left: ExpEmbedding, right: ExpEmbedding, leftNullable: Boolean,
    ): ExpEmbedding =
        if (specifiedAsRelation(kind)) EqualsRelation(left, right)
        else nullGuarded(left, right, leftNullable) { l, r -> specified(kind, l, r) }

    private fun specifiedAsRelation(kind: EqualityKind): Boolean = when (kind) {
        EqualityKind.Value, EqualityKind.Identity -> false
        is EqualityKind.Structural -> kind.cls in expanding || !componentsOf(kind.cls).all { it.isVal }
        is EqualityKind.Declared -> (ctx.embedAnyFunction(kind.equals) as? FunctionSignature)?.isPure != true
        EqualityKind.Dynamic -> true
    }

    /** `l == r` given [compare] for a non-null `l`. */
    private fun nullGuarded(
        left: ExpEmbedding, right: ExpEmbedding, leftNullable: Boolean,
        compare: (ExpEmbedding, ExpEmbedding) -> ExpEmbedding,
    ): ExpEmbedding = if (!leftNullable) compare(left, right)
    else SequentialOr(
        SequentialAnd(EqCmp(left, NullLit), EqCmp(right, NullLit)),
        SequentialAnd(NeCmp(left, NullLit), compare(left.withType(left.type.getNonNullable()), right)),
    )

    private fun executed(kind: EqualityKind, left: ExpEmbedding, right: ExpEmbedding): ExpEmbedding = when (kind) {
        EqualityKind.Value, EqualityKind.Identity -> EqCmp(left, right)
        is EqualityKind.Structural -> structural(kind.cls, left, right, ::executedGuarded)
        is EqualityKind.Declared -> declaredCall(kind, left, right)
        EqualityKind.Dynamic -> dynamic(left, right, trusted = false)
    }

    private fun dynamic(left: ExpEmbedding, right: ExpEmbedding, trusted: Boolean): ExpEmbedding = Block {
        if (ctx.activeCatchLabels.isNotEmpty()) add(NonDeterministically(ctx.exceptionalExit()))
        add(if (trusted) EqualsRelation(left, right) else ctx.unconstrainedValue(buildType { boolean() }))
    }

    private fun specified(kind: EqualityKind, left: ExpEmbedding, right: ExpEmbedding): ExpEmbedding = when (kind) {
        EqualityKind.Value, EqualityKind.Identity -> EqCmp(left, right)
        is EqualityKind.Structural -> structural(kind.cls, left, right, ::specifiedGuarded)
        is EqualityKind.Declared -> declaredCall(kind, left, right)
        EqualityKind.Dynamic -> EqualsRelation(left, right)
    }

    private fun declaredCall(kind: EqualityKind.Declared, left: ExpEmbedding, right: ExpEmbedding): ExpEmbedding =
        ctx.withCallSite(call) {
            embedAnyFunction(kind.equals).insertCall(listOf(left, right), this, buildType { boolean() })
        }

    /** `left === right || right is D && the components of left and right are equal`, as a data class's `equals`. */
    private fun structural(
        cls: FirRegularClassSymbol, left: ExpEmbedding, right: ExpEmbedding,
        component: (EqualityKind, ExpEmbedding, ExpEmbedding, Boolean) -> ExpEmbedding,
    ): ExpEmbedding {
        val type = ctx.embedType(cls.defaultType())
        if (!expanding.add(cls)) return component(EqualityKind.Dynamic, left, right, false)
        val castRight = right.withType(type)
        val components = componentsOf(cls).map { property ->
            val propertyType = ctx.embedType(property.resolvedReturnType)
            val embedding = ctx.embedProperty(property)
            val l = ClassPropertyAccess(left.withType(type), embedding, propertyType).getValue(ctx)
            val r = ClassPropertyAccess(castRight, embedding, propertyType).getValue(ctx)
            when (val kind = ctx.equalityKind(property.resolvedReturnType)) {
                EqualityKind.Value, EqualityKind.Identity -> EqCmp(l, r)
                else -> component(kind, l, r, property.resolvedReturnType.isMarkedOrFlexiblyNullable)
            }
        }
        expanding.remove(cls)
        return SequentialOr(EqCmp(left, right), SequentialAnd(Is(right, type), components.toSequentialConjunction()))
    }

    @OptIn(DirectDeclarationsAccess::class)
    private fun componentsOf(cls: FirRegularClassSymbol): List<FirPropertySymbol> =
        cls.declarationSymbols.filterIsInstance<FirPropertySymbol>().filter { it.correspondingValueParameterFromPrimaryConstructor != null }
}

private fun List<ExpEmbedding>.toSequentialConjunction(): ExpEmbedding =
    if (isEmpty()) BooleanLit(true) else reduce { l, r -> SequentialAnd(l, r) }
