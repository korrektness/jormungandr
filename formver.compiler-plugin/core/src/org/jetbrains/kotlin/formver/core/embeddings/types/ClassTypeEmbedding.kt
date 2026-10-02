/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.types

import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.PathStep
import org.jetbrains.kotlin.formver.core.names.PredicateName
import org.jetbrains.kotlin.formver.core.names.ScopedName
import org.jetbrains.kotlin.formver.core.names.asScope
import org.jetbrains.kotlin.formver.viper.ast.DomainFunc
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.PermExp
import org.jetbrains.kotlin.formver.viper.ast.Predicate

// TODO: incorporate generic parameters.
data class ClassTypeEmbedding(override val name: ScopedName) : PretypeEmbedding {

    override val runtimeType: Exp = this.embedClassTypeFunc()()

    val uniquePredicateName = ScopedName(name.asScope(), PredicateName("unique"))

    context(ctx: TypeResolver)
    fun uniquePredicate(): Predicate = when (this) {
        IntArrayEmbedding.classType -> IntArrayEmbedding.uniquePredicate()
        StringBuilderEmbedding.classType -> StringBuilderEmbedding.uniquePredicate()
        else -> userClassUniquePredicate()
    }

    context(ctx: TypeResolver)
    private fun userClassUniquePredicate(): Predicate = ClassPredicateBuilder.build(name, uniquePredicateName) {
        addUniquePredicateBody(NestedPredicates.Held)
    }

    /**
     * The body of this class's unique predicate for [subject], with [nested] standing for the unique predicates it
     * nests.
     */
    context(ctx: TypeResolver)
    fun uniquePredicateBody(subject: ExpEmbedding, nested: NestedPredicates): ExpEmbedding =
        ClassPredicateBuilder.body(name, subject) { addUniquePredicateBody(nested) }

    context(ctx: TypeResolver)
    private fun ClassPredicateBuilder.addUniquePredicateBody(nested: NestedPredicates) {
        includeSubTypeInvariants()
        forEachPropertyField {
            forBackingField {
                if (!isAlwaysWriteable) {
                    addAccessPermissions(PermExp.FullPerm())

                    forType {
                        includeSubTypeInvariants()
                    }
                }
            }
            forType {
                if (isUnique) {
                    val step = ownedStep
                    addAccessToUniquePredicate { access -> if (step == null) access else nested.ofField(step, access) }
                }
            }
        }
        forEachSuperType {
            val superType = type.pretype as ClassTypeEmbedding
            addAccessToUniquePredicate { access -> nested.ofSuperType(superType, access) }
        }
    }

    override fun accessInvariants(ctx: TypeResolver): List<TypeInvariantEmbedding> =
        ctx.flatMapUniqueFields(name) { field ->
            field.accessInvariantsForParameter()
        }

    override fun uniquePredicateAccessInvariant(ctx: TypeResolver) =
        PredicateAccessTypeInvariantEmbedding(uniquePredicateName, PermExp.FullPerm())

}


fun ClassTypeEmbedding.embedClassTypeFunc(): DomainFunc = RuntimeTypeDomain.classTypeFunc(name)

/**
 * What a unique predicate body asserts in place of the unique predicates it nests. Each function gets the access to
 * the nested predicate and returns the assertion that stands for it, `null` to leave it out.
 */
interface NestedPredicates {
    /** The predicate of the value of the `@Unique` property [step]. */
    fun ofField(step: PathStep, access: TypeInvariantEmbedding): TypeInvariantEmbedding? = access

    /** The predicate of the supertype [type] of the subject. */
    fun ofSuperType(type: ClassTypeEmbedding, access: TypeInvariantEmbedding): TypeInvariantEmbedding? = access

    /** Every nested predicate is held. */
    object Held : NestedPredicates
}
