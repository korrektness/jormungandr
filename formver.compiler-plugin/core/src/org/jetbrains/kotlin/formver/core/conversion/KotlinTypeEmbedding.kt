/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.formver.core.signatureParameterSymbols
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousObjectSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.*
import org.jetbrains.kotlin.formver.core.embeddings.types.*
import org.jetbrains.kotlin.formver.uniqueness.plugin.iterationSequenceClassId
import org.jetbrains.kotlin.formver.uniqueness.plugin.multisetClassId

@OptIn(SymbolInternals::class)
context(converter: ProgramConverter)
internal fun TypeBuilder.embedTypeWithBuilder(type: ConeKotlinType): PretypeBuilder = when {
    type is ConeErrorType -> error("Encountered an erroneous type: $type")
    type is ConeTypeParameterType -> {
        isNullable = type.canBeNull(converter.session)
        embedComponents(type.lookupTag.typeParameterSymbol.resolvedBounds.map { it.coneType }, type.isMarkedNullable)
    }
    type is ConeDefinitelyNotNullType -> embedTypeWithBuilder(type.original).also { isNullable = false }
    type is ConeFlexibleType -> embedTypeWithBuilder(type.upperBound)
    type is ConeCapturedType -> embedComponents(
        type.constructor.supertypes.orEmpty().ifEmpty { listOf(converter.session.builtinTypes.nullableAnyType.coneType) },
        type.isMarkedNullable,
    )
    type is ConeIntersectionType -> embedComponents(type.intersectedTypes.toList(), markedNullable = false)

    type.isString -> {
        val stringClassSymbol = type.toClassSymbol(converter.session) as FirRegularClassSymbol
        stringClassSymbol.propertySymbols.forEach {
            converter.embedProperty(it)
        }
        string()
    }

    type.isUnit -> unit()
    type.isChar -> char()
    type.isInt -> int()
    type.isBoolean -> boolean()
    integerPretype(type) != null -> existing(integerPretype(type)!!)
    type.isNothing -> nothing()
    type.isSomeFunctionType(converter.session) -> function {
        check(type is ConeClassLikeType) { "Expected a ConeClassLikeType for a function type, got $type" }
        type.receiverType(converter.session)?.let { withDispatchReceiver { embedTypeWithBuilder(it) } }
        type.valueParameterTypesWithoutReceivers(converter.session).forEach { param ->
            withParam { embedTypeWithBuilder(param) }
        }
        withReturnType { embedTypeWithBuilder(type.returnType(converter.session)) }
    }

    type.canBeNull(converter.session) -> embedNullable(type)

    type.isAny -> any()
    type is ConeClassLikeType && type.lookupTag.classId == iterationSequenceClassId -> {
        converter.typeResolver.markIterationSequenceUsed()
        iterationSequence()
    }
    type is ConeClassLikeType && type.lookupTag.classId == multisetClassId -> {
        val element = type.typeArguments.single().type
        if (element !is ConeTypeParameterType && element?.isInt != true) {
            throw UnsupportedFeatureException(null, "type `${type.renderReadable()}`, a `Multiset` whose element type is not `Int`")
        }
        converter.typeResolver.markMultisetUsed()
        multiset()
    }
    type is ConeClassLikeType -> when (val classLikeSymbol = type.toClassSymbol(converter.session)) {
        is FirRegularClassSymbol -> existing(converter.embedClass(classLikeSymbol))
        is FirAnonymousObjectSymbol -> if (with(converter) { classLikeSymbol.fir.eligibleSuperclass() } != null) {
            existing(converter.typeResolver.embedAnonymousClass(classLikeSymbol) { converter.embedType(it).pretype.name })
        } else embedComponents(classLikeSymbol.resolvedSuperTypes, type.isMarkedNullable)
        else -> unimplementedTypeEmbedding(type)
    }

    else -> unimplementedTypeEmbedding(type)
}

context(converter: ProgramConverter)
internal fun FunctionPretypeBuilder.embedFunctionPretypeWithBuilder(symbol: FirFunctionSymbol<*>) {
    symbol.receiverType?.let {
        withDispatchReceiver { embedTypeWithBuilder(it) }
    }
    symbol.extensionReceiverType?.let {
        withExtensionReceiver { embedTypeWithBuilder(it) }
    }
    symbol.signatureParameterSymbols.forEach { param ->
        withParam {
            embedTypeWithBuilder(param.resolvedReturnType)
        }
    }
    withReturnType(converter.embedCallableResult(symbol))
    returnsUnique = context(converter.checkerContext) { symbol.returnsUnique() }
}

/** The nominal owner of a runtime constructor whose class has a separate specification-value encoding. */
internal fun FirFunctionSymbol<*>.intrinsicConstructionOwner(session: FirSession): FirRegularClassSymbol? {
    val owner = (this as? FirConstructorSymbol)?.resolvedReturnType?.toRegularClassSymbol(session) ?: return null
    return owner.takeIf { it.classId == multisetClassId || it.classId == iterationSequenceClassId }
}

/** A direct constructor call cannot produce a specification value. */
context(converter: ProgramConverter)
internal fun FirFunctionSymbol<*>.checkIntrinsicConstructorUse() {
    if (intrinsicConstructionOwner(converter.session) != null) throw UnsupportedFeatureException(
        null, "a direct call to a specification value constructor",
    )
}

context(converter: ProgramConverter)
private fun TypeBuilder.embedNullable(type: ConeKotlinType): PretypeBuilder {
    isNullable = true
    val nonNullable = type.withNullability(false, converter.session.typeContext)
    if (nonNullable == type) unimplementedTypeEmbedding(type)
    return embedTypeWithBuilder(nonNullable)
}

context(converter: ProgramConverter)
private fun TypeBuilder.embedComponents(components: List<ConeKotlinType>, markedNullable: Boolean): PretypeBuilder {
    val session = converter.session
    isNullable = isNullable || markedNullable || components.all { it.canBeNull(session) }
    val pretypes = components.map { converter.embedType(it.withNullability(false, session.typeContext)) }
        .flatMap { listOf(it.pretype) + it.otherComponents }.filter { it != AnyTypeEmbedding }.distinct()
    otherComponents = pretypes.drop(1)
    return pretypes.firstOrNull()?.let { existing(it) } ?: any()
}

private fun unimplementedTypeEmbedding(type: ConeKotlinType): Nothing =
    throw UnsupportedFeatureException(null, "type `${type.renderReadable()}`")

private fun integerPretype(type: ConeKotlinType): PretypeEmbedding? = when {
    type.isLong -> LongTypeEmbedding
    type.isShort -> ShortTypeEmbedding
    type.isByte -> ByteTypeEmbedding
    type.isUByte -> UByteTypeEmbedding
    type.isUShort -> UShortTypeEmbedding
    type.isUInt -> UIntTypeEmbedding
    type.isULong -> ULongTypeEmbedding
    else -> null
}
