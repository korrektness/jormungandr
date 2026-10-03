/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.embeddings

import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.formver.viper.ast.Info

sealed interface SourceRole {
    data class ListElementAccessCheck(val accessType: AccessCheckType) : SourceRole {
        enum class AccessCheckType {
            LESS_THAN_ZERO,
            GREATER_THAN_LIST_SIZE
        }
    }

    /** A bound on the index of an array element access; [array] is the variable read as the array, if any. */
    data class ArrayElementAccessCheck(val bound: Bound, val array: FirBasedSymbol<*>?) : SourceRole {
        enum class Bound {
            NEGATIVE,
            NOT_BELOW_SIZE
        }
    }

    /**
     * A permission SnaKt generates for an owned path, which the uniqueness checker guarantees is available. [path] is
     * the path as the Kotlin source spells it, ready for messages.
     */
    data class Ownership(val path: String, val site: Site) : SourceRole {
        sealed interface Site {
            /** An `unfold` of the path's predicate, exposing its fields. */
            data object Unfold : Site

            /** A `fold` of the path's predicate. */
            data object Fold : Site

            /** A `fold` of a new object's predicates, which takes those of the arguments stored in it; [path] is the class. */
            data object Construction : Site

            /** The exhale and inhale that havoc what the path holds after a call that may have written through it. */
            data object Havoc : Site

            /** The invariant of a loop head. */
            data object LoopHead : Site

            /** A precondition of [function]; the path is one of its parameters. */
            data class Precondition(val function: String) : Site

            /** A postcondition of [function]; the path is one of its parameters or its result. */
            data class Postcondition(val function: String) : Site
        }
    }

    /**
     * A check that an override refines the contract of the declaration it overrides, which [overridden] names as the
     * Kotlin source spells it.
     */
    data class OverrideRefinement(val overridden: String, val clause: Clause) : SourceRole {
        enum class Clause {
            /** The override's precondition must follow from the overridden precondition. */
            PRECONDITION,

            /** The override's postcondition must establish the overridden postcondition. */
            POSTCONDITION
        }
    }

    data class ConditionalEffect(val effect: ReturnsEffect, val condition: Condition) : SourceRole
    data class FirSymbolHolder(val firSymbol: FirBasedSymbol<*>) : SourceRole, Condition

    sealed interface SubListCreation : SourceRole {
        data object CheckInSize : SubListCreation
        data object CheckNegativeIndices : SubListCreation
    }

    sealed interface ReturnsEffect : SourceRole {
        data object Wildcard : ReturnsEffect
        data class Bool(val bool: Boolean) : ReturnsEffect {
            override fun toString(): String = bool.toString()
        }

        data class Null(val negated: Boolean) : ReturnsEffect {
            override fun toString(): String = if (negated) {
                "non-null"
            } else {
                "null"
            }
        }
    }

    sealed interface Condition : SourceRole {
        data class IsType(
            val targetVariable: FirBasedSymbol<*>,
            val expectedType: ConeKotlinType,
            val negated: Boolean = false
        ) : Condition

        data class IsNull(val targetVariable: FirBasedSymbol<*>, val negated: Boolean = false) : Condition
        data class Constant(val literal: Boolean) : Condition
        data class Conjunction(val lhs: Condition, val rhs: Condition) : Condition
        data class Disjunction(val lhs: Condition, val rhs: Condition) : Condition
        data class Negation(val arg: Condition) : Condition
    }
}


val SourceRole?.asInfo: Info
    get() = when (this) {
        null -> Info.NoInfo
        else -> Info.Wrapped(this)
    }
