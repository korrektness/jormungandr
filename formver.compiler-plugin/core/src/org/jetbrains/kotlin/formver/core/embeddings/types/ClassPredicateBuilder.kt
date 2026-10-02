/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings.types

import org.jetbrains.kotlin.formver.core.conversion.AccessPolicy
import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.core.embeddings.properties.BackingFieldGetter
import org.jetbrains.kotlin.formver.core.embeddings.properties.FieldEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.PropertyEmbedding
import org.jetbrains.kotlin.formver.core.linearization.pureToViper
import org.jetbrains.kotlin.formver.core.names.DispatchReceiverName
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.PermExp
import org.jetbrains.kotlin.formver.viper.ast.Predicate
import org.jetbrains.kotlin.utils.addIfNotNull

internal class ClassPredicateBuilder private constructor(
    val typeEmbedding: TypeEmbedding,
    val properties: List<PropertyEmbedding>,
    val classSuperTypes: List<ClassTypeEmbedding>,
    private val subject: ExpEmbedding,
) {
    private val body = mutableListOf<ExpEmbedding>()

    companion object {
        context(ctx: TypeResolver)
        fun build(
            name: SymbolicName,
            predicateName: SymbolicName,
            action: ClassPredicateBuilder.() -> Unit,
        ): Predicate {
            val subject = PlaceholderVariableEmbedding(DispatchReceiverName, classType(name))
            return Predicate(
                predicateName,
                listOf(subject.toLocalVarDecl()),
                body(name, subject, action).pureToViper(toBuiltin = true, ctx)
            )
        }

        /** The assertions [action] builds about [subject], an instance of the class [name]. */
        context(ctx: TypeResolver)
        fun body(name: SymbolicName, subject: ExpEmbedding, action: ClassPredicateBuilder.() -> Unit): ExpEmbedding {
            val builder = ClassPredicateBuilder(
                classType(name),
                ctx.lookupClassProperties(name),
                ctx.lookupSuperTypes(name),
                subject,
            )
            builder.action()
            return builder.body.toConjunction()
        }

        context(ctx: TypeResolver)
        private fun classType(name: SymbolicName) =
            TypeEmbedding(ctx.lookupClassTypeEmbedding(name)!!, TypeEmbeddingFlags(nullable = false))
    }

    fun includeSubTypeInvariants() = body.add(
        SubTypeInvariantEmbedding(typeEmbedding).fillHole(subject)
    )

    fun forEachPropertyField(action: PropertyAssertionsBuilder.() -> Unit) =
        properties
            .forEach { property ->
                val builder = PropertyAssertionsBuilder(subject, property)
                builder.action()
                body.addAll(builder.toAssertionsList())
            }

    fun forEachSuperType(action: TypeInvariantsBuilder.() -> Unit) =
        classSuperTypes.forEach { type ->
            val builder = TypeInvariantsBuilder(type.asTypeEmbedding())
            builder.action()
            body.addAll(builder.toInvariantsList().fillHoles(subject))
        }
}

class PropertyAssertionsBuilder(private val subject: ExpEmbedding, private val property: PropertyEmbedding) {
    private val assertions = mutableListOf<ExpEmbedding>()
    fun toAssertionsList() = assertions.toList()

    val isUnique = property.isUnique

    /** The field holding the property's value, when it has one. */
    val backingField: FieldEmbedding? = (property.getter as? BackingFieldGetter)?.field

    context(ctx: TypeResolver)
    private fun getPlainValue() = when (val getter = property.getter!!) {
        is BackingFieldGetter -> PrimitiveFieldAccess(subject, getter.field)
        else -> getter.getValueSimple(subject, ctx)
    }


    context(ctx: TypeResolver)
    fun forType(action: TypeInvariantsBuilder.() -> Unit) {
        val builder = TypeInvariantsBuilder(property.type)
        builder.action()
        assertions.addAll(builder.toInvariantsList().fillHoles(getPlainValue()))
    }

    fun forBackingField(action: BackingFieldAssertionsBuilder.() -> Unit) {
        backingField?.let { field ->
            val builder = BackingFieldAssertionsBuilder(subject, field)
            builder.action()
            assertions.addAll(builder.toAssertionsList())
        }
    }

    context(ctx: TypeResolver)
    fun addEqualsGuarantee(block: ExpEmbedding.() -> ExpEmbedding) {
        assertions.add(EqCmp(property.getter!!.getValue(subject, ctx), subject.block()))
    }
}

class BackingFieldAssertionsBuilder(private val subject: ExpEmbedding, private val field: FieldEmbedding) {
    private val assertions = mutableListOf<ExpEmbedding>()

    val isAlwaysWriteable = field.accessPolicy == AccessPolicy.ALWAYS_WRITEABLE

    fun toAssertionsList() = assertions.toList()

    fun addAccessPermissions(perm: PermExp) =
        assertions.add(FieldAccessTypeInvariantEmbedding(field, perm).fillHole(subject))


    fun forType(action: TypeInvariantsBuilder.() -> Unit) {
        val builder = TypeInvariantsBuilder(field.type)
        builder.action()
        assertions.addAll(builder.toInvariantsList().fillHoles(PrimitiveFieldAccess(subject, field)))
    }
}


class TypeInvariantsBuilder(val type: TypeEmbedding) {
    private val invariants = mutableListOf<TypeInvariantEmbedding>()
    fun toInvariantsList() = invariants.toList()

    /**
     * Add the access to the unique predicate of [type], guarded by non-nullness when [type] is nullable. [replace]
     * gives the assertion that stands for the access, `null` to leave it out.
     */
    context(ctx: TypeResolver)
    fun addAccessToUniquePredicate(replace: (TypeInvariantEmbedding) -> TypeInvariantEmbedding? = { it }) =
        invariants.addIfNotNull(
            type.pretype.uniquePredicateAccessInvariant(ctx)?.let(replace)?.let(type.flags::adjustInvariant)
        )

    fun includeSubTypeInvariants() = invariants.add(
        SubTypeInvariantEmbedding(type)
    )
}
