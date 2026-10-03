package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.core.conversion.FreshEntityProducer
import org.jetbrains.kotlin.formver.core.names.SsaVariableName
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Declaration
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Type

class SsaConverter(
    val source: KtSourceElement? = null,
) {
    private var head: SsaBlockNode = SsaBlockNode(SsaStartNode(), Exp.BoolLit(true))
    private val ssaAssignments: MutableList<Assignment> = mutableListOf()
    private val returnExpressions: MutableList<Pair<Exp, Exp>> = mutableListOf()
    private val accessInvariants: MutableMap<SsaVariableName, List<Exp.PredicateAccess>> = mutableMapOf()

    // Produce new ssa names for a source variable name
    private val ssaNameProducers: MutableMap<SymbolicName, FreshEntityProducer<SsaVariableName, SymbolicName>> =
        mutableMapOf()

    fun branch(
        condition: Exp,
        thenBlock: () -> Unit,
        elseBlock: () -> Unit
    ) {
        val splitPoint = head
        val thenStart = splitPoint.generateBranchingBlockNodeFromThisNode(condition)
        head = thenStart
        thenBlock()
        val thenResultHead = head
        val elseStart = splitPoint.generateBranchingBlockNodeFromThisNode(Exp.Not(condition))
        head = elseStart
        elseBlock()
        val elseResultHead = head
        val joinNode = SsaJoinNode(
            thenResultHead,
            elseResultHead,
            condition,
            this
        )
        val thenReach = thenResultHead.fullBranchingCondition
        val elseReach = elseResultHead.fullBranchingCondition
        val joinReach = when {
            thenResultHead.isUnreachable -> elseReach
            elseResultHead.isUnreachable -> thenReach
            thenReach == thenStart.fullBranchingCondition && elseReach == elseStart.fullBranchingCondition ->
                splitPoint.fullBranchingCondition

            else -> Exp.Or(thenReach, elseReach)
        }
        head = SsaBlockNode(joinNode, joinReach)
    }

    fun constructExpression(): Exp {
        if (returnExpressions.isEmpty()) throw SnaktInternalException(
            source,
            "No return expression was found for translation"
        )
        val defaultBody = returnExpressions.last().second
        val bodyExp = returnExpressions.dropLast(1).foldRight(defaultBody) { expPair, elseBranch ->
            Exp.TernaryExp(
                expPair.first,
                expPair.second,
                elseBranch
            )
        }
        return ssaAssignments.foldRight(bodyExp) { assignment, innerScope -> innerScope.bind(assignment) }
            .hoistUnfoldings()
    }

    /** [value] is evaluated where [guard] holds; elsewhere the variable is [default]. */
    private data class Assignment(val name: SsaVariableName, val guard: Exp, val value: Exp, val default: Exp)

    /**
     * [this] with [assignment] bound in the innermost arm of its conditionals that holds every use of it, reached
     * through the values and bodies of let bindings, or around [this] when there is no such arm. [known] are the
     * conditions of the arms entered on the way; when they imply the guard, the value is bound unguarded, so the
     * binding and its uses can share one `unfolding`. When [this] is just the variable, the value replaces it: Silicon
     * does not relate a recursive application inside such a `let` to the function's unrolled definition.
     */
    private fun Exp.bind(assignment: Assignment, known: Set<Exp> = emptySet()): Exp {
        bindInArm(assignment, known)?.let { return it }
        val value = with(assignment) {
            if (known.containsAll(guard.conjuncts())) value else Exp.TernaryExp(guard, value, default)
        }
        if (this is Exp.LocalVar && name == assignment.name) return value
        return Exp.LetBinding(Declaration.LocalVarDecl(assignment.name, Type.Ref), value, this)
    }

    /** [this] with [assignment] bound inside one arm of a conditional, or `null` when no arm holds every use of it. */
    private fun Exp.bindInArm(assignment: Assignment, known: Set<Exp>): Exp? {
        fun Exp.uses() = mentions(assignment.name)
        return when (this) {
            is Exp.LetBinding -> when {
                varExp.uses() && !body.uses() -> varExp.bindInArm(assignment, known)?.let { copy(varExp = it) }
                body.uses() && !varExp.uses() -> body.bindInArm(assignment, known)?.let { copy(body = it) }
                else -> null
            }

            is Exp.TernaryExp -> when {
                condExp.uses() -> null
                thenExp.uses() && !elseExp.uses() -> copy(thenExp = thenExp.bind(assignment, known + condExp.conjuncts()))
                elseExp.uses() && !thenExp.uses() -> copy(elseExp = elseExp.bind(assignment, known + condExp.negations(known)))
                else -> null
            }

            else -> null
        }
    }

    /**
     * What `!this` implies given [known]: itself, and the negation of the one condition it extends that [known] does
     * not imply. A branching condition extends its block's condition with `&&`, so the conditions it extends are its
     * left-nested conjuncts.
     */
    private fun Exp.negations(known: Set<Exp>): List<Exp> {
        val unknown = extendedConditions().filterNot { known.containsAll(it.conjuncts()) }
        return listOfNotNull(Exp.Not(this), unknown.singleOrNull()?.let { Exp.Not(it) })
    }

    private fun Exp.extendedConditions(): List<Exp> =
        if (this is Exp.And) left.extendedConditions() + right else listOf(this)

    private fun Exp.conjuncts(): List<Exp> = when (this) {
        Exp.BoolLit(true) -> emptyList()
        is Exp.And -> left.conjuncts() + right.conjuncts()
        else -> listOf(this)
    }

    fun generateFreshSsaName(name: SymbolicName): SsaVariableName {
        val producer = ssaNameProducers.getOrPut(name) { FreshEntityProducer(::SsaVariableName) }
        return producer.getFresh(name)
    }

    fun addAssignment(
        name: SymbolicName,
        varExp: Exp,
        newVarAccessInvariants: List<Exp.PredicateAccess> = emptyList()
    ) {
        val ssaName = head.updateLatestName(name)
        accessInvariants[ssaName] = newVarAccessInvariants
        varExp.propagateAccessInvariants(ssaName)
        addGuardedAssignment(ssaName, varExp.withAccessInvariants(ssaName))
    }

    /** The predicate accesses that an expression reading through [variable] is wrapped in, outermost first. */
    fun accessInvariantsOf(variable: Exp.LocalVar): List<Exp.PredicateAccess> =
        accessInvariants[variable.name].orEmpty()

    fun addPhiAssignment(condition: Exp, left: SsaVariableName, right: SsaVariableName, name: SsaVariableName) {
        if (left.baseName != right.baseName) {
            throw SnaktInternalException(
                source,
                "Phi Assignments may only be created for SSA variables referring to the same source variable."
            )
        }
        val phiExpression = Exp.TernaryExp(
            condition,
            Exp.LocalVar(left, Type.Ref),
            Exp.LocalVar(right, Type.Ref)
        )
        phiExpression.propagateAccessInvariants(name)
        addGuardedAssignment(name, phiExpression.withAccessInvariants(name))
    }

    fun addReturn(returnExp: Exp) {
        if (head.isUnreachable) return
        returnExpressions.add(head.fullBranchingCondition to returnExp)
        head = head.generateUnreachableBlockNodeFromThisNode()
    }

    fun resolveVariableName(name: SymbolicName): SymbolicName {
        return head.resolveVariableName(name)
    }

    private fun addGuardedAssignment(name: SsaVariableName, varExp: Exp) {
        val defaultExpression = varExp.type.defaultExpression() ?: throw SnaktInternalException(
            source,
            "Tried to assign a variable without a default expression"
        )
        ssaAssignments.add(Assignment(name, head.fullBranchingCondition, varExp, defaultExpression))
    }

    private fun Exp.withAccessInvariants(name: SsaVariableName): Exp =
        when (this) {
            is Exp.FieldAccess, is Exp.FuncApp, is Exp.DomainFuncApp -> accessInvariants[name]?.foldRight(this) { invariant, acc ->
                Exp.Unfolding(invariant, acc)
            } ?: this

            else -> this
        }

    private fun mergeAccessInvariants(from: List<SymbolicName>, newName: SsaVariableName) {
        val mergedInvariants = from.mapNotNull { accessInvariants[it] }.flatten() + accessInvariants[newName].orEmpty()
        accessInvariants[newName] = mergedInvariants.distinct()
    }

    private fun Exp.propagateAccessInvariants(to: SsaVariableName) {
        var from: Exp? = null
        when (this) {
            is Exp.LocalVar -> from = this
            is Exp.FieldAccess -> from = this.rcv
            is Exp.FuncApp -> this.args.forEach { it.propagateAccessInvariants(to) }
            is Exp.DomainFuncApp -> {
                this.args.forEach { it.propagateAccessInvariants(to) }
            }
            // TODO: Determine how to handle the access invariants of a Ternary
            else -> {}
        }
        if (from == null) return
        if (from !is Exp.LocalVar) throw SnaktInternalException(
            source,
            "Access sources must be local variables, got ${from::class.simpleName}"
        )
        mergeAccessInvariants(listOf(from.name), to)
    }
}
