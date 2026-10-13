/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.formver.common.UnsupportedFeatureException
import org.jetbrains.kotlin.formver.core.*
import org.jetbrains.kotlin.formver.core.embeddings.types.*
import org.jetbrains.kotlin.formver.uniqueness.plugin.multisetClassId

context(converter: ProgramConverter)
internal fun TypeBuilder.embedTypeWithBuilder(type: ConeKotlinType): PretypeBuilder = when {
    type is ConeErrorType -> error("Encountered an erroneous type: $type")
    type is ConeTypeParameterType -> {
        isNullable = true; any()
    }

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
    type.isNothing -> nothing()
    type.isSomeFunctionType(converter.session) -> function {
        check(type is ConeClassLikeType) { "Expected a ConeClassLikeType for a function type, got $type" }
        type.receiverType(converter.session)?.let { withDispatchReceiver { embedTypeWithBuilder(it) } }
        type.valueParameterTypesWithoutReceivers(converter.session).forEach { param ->
            withParam { embedTypeWithBuilder(param) }
        }
        withReturnType { embedTypeWithBuilder(type.returnType(converter.session)) }
    }

    type.canBeNull(converter.session) -> {
        isNullable = true
        embedTypeWithBuilder(type.withNullability(false, converter.session.typeContext))
    }

    type.isAny -> any()
    type is ConeClassLikeType && type.lookupTag.classId == multisetClassId -> {
        val element = type.typeArguments.single().type
        if (element !is ConeTypeParameterType && element?.isInt != true) {
            throw UnsupportedFeatureException(null, "type `$type`, a `Multiset` whose element type is not `Int`")
        }
        converter.typeResolver.markMultisetUsed()
        multiset()
    }
    type is ConeClassLikeType -> {
        val classLikeSymbol = type.toClassSymbol(converter.session)
        if (classLikeSymbol is FirRegularClassSymbol) {
            existing(converter.embedClass(classLikeSymbol))
        } else {
            unimplementedTypeEmbedding(type)
        }
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
    symbol.valueParameterSymbols.forEach { param ->
        withParam {
            embedTypeWithBuilder(param.resolvedReturnType)
        }
    }
    withReturnType { embedTypeWithBuilder(symbol.resolvedReturnType) }
    returnsUnique = context(converter.checkerContext) { symbol.returnsUnique() }
}

private fun unimplementedTypeEmbedding(type: ConeKotlinType): Nothing =
    throw UnsupportedFeatureException(null, "type `$type`")
