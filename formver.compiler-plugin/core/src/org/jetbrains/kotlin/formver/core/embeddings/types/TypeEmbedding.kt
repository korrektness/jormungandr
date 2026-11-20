/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.types

import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.domains.Injection
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.embeddings.expression.debug.PlaintextLeaf
import org.jetbrains.kotlin.formver.core.embeddings.expression.debug.TreeView
import org.jetbrains.kotlin.formver.core.names.TypeName
import org.jetbrains.kotlin.formver.viper.NameResolver
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.mangled

/**
 * Represents our representation of a Kotlin type.
 *
 * Due to name mangling, the mapping between Kotlin types and TypeEmbeddings must be 1:1.
 *
 * A value of an intersection type has [pretype] and every pretype in [otherComponents] as well; its subtype, pure and
 * access invariants are those of all of them. Its runtime type and unique predicate are those of [pretype], and its
 * name includes every component.
 */
data class TypeEmbedding(
    val pretype: PretypeEmbedding,
    val flags: TypeEmbeddingFlags,
    val otherComponents: List<PretypeEmbedding> = emptyList(),
) : RuntimeTypeHolder, TypeInvariantHolder {
    /**
     * Name representing the type, used for distinguishing overloads.
     *
     * It may at some point necessary to make a `TypeName` hierarchy of some sort to
     * represent these names, but we do it inline for now.
     */
    val name: TypeName
        get() = TypeName(pretype, flags.nullable, otherComponents)

    /**
     * Get a nullable version of this type embedding.
     *
     * Note that nullability doesn't stack, hence nullable types must return themselves.
     */
    fun getNullable(): TypeEmbedding = copy(flags = flags.copy(nullable = true))

    /**
     * Drop nullability, if it is present.
     */
    fun getNonNullable(): TypeEmbedding = copy(flags = flags.copy(nullable = false))

    val isNullable: Boolean
        get() = flags.nullable

    override val runtimeType: Exp = flags.adjustRuntimeType(pretype.runtimeType)

    override fun accessInvariants(ctx: TypeResolver): List<TypeInvariantEmbedding> =
        flags.adjustManyInvariants((listOf(pretype) + otherComponents).flatMap { it.accessInvariants(ctx) })

    override fun pureInvariants(): List<TypeInvariantEmbedding> =
        flags.adjustManyInvariants((listOf(pretype) + otherComponents).flatMap { it.pureInvariants() })

    override fun uniquePredicateAccessInvariant(ctx: TypeResolver): TypeInvariantEmbedding? =
        flags.adjustOptionalInvariant(pretype.uniquePredicateAccessInvariant(ctx))

    override fun subTypeInvariant(): TypeInvariantEmbedding =
        if (otherComponents.isEmpty()) SubTypeInvariantEmbedding(this)
        else AllInvariants(listOf(SubTypeInvariantEmbedding(this)) + otherComponents.map {
            SubTypeInvariantEmbedding(TypeEmbedding(it, flags))
        })

    context(nameResolver: NameResolver)
    override val debugTreeView: TreeView
        get() = PlaintextLeaf(name.mangled)

}

data class TypeEmbeddingFlags(val nullable: Boolean) {
    fun adjustRuntimeType(runtimeType: Exp): Exp =
        if (nullable) RuntimeTypeDomain.nullable(runtimeType)
        else runtimeType

    fun adjustInvariant(invariant: TypeInvariantEmbedding): TypeInvariantEmbedding =
        if (nullable) IfNonNullInvariant(invariant)
        else invariant

    fun adjustManyInvariants(invariants: List<TypeInvariantEmbedding>): List<TypeInvariantEmbedding> =
        invariants.map { adjustInvariant(it) }

    fun adjustOptionalInvariant(invariant: TypeInvariantEmbedding?): TypeInvariantEmbedding? =
        invariant?.let { adjustInvariant(it) }
}

inline fun TypeEmbedding.injectionOr(default: (TypeEmbedding) -> Injection) = injectionOrNull ?: default(this)

val TypeEmbedding.injection
    get() = injectionOr {
        error("Type ${it.name} has no injection specified.")
    }

val TypeEmbedding.injectionOrNull: Injection?
    get() =
        if (flags.nullable) null
        else when (this.pretype) {
            StringTypeEmbedding -> RuntimeTypeDomain.stringInjection
            MultisetTypeEmbedding -> RuntimeTypeDomain.multisetInjection
            IterationSequenceTypeEmbedding -> RuntimeTypeDomain.iterationSequenceInjection
            CharTypeEmbedding -> RuntimeTypeDomain.charInjection
            IntTypeEmbedding -> RuntimeTypeDomain.intInjection
            LongTypeEmbedding -> RuntimeTypeDomain.longInjection
            ShortTypeEmbedding -> RuntimeTypeDomain.shortInjection
            ByteTypeEmbedding -> RuntimeTypeDomain.byteInjection
            BooleanTypeEmbedding -> RuntimeTypeDomain.boolInjection
            UByteTypeEmbedding -> RuntimeTypeDomain.ubyteInjection
            UShortTypeEmbedding -> RuntimeTypeDomain.ushortInjection
            UIntTypeEmbedding -> RuntimeTypeDomain.uintInjection
            ULongTypeEmbedding -> RuntimeTypeDomain.ulongInjection
            else -> null
        }
