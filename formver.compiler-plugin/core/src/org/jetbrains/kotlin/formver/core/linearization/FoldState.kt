/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.core.asPosition
import org.jetbrains.kotlin.formver.core.embeddings.expression.ExpEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.expression.FieldAccess
import org.jetbrains.kotlin.formver.core.embeddings.expression.VariableEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.properties.FieldEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.ClassTypeEmbedding
import org.jetbrains.kotlin.formver.core.embeddings.types.TypeEmbedding
import org.jetbrains.kotlin.formver.viper.SymbolicName
import org.jetbrains.kotlin.formver.viper.ast.Exp
import org.jetbrains.kotlin.formver.viper.ast.Stmt

/**
 * A path the fold state can track: a root variable followed by `@Unique` fields.
 */
data class OwnedPath(val root: VariableEmbedding, val fields: List<FieldEmbedding> = emptyList()) {
    val type: TypeEmbedding
        get() = fields.lastOrNull()?.type ?: root.type

    operator fun plus(field: FieldEmbedding) = OwnedPath(root, fields + field)
}

/**
 * The owned path this expression reads, or `null` when it is not a variable followed by reads of `@Unique` fields.
 */
fun ExpEmbedding.ownedPath(): OwnedPath? = when (val exp = ignoringCastsAndMetaNodes()) {
    is VariableEmbedding -> OwnedPath(exp)
    is FieldAccess -> if (exp.field.isUnique) exp.receiver.ownedPath()?.plus(exp.field) else null
    else -> null
}

/**
 * Raised when the linearized code needs a unique predicate that the fold state does not hold.
 * The converter reports it as a conversion error.
 */
class FoldStateException(val source: KtSourceElement?, message: String) : Exception(message)

/**
 * The unique predicates the linearized code holds for owned paths, and how far each one is unfolded.
 *
 * Roots are keyed by variable name and children by field name. A root missing from the map holds nothing.
 * Only paths whose static type is a class that is not `@Manual` are tracked. After an unconditional jump the state
 * is dead: it holds nothing and joins as the identity.
 */
class FoldState {
    internal sealed interface Status {
        data object Folded : Status
        data object Absent : Status

        /**
         * The predicates of [opened] are unfolded, the static class first. The predicate of the next class in the
         * chain is held folded.
         */
        data class Open(val opened: List<ClassTypeEmbedding>) : Status
    }

    internal class Node(val type: TypeEmbedding, var status: Status) {
        /** Entries for `@Unique` fields of opened classes. A field without an entry is held folded. */
        val children: MutableMap<SymbolicName, Pair<FieldEmbedding, Node>> = mutableMapOf()

        fun deepCopy(): Node = Node(type, status).also { copy ->
            children.forEach { (name, entry) -> copy.children[name] = entry.first to entry.second.deepCopy() }
        }
    }

    /**
     * A copy of the state at a program point, for joining at labels and branch merges. It is opaque outside
     * [FoldState], so it has no value semantics.
     */
    @Suppress("UseDataClass")
    class Snapshot internal constructor(internal val roots: Map<SymbolicName, Pair<VariableEmbedding, Node>>?)

    private var roots: MutableMap<SymbolicName, Pair<VariableEmbedding, Node>>? = mutableMapOf()

    /** Labels that jumps have reached, with the joined state of those jumps. */
    private val pendingJumps: MutableMap<SymbolicName, Snapshot> = mutableMapOf()

    private fun LinearizationContext.trackedClass(type: TypeEmbedding): ClassTypeEmbedding? =
        (type.pretype as? ClassTypeEmbedding)?.takeUnless { with(typeResolver) { it.isManual } }

    /** Mark [path] as holding its folded predicate. */
    fun acquire(ctx: LinearizationContext, path: OwnedPath) {
        if (ctx.trackedClass(path.type) == null) return
        val node = Node(path.type, Status.Folded)
        val live = roots ?: return
        if (path.fields.isEmpty()) {
            live[path.root.name] = path.root to node
        } else {
            val parent = reach(ctx, OwnedPath(path.root, path.fields.dropLast(1)), path.fields.last())
                ?: throw notHeld(ctx)
            parent.children[path.fields.last().name] = path.fields.last() to node
        }
    }

    /** Whether the predicate of [path] is held, possibly nested in the folded predicate of an ancestor. */
    fun holds(path: OwnedPath): Boolean {
        var node = roots?.get(path.root.name)?.second ?: return false
        for (field in path.fields) {
            if (node.status == Status.Absent) return false
            node = node.children[field.name]?.second ?: return true
        }
        return node.status != Status.Absent
    }

    /** Mark [path] as holding nothing, emitting nothing. The predicate, if any, is leaked. */
    fun release(ctx: LinearizationContext, path: OwnedPath) {
        if (ctx.trackedClass(path.type) == null || !holds(path)) return
        if (path.fields.isEmpty()) {
            roots?.remove(path.root.name)
            return
        }
        val field = path.fields.last()
        val parent = reach(ctx, OwnedPath(path.root, path.fields.dropLast(1)), field) ?: return
        parent.children[field.name] = field to Node(field.type, Status.Absent)
    }

    /** Unfold what is needed to access [field] on [path]. */
    fun open(ctx: LinearizationContext, path: OwnedPath, field: FieldEmbedding) {
        if (roots == null) return
        reach(ctx, path, field) ?: throw notHeld(ctx)
    }

    /** Fold [path] completely. Its subtree must have no moved-out field. */
    fun close(ctx: LinearizationContext, path: OwnedPath) {
        if (roots == null || ctx.trackedClass(path.type) == null) return
        if (!holds(path)) throw notHeld(ctx)
        var node = roots!![path.root.name]?.second ?: return
        var exp = path.root.toViperExp(ctx)
        for (field in path.fields) {
            node = node.children[field.name]?.second ?: return
            exp = Exp.FieldAccess(exp, field.toViper(), ctx.source.asPosition)
        }
        closeNode(ctx, node, exp)
    }

    /** Move the predicate of [src] to [dst]: [src] holds nothing afterwards. [src] must be folded. */
    fun transfer(ctx: LinearizationContext, src: OwnedPath, dst: OwnedPath) {
        if (roots == null) return
        release(ctx, src)
        acquire(ctx, dst)
    }

    /**
     * Fold every root that can be folded and forget the others, so that the state can be joined with another.
     */
    fun normalize(ctx: LinearizationContext) {
        val live = roots ?: return
        for ((name, entry) in live.entries.toList()) {
            val (variable, node) = entry
            if (node.hasHole()) live.remove(name) else closeNode(ctx, node, variable.toViperExp(ctx))
        }
    }

    fun snapshot(): Snapshot = Snapshot(roots?.copyRoots())

    fun restore(snapshot: Snapshot) {
        roots = snapshot.roots?.copyRoots()
    }

    private fun Map<SymbolicName, Pair<VariableEmbedding, Node>>.copyRoots() =
        mapValuesTo(mutableMapOf()) { (_, entry) -> entry.first to entry.second.deepCopy() }

    /** The state after an unconditional jump. */
    fun kill() {
        roots = null
    }

    /**
     * Join of two normalized snapshots: a root stays held only when both hold it folded. A dead snapshot is the
     * identity.
     */
    fun join(a: Snapshot, b: Snapshot): Snapshot {
        val left = a.roots ?: return b
        val right = b.roots ?: return a
        return Snapshot(left.filter { (name, entry) ->
            entry.second.status == Status.Folded && right[name]?.second?.status == Status.Folded
        })
    }

    /** Normalize, record the state for the jump target [label], and become dead. */
    fun jumpTo(ctx: LinearizationContext, label: SymbolicName) {
        normalize(ctx)
        val current = snapshot()
        pendingJumps[label] = pendingJumps[label]?.let { join(it, current) } ?: current
        kill()
    }

    /** Join the states of jumps to [label] into the state falling through to it. */
    fun arriveAt(ctx: LinearizationContext, label: SymbolicName) {
        val jumps = pendingJumps.remove(label) ?: return
        normalize(ctx)
        restore(join(snapshot(), jumps))
    }

    /**
     * The node for [path], opened far enough that [field] is accessible. Ancestors are opened through the fields
     * on the path. Returns `null` when [path] holds nothing.
     */
    private fun reach(ctx: LinearizationContext, path: OwnedPath, field: FieldEmbedding): Node? {
        var node = roots?.get(path.root.name)?.second ?: return null
        var exp: Exp = path.root.toViperExp(ctx)
        for (step in path.fields) {
            if (!openThrough(ctx, node, exp, step)) return null
            node = node.children.getOrPut(step.name) { step to Node(step.type, Status.Folded) }.second
            exp = Exp.FieldAccess(exp, step.toViper(), ctx.source.asPosition)
        }
        return node.takeIf { openThrough(ctx, it, exp, field) }
    }

    /** Unfold the chain of [node] through the class declaring [field]. Returns false when [node] holds nothing. */
    private fun openThrough(ctx: LinearizationContext, node: Node, exp: Exp, field: FieldEmbedding): Boolean {
        val opened = when (val status = node.status) {
            Status.Absent -> return false
            Status.Folded -> emptyList()
            is Status.Open -> status.opened
        }
        if (ctx.trackedClass(node.type) == null) return true
        val chain = ctx.typeResolver.hierarchyPathTo(node.type.pretype, field).toList()
        if (chain.size <= opened.size) return true
        for (cls in chain.drop(opened.size)) {
            ctx.addStatement { Stmt.Unfold(hierarchyPredicateAccess(exp, cls, source), source.asPosition) }
        }
        node.status = Status.Open(chain)
        return true
    }

    private fun Node.hasHole(): Boolean =
        children.values.any { (_, child) -> child.status == Status.Absent || child.hasHole() }

    private fun closeNode(ctx: LinearizationContext, node: Node, exp: Exp) {
        for ((field, child) in node.children.values) {
            if (child.status == Status.Absent) {
                throw FoldStateException(ctx.source, "A unique path must be folded here, but one of its @Unique fields was moved out.")
            }
            closeNode(ctx, child, Exp.FieldAccess(exp, field.toViper(), ctx.source.asPosition))
        }
        node.children.clear()
        val status = node.status as? Status.Open ?: return
        for (cls in status.opened.reversed()) {
            ctx.addStatement { Stmt.Fold(hierarchyPredicateAccess(exp, cls, source), source.asPosition) }
        }
        node.status = Status.Folded
    }

    private fun notHeld(ctx: LinearizationContext) =
        FoldStateException(ctx.source, "A unique predicate needed here is not held.")
}
