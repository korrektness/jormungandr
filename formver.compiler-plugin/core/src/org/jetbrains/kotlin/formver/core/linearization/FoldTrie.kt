/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

/**
 * A root followed by `@Unique` fields.
 */
data class FoldPath<R, F>(val root: R, val fields: List<F> = emptyList()) {
    operator fun plus(field: F) = FoldPath(root, fields + field)
}

/**
 * What a [FoldTrie] needs to know about the program: the class whose predicate a root or field holds, and the class
 * chains that are opened to reach a field.
 */
interface FoldHierarchy<R, F, C> {
    /** The static class of [root] when its predicate is tracked, `null` otherwise. */
    fun rootClass(root: R): C?

    /** The static class of [field] when its predicate is tracked, `null` otherwise. */
    fun fieldClass(field: F): C?

    /** The classes from [cls] up to the one declaring [field], [cls] first. */
    fun chainTo(cls: C, field: F): List<C>

    /** The identity of [root] in the trie. */
    fun rootKey(root: R): Any

    /** The identity of [field] among the fields of one path. */
    fun fieldKey(field: F): Any
}

/**
 * Receives the statements a [FoldTrie] emits, and the failures it reports.
 */
interface FoldSink<R, F, C> {
    /** Unfold the predicate of [cls] for [path]. */
    fun unfold(path: FoldPath<R, F>, cls: C)

    /** Fold the predicate of [cls] for [path]. */
    fun fold(path: FoldPath<R, F>, cls: C)

    /** Exhale and inhale the folded predicate of [cls] for [path], which havocs every value under it. */
    fun refresh(path: FoldPath<R, F>, cls: C)

    /** The code needs a predicate the trie does not hold. */
    fun fail(message: String): Nothing
}

internal sealed interface FoldStatus<out C> {
    data object Folded : FoldStatus<Nothing>
    data object Absent : FoldStatus<Nothing>

    /**
     * The predicates of [opened] are unfolded, the static class first. The predicate of the next class in the chain is
     * held folded.
     */
    data class Open<C>(val opened: List<C>) : FoldStatus<C>
}

/** [cls] is the static class of the node's path, or `null` when that path is not tracked. */
internal class FoldNode<F, C>(val cls: C?, var status: FoldStatus<C>) {
    /** Entries for `@Unique` fields of opened classes. A field without an entry is held folded. */
    val children: MutableMap<Any, Pair<F, FoldNode<F, C>>> = mutableMapOf()

    fun deepCopy(): FoldNode<F, C> = FoldNode<F, C>(cls, status).also { copy ->
        children.forEach { (key, entry) -> copy.children[key] = entry.first to entry.second.deepCopy() }
    }
}

/**
 * The unique predicates held for owned paths, and how far each one is unfolded.
 *
 * Roots and fields are identified by the keys [hierarchy] gives them. A root missing from the trie holds nothing. Only
 * paths whose static class [hierarchy] tracks are tracked. After an unconditional jump the state is dead: it holds
 * nothing and joins as the identity.
 *
 * Operations that unfold or fold predicates report each statement to a [FoldSink], in program order.
 */
class FoldTrie<R, F, C>(private val hierarchy: FoldHierarchy<R, F, C>) {
    /**
     * A copy of the state at a program point, for joining at labels and branch merges. It is opaque outside
     * [FoldTrie], so it has no value semantics.
     */
    @Suppress("UseDataClass")
    class Snapshot<R, F, C> internal constructor(internal val roots: Map<Any, Pair<R, FoldNode<F, C>>>?)

    private var roots: MutableMap<Any, Pair<R, FoldNode<F, C>>>? = mutableMapOf()

    /** Labels that jumps have reached, with the joined state of those jumps. */
    private val pendingJumps: MutableMap<Any, Snapshot<R, F, C>> = mutableMapOf()

    /** Loop heads entered so far, with the roots each one holds folded. */
    private val loopHeads: MutableMap<Any, List<R>> = mutableMapOf()

    private fun classOf(path: FoldPath<R, F>): C? =
        if (path.fields.isEmpty()) hierarchy.rootClass(path.root) else hierarchy.fieldClass(path.fields.last())

    private fun FoldPath<R, F>.parent(): FoldPath<R, F> = FoldPath(root, fields.dropLast(1))

    /** Mark [path] as holding its folded predicate. */
    fun acquire(sink: FoldSink<R, F, C>, path: FoldPath<R, F>) {
        val cls = classOf(path) ?: return
        val node = FoldNode<F, C>(cls, FoldStatus.Folded)
        val live = roots ?: return
        if (path.fields.isEmpty()) {
            live[hierarchy.rootKey(path.root)] = path.root to node
        } else {
            val field = path.fields.last()
            val parent = reach(sink, path.parent(), field) ?: notHeld(sink)
            parent.children[hierarchy.fieldKey(field)] = field to node
        }
    }

    /** Whether the predicate of [path] is held, possibly nested in the folded predicate of an ancestor. */
    fun holds(path: FoldPath<R, F>): Boolean {
        var node = roots?.get(hierarchy.rootKey(path.root))?.second ?: return false
        for (field in path.fields) {
            if (node.status == FoldStatus.Absent) return false
            node = node.children[hierarchy.fieldKey(field)]?.second ?: return true
        }
        return node.status != FoldStatus.Absent
    }

    /** Mark [path] as holding nothing, emitting nothing. The predicate, if any, is leaked. */
    fun release(sink: FoldSink<R, F, C>, path: FoldPath<R, F>) {
        if (classOf(path) == null || !holds(path)) return
        if (path.fields.isEmpty()) {
            roots?.remove(hierarchy.rootKey(path.root))
            return
        }
        val field = path.fields.last()
        val parent = reach(sink, path.parent(), field) ?: return
        parent.children[hierarchy.fieldKey(field)] = field to FoldNode(hierarchy.fieldClass(field), FoldStatus.Absent)
    }

    /** Unfold what is needed to access [field] on [path]. */
    fun open(sink: FoldSink<R, F, C>, path: FoldPath<R, F>, field: F) {
        if (roots == null) return
        reach(sink, path, field) ?: notHeld(sink)
    }

    /** Unfold the predicate of the static class of [path], and what is needed to reach [path]. */
    fun openOwn(sink: FoldSink<R, F, C>, path: FoldPath<R, F>) {
        if (roots == null) return
        val node = locate(sink, path) ?: notHeld(sink)
        if (!openChain(sink, node, path) { listOf(it) }) notHeld(sink)
    }

    /** Fold [path] completely. Its subtree must have no moved-out field. */
    fun close(sink: FoldSink<R, F, C>, path: FoldPath<R, F>) {
        val live = roots ?: return
        if (classOf(path) == null) return
        if (!holds(path)) notHeld(sink)
        var node = live[hierarchy.rootKey(path.root)]?.second ?: return
        for (field in path.fields) {
            node = node.children[hierarchy.fieldKey(field)]?.second ?: return
        }
        closeNode(sink, node, path)
    }

    /**
     * Replace the predicate of [path] with a fresh instance, which havocs every value under it. [path] must be
     * folded, and stays folded. Ancestors are unfolded so that the predicate is held on its own.
     */
    fun refresh(sink: FoldSink<R, F, C>, path: FoldPath<R, F>) {
        if (roots == null) return
        val cls = classOf(path) ?: return
        if (!holds(path)) notHeld(sink)
        if (path.fields.isNotEmpty()) reach(sink, path.parent(), path.fields.last()) ?: notHeld(sink)
        sink.refresh(path, cls)
    }

    /** Move the predicate of [src] to [dst]: [src] holds nothing afterwards. [src] must be folded. */
    fun transfer(sink: FoldSink<R, F, C>, src: FoldPath<R, F>, dst: FoldPath<R, F>) {
        if (roots == null) return
        release(sink, src)
        acquire(sink, dst)
    }

    /**
     * Fold every root that can be folded and forget the others, so that the state can be joined with another.
     */
    fun normalize(sink: FoldSink<R, F, C>) {
        val live = roots ?: return
        for ((key, entry) in live.entries.toList()) {
            val (root, node) = entry
            if (node.hasHole()) live.remove(key) else closeNode(sink, node, FoldPath(root))
        }
    }

    fun snapshot(): Snapshot<R, F, C> = Snapshot(roots?.copyRoots())

    fun restore(snapshot: Snapshot<R, F, C>) {
        roots = snapshot.roots?.copyRoots()
    }

    private fun Map<Any, Pair<R, FoldNode<F, C>>>.copyRoots() =
        mapValuesTo(mutableMapOf()) { (_, entry) -> entry.first to entry.second.deepCopy() }

    /** The state after an unconditional jump. */
    fun kill() {
        roots = null
    }

    /**
     * Join of two normalized snapshots: a root stays held only when both hold it folded. A dead snapshot is the
     * identity.
     */
    fun join(a: Snapshot<R, F, C>, b: Snapshot<R, F, C>): Snapshot<R, F, C> {
        val left = a.roots ?: return b
        val right = b.roots ?: return a
        return Snapshot(left.filter { (key, entry) ->
            entry.second.status == FoldStatus.Folded && right[key]?.second?.status == FoldStatus.Folded
        })
    }

    /**
     * Normalize and become dead. A jump to an entered loop head must hold the roots of that head folded; a jump to
     * any other [label] records its state for the label.
     */
    fun jumpTo(sink: FoldSink<R, F, C>, label: Any) {
        normalize(sink)
        val head = loopHeads[label]
        if (head != null) {
            requireFolded(sink, head)
        } else {
            val current = snapshot()
            pendingJumps[label] = pendingJumps[label]?.let { join(it, current) } ?: current
        }
        kill()
    }

    /**
     * Enter the head of the loop whose continue label is [label]. [unique] are the roots the uniqueness checker finds
     * `Unique` there. The state is normalized, every tracked one of them must be held folded, and every other root is
     * forgotten. Jumps to [label] must hold the same roots folded.
     *
     * Returns the tracked roots of [unique]: the loop invariant holds their predicates.
     */
    fun enterLoopHead(sink: FoldSink<R, F, C>, label: Any, unique: List<R>): List<R> {
        val tracked = unique.filter { hierarchy.rootClass(it) != null }
        loopHeads[label] = tracked
        normalize(sink)
        val live = roots ?: return tracked
        requireFolded(sink, tracked)
        live.keys.retainAll(tracked.map(hierarchy::rootKey).toSet())
        return tracked
    }

    private fun requireFolded(sink: FoldSink<R, F, C>, required: List<R>) {
        val live = roots ?: return
        for (root in required) {
            if (live[hierarchy.rootKey(root)]?.second?.status != FoldStatus.Folded) {
                sink.fail("The loop head needs a unique predicate that is not held.")
            }
        }
    }

    /** Join the states of jumps to [label] into the state falling through to it. */
    fun arriveAt(sink: FoldSink<R, F, C>, label: Any) {
        val jumps = pendingJumps.remove(label) ?: return
        normalize(sink)
        restore(join(snapshot(), jumps))
    }

    /**
     * The node for [path], with the ancestors opened through the fields on the path. Returns `null` when [path] holds
     * nothing.
     */
    private fun locate(sink: FoldSink<R, F, C>, path: FoldPath<R, F>): FoldNode<F, C>? {
        var node = roots?.get(hierarchy.rootKey(path.root))?.second ?: return null
        var prefix = FoldPath<R, F>(path.root)
        for (step in path.fields) {
            if (!openThrough(sink, node, prefix, step)) return null
            node = node.children.getOrPut(hierarchy.fieldKey(step)) {
                step to FoldNode(hierarchy.fieldClass(step), FoldStatus.Folded)
            }.second
            prefix += step
        }
        return node
    }

    /** The node for [path], opened far enough that [field] is accessible, or `null` when [path] holds nothing. */
    private fun reach(sink: FoldSink<R, F, C>, path: FoldPath<R, F>, field: F): FoldNode<F, C>? =
        locate(sink, path)?.takeIf { openThrough(sink, it, path, field) }

    /** Unfold the chain of [node] through the class declaring [field]. Returns false when [node] holds nothing. */
    private fun openThrough(sink: FoldSink<R, F, C>, node: FoldNode<F, C>, path: FoldPath<R, F>, field: F): Boolean =
        openChain(sink, node, path) { hierarchy.chainTo(it, field) }

    /**
     * Unfold the classes of [chain], applied to the static class of [node], that are not unfolded yet. Returns false
     * when [node] holds nothing.
     */
    private fun openChain(
        sink: FoldSink<R, F, C>,
        node: FoldNode<F, C>,
        path: FoldPath<R, F>,
        chain: (C) -> List<C>,
    ): Boolean {
        val opened = when (val status = node.status) {
            FoldStatus.Absent -> return false
            FoldStatus.Folded -> emptyList()
            is FoldStatus.Open -> status.opened
        }
        val cls = node.cls ?: return true
        val classes = chain(cls)
        if (classes.size <= opened.size) return true
        for (next in classes.drop(opened.size)) sink.unfold(path, next)
        node.status = FoldStatus.Open(classes)
        return true
    }

    private fun FoldNode<F, C>.hasHole(): Boolean =
        children.values.any { (_, child) -> child.status == FoldStatus.Absent || child.hasHole() }

    private fun closeNode(sink: FoldSink<R, F, C>, node: FoldNode<F, C>, path: FoldPath<R, F>) {
        for ((field, child) in node.children.values) {
            if (child.status == FoldStatus.Absent) {
                sink.fail("A unique path must be folded here, but one of its @Unique fields was moved out.")
            }
            closeNode(sink, child, path + field)
        }
        node.children.clear()
        val status = node.status as? FoldStatus.Open ?: return
        for (cls in status.opened.reversed()) sink.fold(path, cls)
        node.status = FoldStatus.Folded
    }

    private fun notHeld(sink: FoldSink<R, F, C>): Nothing = sink.fail("A unique predicate needed here is not held.")
}
