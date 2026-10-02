/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.formver.core.asPosition
import org.jetbrains.kotlin.formver.core.domains.RuntimeTypeDomain
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

    /** Loop heads entered so far, with the roots each one holds folded. */
    private val loopHeads: MutableMap<SymbolicName, List<VariableEmbedding>> = mutableMapOf()

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
        var place = ctx.rootPlace(path.root)
        for (field in path.fields) {
            node = node.children[field.name]?.second ?: return
            place = ctx.fieldPlace(place, field)
        }
        closeNode(ctx, node, place)
    }

    /**
     * Replace the predicate of [path] with a fresh instance, which havocs every value under it. [path] must be
     * folded, and stays folded.
     */
    fun refresh(ctx: LinearizationContext, path: OwnedPath) {
        if (roots == null) return
        val cls = ctx.trackedClass(path.type) ?: return
        if (!holds(path)) throw notHeld(ctx)
        val place = path.fields.fold(ctx.rootPlace(path.root)) { parent, field -> ctx.fieldPlace(parent, field) }
        val pos = ctx.source.asPosition
        val access = place.guards.foldRight<Exp, Exp>(hierarchyPredicateAccess(place.exp, cls, ctx.source)) { guard, inner ->
            Exp.Implies(guard, inner, pos)
        }
        ctx.addStatement { Stmt.Exhale(access, pos) }
        ctx.addStatement { Stmt.Inhale(access, pos) }
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
            if (node.hasHole()) live.remove(name) else closeNode(ctx, node, ctx.rootPlace(variable))
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

    /**
     * Normalize and become dead. A jump to an entered loop head must hold the roots of that head folded; a jump to
     * any other [label] records its state for the label.
     */
    fun jumpTo(ctx: LinearizationContext, label: SymbolicName) {
        normalize(ctx)
        val head = loopHeads[label]
        if (head != null) {
            requireFolded(ctx, head)
        } else {
            val current = snapshot()
            pendingJumps[label] = pendingJumps[label]?.let { join(it, current) } ?: current
        }
        kill()
    }

    /**
     * Enter the head of the loop whose continue label is [label]. [unique] are the variables the uniqueness checker
     * finds `Unique` there. The state is normalized, every tracked one of them must be held folded, and every other
     * root is forgotten. Jumps to [label] must hold the same roots folded.
     *
     * Returns the tracked variables of [unique]: the loop invariant holds their predicates.
     */
    fun enterLoopHead(
        ctx: LinearizationContext,
        label: SymbolicName,
        unique: List<VariableEmbedding>,
    ): List<VariableEmbedding> {
        val tracked = unique.filter { ctx.trackedClass(it.type) != null }
        loopHeads[label] = tracked
        normalize(ctx)
        val live = roots ?: return tracked
        requireFolded(ctx, tracked)
        live.keys.retainAll(tracked.map { it.name }.toSet())
        return tracked
    }

    private fun requireFolded(ctx: LinearizationContext, variables: List<VariableEmbedding>) {
        val live = roots ?: return
        for (variable in variables) {
            if (live[variable.name]?.second?.status != Status.Folded) {
                throw FoldStateException(ctx.source, "The loop head needs a unique predicate that is not held.")
            }
        }
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
        var place = ctx.rootPlace(path.root)
        for (step in path.fields) {
            if (!openThrough(ctx, node, place, step)) return null
            node = node.children.getOrPut(step.name) { step to Node(step.type, Status.Folded) }.second
            place = ctx.fieldPlace(place, step)
        }
        return node.takeIf { openThrough(ctx, it, place, field) }
    }

    /** Unfold the chain of [node] through the class declaring [field]. Returns false when [node] holds nothing. */
    private fun openThrough(ctx: LinearizationContext, node: Node, place: Place, field: FieldEmbedding): Boolean {
        val opened = when (val status = node.status) {
            Status.Absent -> return false
            Status.Folded -> emptyList()
            is Status.Open -> status.opened
        }
        if (ctx.trackedClass(node.type) == null) return true
        val chain = ctx.typeResolver.hierarchyPathTo(node.type.pretype, field).toList()
        if (chain.size <= opened.size) return true
        for (cls in chain.drop(opened.size)) {
            ctx.addGuarded(place) { Stmt.Unfold(hierarchyPredicateAccess(place.exp, cls, source), source.asPosition) }
        }
        node.status = Status.Open(chain)
        return true
    }

    private fun Node.hasHole(): Boolean =
        children.values.any { (_, child) -> child.status == Status.Absent || child.hasHole() }

    private fun closeNode(ctx: LinearizationContext, node: Node, place: Place) {
        for ((field, child) in node.children.values) {
            if (child.status == Status.Absent) {
                throw FoldStateException(ctx.source, "A unique path must be folded here, but one of its @Unique fields was moved out.")
            }
            closeNode(ctx, child, ctx.fieldPlace(place, field))
        }
        node.children.clear()
        val status = node.status as? Status.Open ?: return
        for (cls in status.opened.reversed()) {
            ctx.addGuarded(place) { Stmt.Fold(hierarchyPredicateAccess(place.exp, cls, source), source.asPosition) }
        }
        node.status = Status.Folded
    }

    private fun notHeld(ctx: LinearizationContext) =
        FoldStateException(ctx.source, "A unique predicate needed here is not held.")
}

/**
 * A tracked path as Viper reads it. [guards] say that each nullable prefix of the path, the path itself included, is
 * not `null`, outermost first.
 */
private data class Place(val exp: Exp, val guards: List<Exp>)

private fun LinearizationContext.guardsOf(exp: Exp, type: TypeEmbedding): List<Exp> =
    if (type.isNullable) listOf(Exp.NeCmp(exp, RuntimeTypeDomain.nullValue(pos = source.asPosition), source.asPosition))
    else emptyList()

private fun LinearizationContext.rootPlace(root: VariableEmbedding): Place {
    val exp = root.toViperExp(this)
    return Place(exp, guardsOf(exp, root.type))
}

private fun LinearizationContext.fieldPlace(parent: Place, field: FieldEmbedding): Place {
    val exp = Exp.FieldAccess(parent.exp, field.toViper(), source.asPosition)
    return Place(exp, parent.guards + guardsOf(exp, field.type))
}

/** Add the statement [buildStmt] builds, nested in one `if` per guard of [place]. */
private fun LinearizationContext.addGuarded(place: Place, buildStmt: LinearizationContext.() -> Stmt) =
    addStatement {
        place.guards.foldRight(buildStmt()) { guard, inner ->
            Stmt.If(guard, Stmt.Seqn(listOf(inner)), Stmt.Seqn(), source.asPosition)
        }
    }
