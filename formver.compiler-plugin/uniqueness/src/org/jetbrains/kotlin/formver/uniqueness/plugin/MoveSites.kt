/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.jetbrains.kotlin.fir.analysis.cfa.util.PathAwareControlFlowGraphVisitor
import org.jetbrains.kotlin.fir.analysis.cfa.util.PathAwareControlFlowInfo
import org.jetbrains.kotlin.fir.analysis.cfa.util.merge
import org.jetbrains.kotlin.fir.analysis.cfa.util.previousCfgNodes
import org.jetbrains.kotlin.fir.analysis.cfa.util.transformValues
import org.jetbrains.kotlin.fir.analysis.cfa.util.traverseToFixedPoint
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNodeWithSubgraphs
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ControlFlowGraph

/** For each moved path, the nodes whose moves of it reach the current point. */
typealias MoveSites = PersistentMap<Path, PersistentSet<CFGNode<*>>>

typealias PathAwareMoveSites = PathAwareControlFlowInfo<Path, PersistentSet<CFGNode<*>>>

/**
 * Data-flow analysis that tracks, alongside a finished uniqueness analysis, where each moved path was moved.
 *
 * A path that is moved after a node keeps the sites it had before the node. When it had none, the node is its site if
 * the node moves it, that is, if [transfer] moves it from the state before the node with the path not moved. The input
 * state of a node in a loop already has the paths the loop moves moved, so this test, rather than a comparison of the
 * node's input and output, is what finds a move in a loop. A path that is not moved after a node has no sites.
 *
 * A join that truncates the uniqueness state (see [truncate]) moves a path whose descendants moved without moving it
 * itself. Such a path takes the sites of those descendants.
 */
private class MoveSitesAnalyzer(
    private val uniquenessStateFlows: UniquenessStateFlows,
    private val transfer: (CFGNode<*>, UniquenessState) -> UniquenessState,
) : PathAwareControlFlowGraphVisitor<Path, PersistentSet<CFGNode<*>>>() {
    override fun mergeInfo(a: MoveSites, b: MoveSites, node: CFGNode<*>): MoveSites =
        a.merge(b) { left, right -> left.addAll(right) }

    override fun visitSubGraph(node: CFGNodeWithSubgraphs<*>, graph: ControlFlowGraph): Boolean =
        graph.extendsLocalFlow

    private fun CFGNode<*>.moves(path: Path): Boolean {
        val input = uniquenessStateFlows.readInputUniquenessStateOf(this) ?: return true
        val substate = input.find(path) ?: return true
        if (substate.data != Uniqueness.Moved) return true
        val unmoved = input.insert(path, substate.copy(data = Uniqueness.Unique))
        return transfer(this, unmoved).find(path)?.data == Uniqueness.Moved
    }

    override fun visitNode(
        node: CFGNode<*>,
        data: PathAwareMoveSites,
    ): PathAwareMoveSites {
        val movedPaths = uniquenessStateFlows.readOutputUniquenessStateOf(node).enumerateInconsistentPaths().toList()

        return data.transformValues { sites ->
            movedPaths.fold(persistentMapOf()) { result, path ->
                val pathSites = sites[path]
                    ?: persistentSetOf<CFGNode<*>>(node).takeIf { node.moves(path) }
                    ?: sites.descendantSitesOf(path)
                if (pathSites != null) result.put(path, pathSites) else result
            }
        }
    }
}

private fun MoveSites.descendantSitesOf(path: Path): PersistentSet<CFGNode<*>>? =
    entries
        .filter { (descendant, _) -> descendant.size > path.size && descendant.subList(0, path.size) == path }
        .fold(persistentSetOf<CFGNode<*>>()) { result, (_, sites) -> result.addAll(sites) }
        .takeIf { it.isNotEmpty() }

/**
 * Resolves, for each node of [this] graph, the move sites of the paths moved after it.
 */
context(context: CheckerContext)
fun ControlFlowGraph.resolveMoveSites(
    uniquenessStateFlows: UniquenessStateFlows,
): Map<CFGNode<*>, PathAwareMoveSites> =
    traverseToFixedPoint(MoveSitesAnalyzer(uniquenessStateFlows, uniquenessTransfer()))

/**
 * Reads the sites at which [path] was moved on the routes into [node].
 */
fun Map<CFGNode<*>, PathAwareMoveSites>.readInputMoveSitesOf(
    node: CFGNode<*>,
    path: Path,
): Set<CFGNode<*>> =
    node.previousCfgNodes.flatMapTo(mutableSetOf()) { predecessor ->
        this[predecessor]?.values?.flatMap { it[path].orEmpty() }.orEmpty()
    }
