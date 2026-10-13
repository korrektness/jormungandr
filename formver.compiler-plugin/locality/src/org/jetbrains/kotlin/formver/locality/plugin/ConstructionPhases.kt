/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.locality.plugin

import org.jetbrains.kotlin.contracts.description.isDefinitelyVisited
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.cfa.evaluatedInPlace
import org.jetbrains.kotlin.fir.analysis.cfa.requiresInitialization
import org.jetbrains.kotlin.fir.analysis.cfa.util.PropertyInitializationInfoData
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.utils.hasBackingField
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ControlFlowGraph
import org.jetbrains.kotlin.fir.resolve.dfa.controlFlowGraph
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol

/** Kotlin definite-assignment phases of one class's initialization graph. */
@OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
class ConstructionPhases(owner: FirRegularClassSymbol) {
    private val properties = owner.fir.declarations.filterIsInstance<FirProperty>()
        .map { it.symbol }.filter { it.requiresInitialization(isForInitialization = true) }.toSet()
    private val graph = owner.fir.controlFlowGraphReference?.controlFlowGraph
        ?: error("Source class initialization has no control-flow graph.")
    private val initialization = PropertyInitializationInfoData(properties, emptySet(), owner, graph)
    private val stored = owner.fir.declarations.filterIsInstance<FirProperty>().filter { it.hasBackingField }.map { it.symbol }.toSet()
    private val storedInitialization by lazy { PropertyInitializationInfoData(stored, emptySet(), owner, graph) }
    private val nodes = initializationNodes(graph)
    private val nodesByElement = nodes.groupBy { it.fir }

    /** Whether all stored properties are assigned on every live path reaching [element]. */
    fun phaseTwoBefore(element: FirElement): Boolean? {
        val matching = nodesByElement[element].orEmpty().filterNot { it.isDead }
        if (matching.isEmpty()) return null
        return matching.all { node ->
            node.previousNodes.filterNot { it.isDead }.all(::assignedAfter)
        }
    }

    /** Whether [property] is assigned on every live path reaching [element]. */
    fun assignedBefore(property: FirPropertySymbol, element: FirElement): Boolean {
        if (property !in stored) return false
        val matching = nodesByElement[element].orEmpty().filterNot { it.isDead }
        return matching.isNotEmpty() && matching.all { node ->
            node.previousNodes.filterNot { it.isDead }.all { previous ->
                val paths = storedInitialization.getValue(previous).values
                paths.isNotEmpty() && paths.all { it[property]?.range?.isDefinitelyVisited() == true }
            }
        }
    }

    val isFullyAssigned: Boolean get() = graph.exitNode.isDead || assignedAfter(graph.exitNode)

    private fun assignedAfter(node: CFGNode<*>): Boolean {
        if (properties.isEmpty()) return true
        val paths = initialization.getValue(node).values
        return paths.isNotEmpty() && paths.all { state ->
            properties.all { property: FirPropertySymbol ->
                val event = state[property]
                event?.range?.isDefinitelyVisited() == true && !event.mustBeLateinit
            }
        }
    }

    private fun initializationNodes(root: ControlFlowGraph): List<CFGNode<*>> = buildList {
        addAll(root.nodes)
        for (child in root.subGraphs) {
            val declaration = child.declaration ?: continue
            if (declaration.evaluatedInPlace || declaration is FirProperty || declaration is FirAnonymousInitializer) {
                addAll(initializationNodes(child))
            }
        }
    }
}
