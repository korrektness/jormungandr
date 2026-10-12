/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.types

import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpWrapper
import org.jetbrains.kotlin.formver.core.linearization.pureToViper
import org.jetbrains.kotlin.formver.core.embeddings.properties.PathStep
import org.jetbrains.kotlin.formver.core.names.DispatchReceiverName
import org.jetbrains.kotlin.formver.core.names.PredicateName
import org.jetbrains.kotlin.formver.core.names.ScopedName
import org.jetbrains.kotlin.formver.core.names.asScope
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Declaration
import org.jetbrains.kotlin.formver.viper.ast.DomainFunc
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.PermExp
import org.jetbrains.kotlin.formver.viper.ast.Predicate
import org.jetbrains.kotlin.formver.viper.ast.Type

// TODO: incorporate generic parameters.
data class ClassTypeEmbedding(override val name: ScopedName) : PretypeEmbedding {

    override val runtimeType: Exp = this.embedClassTypeFunc()()

    val uniquePredicateName = ScopedName(name.asScope(), PredicateName("unique"))

    val footprintPredicateName = ScopedName(name.asScope(), PredicateName("footprint"))

    context(ctx: TypeResolver)
    fun footprintPredicate(): Predicate? = if (ctx.hasInterfaceLaws(this))
        Predicate(footprintPredicateName, listOf(Declaration.LocalVarDecl(DispatchReceiverName, Type.Ref)), body = null)
    else null

    context(ctx: TypeResolver)
    fun uniquePredicate(): Predicate = when (this) {
        IntArrayEmbedding.classType -> IntArrayEmbedding.uniquePredicate()
        StringBuilderEmbedding.classType -> StringBuilderEmbedding.uniquePredicate()
        else -> if (ctx.isInterface(this)) {
            val receiver = Exp.LocalVar(DispatchReceiverName, Type.Ref)
            val body = if (ctx.hasInterfaceLaws(this)) {
                val footprint = Exp.PredicateAccess(footprintPredicateName, listOf(receiver), PermExp.FullPerm())
                val subtype = RuntimeTypeDomain.isSubtype(RuntimeTypeDomain.typeOf(receiver), runtimeType)
                ctx.interfaceLaws(this).fold<Exp, Exp>(Exp.And(footprint, subtype)) { acc, law -> Exp.And(acc, law) }
            } else null
            Predicate(uniquePredicateName, listOf(Declaration.LocalVarDecl(DispatchReceiverName, Type.Ref)), body)
        } else userClassUniquePredicate()
    }

    /** Whether this type's unique predicate has a built-in representation. */
    val hasBuiltinPredicate: Boolean
        get() = this == IntArrayEmbedding.classType || this == StringBuilderEmbedding.classType

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

    /**
     * The bodies of the unique predicates of this class and its superclasses for [subject], the predicates of the
     * `@Unique` properties named in [omitted] left out.
     */
    context(ctx: TypeResolver)
    fun openPredicateChain(subject: ExpEmbedding, omitted: Set<SymbolicName>): ExpEmbedding =
        uniquePredicateBody(subject, object : NestedPredicates {
            override fun ofField(step: PathStep, access: TypeInvariantEmbedding) =
                access.takeUnless { step.name in omitted }

            override fun ofSuperType(type: ClassTypeEmbedding, access: TypeInvariantEmbedding) =
                if (type == ctx.superClass(this@ClassTypeEmbedding)) TypeInvariantEmbedding { type.openPredicateChain(it, omitted) }
                else access
        })

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
        if (ctx.hasClassLaws(this@ClassTypeEmbedding)) {
            includeInvariant(TypeInvariantEmbedding { subject ->
                val boolean = buildType { boolean() }
                ExpWrapper.deferred(boolean) {
                    boolean.injection.toRef(ctx.classLawAssertion(
                        this@ClassTypeEmbedding, subject.pureToViper(toBuiltin = false, ctx),
                    ))
                }
            })
        }
        forEachSuperType {
            val superType = type.pretype as ClassTypeEmbedding
            if (ctx.isInterface(superType)) return@forEachSuperType
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
