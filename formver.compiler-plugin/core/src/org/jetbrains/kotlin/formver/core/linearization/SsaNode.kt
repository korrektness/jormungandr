package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.formver.common.SnaktInternalException
import org.jetbrains.kotlin.formver.core.names.SsaVariableName
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Exp

/**
 * A node in a SSA-Graph
 */
sealed interface SsaNode {
    /**
     * Function resolves source names to their SSAVariableName
     * Fallsback to provided name if no such name is found
     */
    fun resolveVariableName(name: SymbolicName): SymbolicName
}

class SsaStartNode : SsaNode {
    override fun resolveVariableName(name: SymbolicName): SymbolicName =
        name
}

/**
 * [fullBranchingCondition] holds exactly where control reaches this block: it is `false` after a return.
 */
class SsaBlockNode(
    private val predecessor: SsaNode,
    val fullBranchingCondition: Exp
) : SsaNode {
    val latestName: MutableMap<SymbolicName, SsaVariableName> = mutableMapOf()

    val isUnreachable: Boolean
        get() = fullBranchingCondition == Exp.BoolLit(false)

    fun generateBranchingBlockNodeFromThisNode(condition: Exp): SsaBlockNode =
        SsaBlockNode(
            this,
            when (fullBranchingCondition) {
                Exp.BoolLit(true) -> condition
                Exp.BoolLit(false) -> fullBranchingCondition
                else -> Exp.And(fullBranchingCondition, condition)
            },
        )

    /** The block following a return from this one. */
    fun generateUnreachableBlockNodeFromThisNode(): SsaBlockNode = SsaBlockNode(this, Exp.BoolLit(false))

    context(ssaConverter: SsaConverter)
    fun updateLatestName(name: SymbolicName): SsaVariableName =
        ssaConverter.generateFreshSsaName(name).also { latestName[name] = it }

    override fun resolveVariableName(name: SymbolicName): SymbolicName =
        latestName[name] ?: predecessor.resolveVariableName(name)
}

class SsaJoinNode(
    private val leftPredecessor: SsaBlockNode,
    private val rightPredecessor: SsaBlockNode,
    private val mostRecentBranchingCondition: Exp,
    private val ssaConverter: SsaConverter
) : SsaNode {
    private val lookupCache: MutableMap<SymbolicName, SymbolicName> = mutableMapOf()

    override fun resolveVariableName(name: SymbolicName): SymbolicName =
        lookupCache[name] ?: resolveNameFromPredecessors(name)

    private fun resolveNameFromPredecessors(name: SymbolicName): SymbolicName {
        if (leftPredecessor.isUnreachable) return rightPredecessor.resolveVariableName(name)
        if (rightPredecessor.isUnreachable) return leftPredecessor.resolveVariableName(name)
        val leftIncoming = leftPredecessor.resolveVariableName(name)
        val rightIncoming = rightPredecessor.resolveVariableName(name)
        return if (rightIncoming == leftIncoming) {
            leftIncoming
        } else if (leftIncoming is SsaVariableName && rightIncoming is SsaVariableName) {
            val ssaName = ssaConverter.generateFreshSsaName(leftIncoming.baseName)
            ssaConverter.addPhiAssignment( // Resolve to phi assignment
                mostRecentBranchingCondition,
                leftIncoming,
                rightIncoming,
                ssaName
            )
            ssaName
        } else {
            throw SnaktInternalException(
                ssaConverter.source,
                "Phi Assignments may only be created for SSA variables"
            )
        }
    }
}
