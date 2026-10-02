package org.jetbrains.kotlin.formver.core.embeddings.callables

import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.core.conversion.TypeResolver
import org.jetbrains.kotlin.formver.core.conversion.stdLibPostconditions
import org.jetbrains.kotlin.formver.core.conversion.stdLibPreconditions
import org.jetbrains.kotlin.formver.core.embeddings.SourceRole
import org.jetbrains.kotlin.formver.core.embeddings.expression.AccEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.PredicateAccessPermissions
import org.jetbrains.kotlin.formver.core.embeddings.expression.VariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.withAccessRole
import org.jetbrains.kotlin.formver.core.linearization.OwnedPath
import org.jetbrains.kotlin.formver.core.linearization.render
import org.jetbrains.kotlin.formver.core.names.sourceSpelling
import org.jetbrains.kotlin.formver.core.purity.preorder


// Container for the generated conditions
data class FunctionContract(
    val preconditions: List<ExpEmbedding>, val postconditions: List<ExpEmbedding>
)


fun NamedFunctionSignature.buildConditions(
    typeResolver: TypeResolver, block: FunctionConditionBuilder.() -> Unit
): FunctionContract {
    return FunctionConditionBuilder(this, typeResolver).apply(block).build()
}

class FunctionConditionBuilder(
    private val signature: NamedFunctionSignature, private val typeResolver: TypeResolver
) {
    private val preconditions = mutableListOf<ExpEmbedding>()
    private val postconditions = mutableListOf<ExpEmbedding>()

    fun preconditions(block: PreconditionScope.() -> Unit) {
        PreconditionScope(signature, typeResolver, preconditions).apply(block)
    }

    fun postconditions(block: PostconditionScope.() -> Unit) {
        PostconditionScope(signature, typeResolver, postconditions).apply(block)
    }


    fun addPreconditions(list: List<ExpEmbedding>) = preconditions.addAll(list)
    fun addPostconditions(list: List<ExpEmbedding>) = postconditions.addAll(list)

    fun userFunctionPreconditions() {
        preconditions {
            args {
                pureInvariants()
                provenInvariants()
                accessInvariants()
                if (variable.isUnique) uniquePredicateInvariants()
            }
            stdLib()
        }
    }

    fun userFunctionPostcondition() {
        postconditions {
            args {
                accessInvariants()
                pureInvariants()
                if (variable.isBorrowed && variable.isUnique && !signature.isPure) uniquePredicateInvariants()
            }
            returns {
                pureInvariants()
                provenInvariants()
                if (!signature.isPure) {
                    accessInvariants()
                    if (signature.callableType.returnsUnique) {
                        uniquePredicateInvariants()
                    }
                }
            }
            stdLib()
        }
    }

    fun userFunctionContract() {
        userFunctionPreconditions()
        userFunctionPostcondition()
    }

    private fun checkNoPermInPurePostcondition() {
        if (signature.isPure) {
            postconditions.forEach { postcondition ->
                val isValid =
                    postcondition.preorder()
                        .all { it.first !is AccEmbedding && it.first !is PredicateAccessPermissions }
                if (!isValid) throw SnaktInternalException(
                    signature.symbol?.source,
                    "Postcondition tries to acquire permissions, which is not allowed in a function"
                )
            }
        }
    }

    fun build(): FunctionContract {
        checkNoPermInPurePostcondition()
        return FunctionContract(preconditions, postconditions)
    }
}

class PreconditionScope(
    private val signature: NamedFunctionSignature,
    private val typeResolver: TypeResolver,
    private val list: MutableList<ExpEmbedding>
) {
    private val site = SourceRole.Ownership.Site.Precondition(signature.spelling)

    fun args(block: VariableScope.() -> Unit) {
        signature.formalArgs.forEach { variable -> VariableScope(variable, list, typeResolver, site).apply(block) }
    }

    fun stdLib() {
        list.addAll(signature.stdLibPreconditions(typeResolver))
    }
}


class VariableScope(
    val variable: VariableEmbedding,
    private val list: MutableList<ExpEmbedding>,
    private val typeResolver: TypeResolver,
    private val site: SourceRole.Ownership.Site,
) {
    fun pureInvariants() = list.addAll(variable.pureInvariants())
    fun accessInvariants() = list.addAll(variable.accessInvariants(typeResolver))
    fun provenInvariants() = list.addAll(variable.provenInvariants())

    fun uniquePredicateInvariants() {
        val invariant = variable.type.uniquePredicateAccessInvariant(typeResolver)
        // The predicates of `@Manual` classes are folded by the user, so a failure to hold one is the user's error.
        val manual = with(typeResolver) { (variable.type.pretype as? ClassTypeEmbedding)?.isManual } != false
        val role = SourceRole.Ownership(OwnedPath(variable).render(), site).takeUnless { manual }
        (if (role == null) invariant else invariant?.withAccessRole(role))?.fillHole(variable)?.let { inv ->
            list.add(inv)
        }
    }
}

class PostconditionScope(
    private val signature: NamedFunctionSignature,
    private val typeResolver: TypeResolver,
    private val list: MutableList<ExpEmbedding>
) {
    private val site = SourceRole.Ownership.Site.Postcondition(signature.spelling)

    fun args(block: VariableScope.() -> Unit) {
        signature.formalArgs.forEach { variable -> VariableScope(variable, list, typeResolver, site).apply(block) }
    }

    fun returns(block: VariableScope.() -> Unit) {
        VariableScope(signature.returns, list, typeResolver, site).apply(block)
    }

    fun stdLib() {
        list.addAll(signature.stdLibPostconditions(signature.returns, typeResolver))
    }
}

/** The function's name as the Kotlin source spells it. */
private val NamedFunctionSignature.spelling: String
    get() = labelName ?: name.sourceSpelling ?: "this function"
