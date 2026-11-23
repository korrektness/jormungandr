/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirSmartCastExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.scopes.impl.typeAliasConstructorInfo
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.isMarkedOrFlexiblyNullable
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.formver.common.isLibraryDeclaration
import org.jetbrains.kotlin.formver.core.embeddings.callables.CallableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.callables.insertCall
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.intrinsics.plugin.stringBuilderClassIds
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

private val valueClassIds: Set<ClassId> = setOf(
    StandardClassIds.String, StandardClassIds.Char, StandardClassIds.Boolean,
    StandardClassIds.Byte, StandardClassIds.Short, StandardClassIds.Int, StandardClassIds.Long,
    StandardClassIds.Float, StandardClassIds.Double,
    StandardClassIds.UByte, StandardClassIds.UShort, StandardClassIds.UInt, StandardClassIds.ULong,
)

private val recordClassIds: Set<ClassId> = setOf(ClassId.topLevel(FqName("kotlin.Pair")), ClassId.topLevel(FqName("kotlin.Triple")))

private val kotlinPackage = FqName("kotlin")
private val collectionsPackage = FqName("kotlin.collections")
private val TO = Name.identifier("to")
private val TO_STRING = Name.identifier("toString")
private val HASH_CODE = Name.identifier("hashCode")
private val EQUALS = Name.identifier("equals")
private val PLUS = Name.identifier("plus")
private val ADD = Name.identifier("add")

private fun collectionFunctions(vararg names: String): Set<Name> = names.map(Name::identifier).toSet()

/** Collection factories that allocate and copy their arguments, calling no method of an element. */
private val listFactories = collectionFunctions(
    "emptyList", "listOf", "listOfNotNull", "mutableListOf", "arrayListOf", "emptySet", "emptyMap",
)

/**
 * Hashed collection factories: an overload without a `vararg` parameter, such as `setOf(element)`, allocates only; a
 * `vararg` overload calls `hashCode` and `equals` of the elements or keys.
 */
private val hashedFactories = collectionFunctions(
    "setOf", "mutableSetOf", "hashSetOf", "linkedSetOf", "mapOf", "mutableMapOf", "hashMapOf", "linkedMapOf",
)

/** Calls that return a new list of a class whose `add(element)` appends without throwing. */
private val growableListFactories = setOf(
    CallableId(collectionsPackage, Name.identifier("mutableListOf")),
    CallableId(collectionsPackage, Name.identifier("arrayListOf")),
)
private val arrayListClassId = ClassId.topLevel(FqName("java.util.ArrayList"))
private val mutableListClassId = ClassId.topLevel(FqName("kotlin.collections.MutableList"))

/**
 * Whether [this] call is to a library function that throws no exception here other than by resource exhaustion
 * (SPECIFICATIONS.md, "Exception freedom"): `to`, the constructor of `Pair` or `Triple`, `toString`, `hashCode` or
 * `equals` on a receiver of a [valueClassIds] type, `String.plus` of a value it converts by such a `toString`, a
 * `StringBuilder` constructor without a capacity, the `ArrayList` constructor without arguments, a collection factory that calls no element method, and
 * `add(element)` on a list that [isGrowableList]. None of these calls can dispatch to source code.
 */
fun FirFunctionCall.isExceptionFreeLibraryCall(): Boolean {
    val symbol = (toResolvedCallableSymbol()?.unaliased() as? FirFunctionSymbol<*>)?.takeIf { it.isLibraryDeclaration() } ?: return false
    val id = symbol.callableId ?: return false
    val topLevelKotlin = id.className == null && id.packageName == kotlinPackage
    return when {
        symbol is FirConstructorSymbol -> id.classId in recordClassIds || isPlainStringBuilderConstruction(id.classId) ||
                id.classId == arrayListClassId && arguments.isEmpty()
        topLevelKotlin && id.callableName == TO -> true
        id.className == null && id.packageName == collectionsPackage -> when (id.callableName) {
            in listFactories -> true
            in hashedFactories -> symbol.valueParameterSymbols.none { it.isVararg } ||
                    resolvedType.typeArguments.firstOrNull()?.type?.classId() in valueClassIds
            else -> false
        }
        id.classId == StandardClassIds.String && id.callableName == PLUS ->
            arguments.singleOrNull()?.resolvedType?.isConcatenatedByToString() == true
        id.callableName == ADD && (id.classId == mutableListClassId || id.classId == arrayListClassId) ->
            arguments.size == 1 && dispatchReceiver?.isGrowableList() == true
        else -> isValueTypeMethodCall(id.callableName, if (topLevelKotlin) extensionReceiver else dispatchReceiver)
    }
}

private fun FirFunctionCall.isValueTypeMethodCall(name: Name, receiver: FirExpression?): Boolean {
    val arity = when (name) {
        TO_STRING, HASH_CODE -> 0
        EQUALS -> 1
        else -> return false
    }
    return arguments.size == arity && receiver?.resolvedType?.classId() in valueClassIds
}

private fun ConeKotlinType.classId(): ClassId? = (lowerBoundIfFlexible() as? ConeClassLikeType)?.lookupTag?.classId

/**
 * Whether `String.plus` and a string template convert a value of [this] type by its library `toString`. A `String` or
 * `Char` that is not nullable is instead appended by the concatenation operators.
 */
internal fun ConeKotlinType.isConcatenatedByToString(): Boolean {
    val classId = classId() ?: return false
    val appendedDirectly = !isMarkedOrFlexiblyNullable && (classId == StandardClassIds.String || classId == StandardClassIds.Char)
    return classId in valueClassIds && !appendedDirectly
}

/** A `StringBuilder()` or `StringBuilder(String)`; a capacity may be negative, and a `CharSequence` may throw. */
private fun FirFunctionCall.isPlainStringBuilderConstruction(classId: ClassId?): Boolean {
    if (classId !in stringBuilderClassIds) return false
    val argument = arguments.singleOrNull() ?: return arguments.isEmpty()
    val type = argument.resolvedType
    return !type.isMarkedOrFlexiblyNullable && type.classId() == StandardClassIds.String
}

/**
 * Whether [this] receiver is a list whose class SnaKt knows to grow on `add`: a call of `mutableListOf`,
 * `arrayListOf` or the `ArrayList` constructor, or a `val` without a custom getter that cannot be overridden and is
 * initialized by such a call.
 */
@OptIn(SymbolInternals::class)
private fun FirExpression.isGrowableList(): Boolean {
    val expression = (this as? FirSmartCastExpression)?.originalExpression ?: this
    if (expression is FirFunctionCall) return expression.isGrowableListAllocation()
    val property = (expression as? FirPropertyAccessExpression)?.toResolvedCallableSymbol() as? FirPropertySymbol ?: return false
    val defaultGetter = property.getterSymbol?.fir.let { it == null || it is FirDefaultPropertyGetter }
    if (!property.isVal || property.hasDelegate) return false
    if (!property.isLocal && !(property.isFinal && defaultGetter)) return false
    return (property.resolvedInitializer as? FirFunctionCall)?.isGrowableListAllocation() == true
}

private fun FirFunctionCall.isGrowableListAllocation(): Boolean {
    val symbol = toResolvedCallableSymbol()?.unaliased()?.takeIf { it.isLibraryDeclaration() } ?: return false
    return if (symbol is FirConstructorSymbol) symbol.callableId.classId == arrayListClassId
    else symbol.callableId in growableListFactories
}

/** [this] callable, or the constructor that a typealias constructor such as `StringBuilder()` stands for. */
private fun FirCallableSymbol<*>.unaliased(): FirCallableSymbol<*> =
    (this as? FirConstructorSymbol)?.typeAliasConstructorInfo?.originalConstructor?.symbol ?: this

/** Inserts [callee] at [call] on the evaluated [args], with no exceptional exit when the call is exception-free. */
fun StmtConversionContext.insertCallAt(
    call: FirFunctionCall,
    callee: CallableEmbedding,
    args: List<ExpEmbedding>,
    returnType: TypeEmbedding,
): ExpEmbedding = withCallSite(call) {
    if (call.isExceptionFreeLibraryCall()) withoutExceptionalExits { callee.insertCall(args, this, returnType) }
    else callee.insertCall(args, this, returnType)
}
