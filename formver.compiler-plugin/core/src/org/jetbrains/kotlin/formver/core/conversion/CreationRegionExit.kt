/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.conversion

import org.jetbrains.kotlin.formver.core.embeddings.expression.*
import org.jetbrains.kotlin.formver.viper.SymbolicName

/** Whether the region's fall-through is unreachable, resolving jumps to labels inside the region. */
internal fun ExpEmbedding.exitsCreationRegion(): Boolean = CreationRegionGraph().exits(this)

private class CreationRegionGraph {
    private val edges = mutableListOf<MutableList<Int>>()
    private val labels = mutableMapOf<SymbolicName, Int>()
    private val jumps = mutableListOf<Pair<Int, SymbolicName>>()

    private fun node(vararg successors: Int): Int {
        edges += successors.toMutableList()
        return edges.lastIndex
    }

    private fun jump(name: SymbolicName): Int = node().also { jumps += it to name }

    private fun build(expression: ExpEmbedding, next: Int): Int = when (val exp = expression.ignoringCastsAndMetaNodes()) {
        is Return -> build(exp.returnExp, exp.target.label?.let { jump(it.name) } ?: node())
        is Goto -> jump(exp.target.name)
        is Block -> exp.exps.asReversed().fold(next) { successor, statement -> build(statement, successor) }
        is If -> build(exp.condition, node(build(exp.thenBranch, next), build(exp.elseBranch, next)))
        is Elvis -> build(exp.left, node(next, build(exp.right, next)))
        is SequentialLogicOperatorEmbedding -> build(exp.left, node(next, build(exp.right, next)))
        is LabelExp -> next.also { labels[exp.label.name] = it }
        is GotoChainNode -> build(exp.exp, jump(exp.next.name)).also { entry ->
            exp.label?.let { labels[it.name] = entry }
        }
        is While -> {
            val head = node(next)
            val condition = build(exp.condition, head)
            labels[exp.breakLabelName] = next
            labels[exp.continueLabelName] = condition
            edges[head] += build(exp.body, condition)
            condition
        }
        is NonDeterministically -> node(next, build(exp.exp, next))
        is FunctionExp -> build(exp.body, next).also { labels[exp.returnLabel.name] = next }
        else -> exp.children().toList().asReversed().fold(next) { successor, child -> build(child, successor) }
    }

    fun exits(expression: ExpEmbedding): Boolean {
        val fallThrough = node()
        val entry = build(expression, fallThrough)
        for ((node, name) in jumps) labels[name]?.let { edges[node] += it }
        val visited = mutableSetOf<Int>()
        val pending = ArrayDeque<Int>()
        pending.add(entry)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node == fallThrough) return false
            if (visited.add(node)) pending.addAll(edges[node])
        }
        return true
    }
}
