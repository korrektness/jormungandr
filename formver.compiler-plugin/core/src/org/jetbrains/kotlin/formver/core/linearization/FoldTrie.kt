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
 * The shape a root's predicate has where the uniqueness checker finds the root `Unique`: [holes] are the paths below
 * it, as field lists, whose predicates are moved out. No hole extends another.
 *
 * Without holes the root is folded. Otherwise each ancestor of a hole is open just far enough to expose the field on
 * the way to it, a hole holds nothing, and every other path is folded.
 */
data class RootShape<R, F>(val root: R, val holes: List<List<F>> = emptyList())

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

    /**
     * The classes to unfold from [cls] so that the predicate of its supertype [target] is held folded, [cls] first:
     * empty when [target] is [cls], and `null` when [target] is not a supertype of [cls] whose predicate an unfolding
     * of its class chain exposes.
     */
    fun chainBelow(cls: C, target: C): List<C>?

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

    /**
     * Exhale and inhale what unfolding the predicate of [cls] for [path] exposes, apart from the predicates of its
     * supertypes: its fields and the folded predicates of its `@Unique` fields. This havocs every value under them.
     */
    fun refreshOwn(path: FoldPath<R, F>, cls: C)

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

    /** Labels whose shape is fixed in advance: every edge into one of them is brought to that shape. */
    private val labelShapes: MutableMap<Any, List<RootShape<R, F>>> = mutableMapOf()

    private fun classOf(path: FoldPath<R, F>): C? =
        if (path.fields.isEmpty()) hierarchy.rootClass(path.root) else hierarchy.fieldClass(path.fields.last())

    private fun FoldPath<R, F>.parent(): FoldPath<R, F> = FoldPath(root, fields.dropLast(1))

    /** Mark [path] as holding its folded predicate. */
    fun acquire(sink: FoldSink<R, F, C>, path: FoldPath<R, F>) {
        val cls = classOf(path) ?: return
        place(sink, path, FoldNode(cls, FoldStatus.Folded))
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

    /**
     * Make the folded predicate of [cls], a supertype of the static class of [path], held for [path]: unfold the
     * classes below [cls] that are still folded, or fold back up to [cls], closing the children of the classes folded.
     * The predicates of the classes below [cls] stay unfolded.
     */
    fun expose(sink: FoldSink<R, F, C>, path: FoldPath<R, F>, cls: C) {
        if (roots == null) return
        val own = classOf(path) ?: return
        if (!holds(path)) notHeld(sink)
        val node = locate(sink, path) ?: notHeld(sink)
        exposeNode(sink, node, path, own, cls)
    }

    /**
     * Havoc what [path] holds besides the folded predicate [expose] made held: close its children, which must have no
     * moved-out field, and refresh the fields of each unfolded class of its chain together with their nested
     * predicates. The callee of a borrowing call that received a supertype predicate may have written them through a
     * view the caller does not see.
     */
    fun refreshRetained(sink: FoldSink<R, F, C>, path: FoldPath<R, F>) {
        if (roots == null || classOf(path) == null) return
        val node = locate(sink, path) ?: notHeld(sink)
        val opened = (node.status as? FoldStatus.Open)?.opened ?: return
        closeChildren(sink, node, path)
        for (cls in opened) sink.refreshOwn(path, cls)
    }

    /** Fold everything under [path] that can be folded, keeping its holes. */
    fun tidy(sink: FoldSink<R, F, C>, path: FoldPath<R, F>) {
        if (roots == null || classOf(path) == null) return
        if (!holds(path)) notHeld(sink)
        val node = locate(sink, path) ?: notHeld(sink)
        settle(sink, node, path, node.holes())
    }

    /**
     * Move what [src] holds to [dst]: [src] holds nothing afterwards. When both have the same static class, the subtree
     * of [src] moves as it is, holes included, and nothing is emitted. Otherwise the static class of [dst] must be a
     * supertype of that of [src]: its predicate is exposed for [src] and moves, and the rest of [src] is leaked.
     */
    fun transfer(sink: FoldSink<R, F, C>, src: FoldPath<R, F>, dst: FoldPath<R, F>) {
        if (roots == null) return
        val cls = classOf(dst) ?: return release(sink, src)
        if (!holds(src)) notHeld(sink)
        val node = locate(sink, src) ?: notHeld(sink)
        val own = classOf(src) ?: notHeld(sink)
        val moved = if (own == cls) {
            node.deepCopy()
        } else {
            exposeNode(sink, node, src, own, cls)
            FoldNode<F, C>(cls, FoldStatus.Folded)
        }
        release(sink, src)
        place(sink, dst, moved)
    }

    /**
     * Fold everything that can be folded: each root is brought to the [RootShape] of its own holes, so that the state
     * can be joined with another.
     */
    fun normalize(sink: FoldSink<R, F, C>) {
        val live = roots ?: return
        normalizeTo(sink, live.values.map { (root, node) -> RootShape(root, node.holes()) })
    }

    /**
     * Bring the state to [shapes]: each of their roots to its shape, forgetting the predicates under its holes, and
     * forget every other root. Every root of [shapes] must be held, and every path that holds nothing must lie under a
     * hole.
     */
    fun normalizeTo(sink: FoldSink<R, F, C>, shapes: List<RootShape<R, F>>) {
        val live = roots ?: return
        val wanted = shapes.filter { hierarchy.rootClass(it.root) != null }.associateBy { hierarchy.rootKey(it.root) }
        live.keys.retainAll(wanted.keys)
        for ((key, shape) in wanted) {
            val node = live[key]?.second ?: notHeld(sink)
            settle(sink, node, FoldPath(shape.root), shape.holes)
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
     * Join of two normalized snapshots of edges into a label: a root stays held only when both hold it with the same
     * holes. A dead snapshot is the identity.
     */
    fun join(a: Snapshot<R, F, C>, b: Snapshot<R, F, C>): Snapshot<R, F, C> {
        val left = a.roots ?: return b
        val right = b.roots ?: return a
        return Snapshot(left.filter { (key, entry) ->
            val other = right[key]?.second
            other != null && entry.second.holes().keys() == other.holes().keys()
        })
    }

    /**
     * The shapes that the arms of a branch, ending in the normalized snapshots [a] and [b], are brought to where they
     * merge: a root stays held when both arms hold it, with every hole either arm has. `null` when both arms are dead.
     */
    fun mergeShapes(a: Snapshot<R, F, C>, b: Snapshot<R, F, C>): List<RootShape<R, F>>? {
        val left = a.roots
        val right = b.roots
        if (left == null || right == null) return (left ?: right)?.values?.map { (root, node) -> RootShape(root, node.holes()) }
        return left.mapNotNull { (key, entry) ->
            val other = right[key]?.second ?: return@mapNotNull null
            RootShape(entry.first, outermost(entry.second.holes() + other.holes()))
        }
    }

    /**
     * Become dead. A jump to a label whose shape is fixed brings the state to that shape first; a jump to any other
     * [label] is normalized and records its state for the label.
     */
    fun jumpTo(sink: FoldSink<R, F, C>, label: Any) {
        val shapes = labelShapes[label]
        if (shapes != null) {
            normalizeTo(sink, shapes)
        } else {
            normalize(sink)
            val current = snapshot()
            pendingJumps[label] = pendingJumps[label]?.let { join(it, current) } ?: current
        }
        kill()
    }

    /**
     * Enter a loop: [head] are the shapes of the roots the uniqueness checker finds `Unique` at the head, whose
     * continue label is [headLabel], and [exit] those after the loop, whose break label is [exitLabel]. The state is
     * brought to [head], and every edge into either label is brought to its shapes.
     *
     * Returns the tracked shapes of [head]: the loop invariant holds their permissions.
     */
    fun enterLoop(
        sink: FoldSink<R, F, C>,
        headLabel: Any,
        head: List<RootShape<R, F>>,
        exitLabel: Any,
        exit: List<RootShape<R, F>>,
    ): List<RootShape<R, F>> {
        val tracked = head.filter { hierarchy.rootClass(it.root) != null }
        labelShapes[headLabel] = tracked
        labelShapes[exitLabel] = exit
        val live = roots
        if (live != null && tracked.any { hierarchy.rootKey(it.root) !in live }) {
            sink.fail("The loop head needs a unique predicate that is not held.")
        }
        normalizeTo(sink, tracked)
        return tracked
    }

    /**
     * Arrive at [label] falling through. At a label whose shape is fixed the state is brought to that shape; otherwise
     * the states of jumps to [label] are joined into the current one.
     */
    fun arriveAt(sink: FoldSink<R, F, C>, label: Any) {
        labelShapes[label]?.let { return normalizeTo(sink, it) }
        val jumps = pendingJumps.remove(label) ?: return
        normalize(sink)
        restore(join(snapshot(), jumps))
    }

    /**
     * How [shape] holds its root: [folded] describes a folded path with its static class, and [open] an open path
     * with the classes unfolded on it and its children, a hole being `null`. A field of an unfolded class with no
     * child is folded. `null` when the root is not tracked.
     */
    fun <T> describe(
        shape: RootShape<R, F>,
        folded: (FoldPath<R, F>, C) -> T,
        open: (FoldPath<R, F>, C, List<C>, List<Pair<F, T?>>) -> T,
    ): T? {
        val cls = hierarchy.rootClass(shape.root) ?: return null
        val node = FoldNode<F, C>(cls, FoldStatus.Folded)
        val silent = object : FoldSink<R, F, C> {
            override fun unfold(path: FoldPath<R, F>, cls: C) {}
            override fun fold(path: FoldPath<R, F>, cls: C) {}
            override fun refresh(path: FoldPath<R, F>, cls: C) {}
            override fun refreshOwn(path: FoldPath<R, F>, cls: C) {}
            override fun fail(message: String): Nothing = error(message)
        }
        settle(silent, node, FoldPath(shape.root), shape.holes)
        fun describeNode(node: FoldNode<F, C>, path: FoldPath<R, F>): T? {
            val nodeCls = node.cls ?: return null
            return when (val status = node.status) {
                FoldStatus.Absent -> null
                FoldStatus.Folded -> folded(path, nodeCls)
                is FoldStatus.Open -> open(path, nodeCls, status.opened, node.children.values.map { (field, child) ->
                    field to describeNode(child, path + field)
                })
            }
        }
        return describeNode(node, FoldPath(shape.root))
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

    /** The paths below this node, as field lists, that hold nothing; none extends another. */
    private fun FoldNode<F, C>.holes(): List<List<F>> = children.values.flatMap { (field, child) ->
        if (child.status == FoldStatus.Absent) listOf(listOf(field)) else child.holes().map { listOf(field) + it }
    }

    private fun List<List<F>>.keys(): Set<List<Any>> = mapTo(mutableSetOf()) { path -> path.map(hierarchy::fieldKey) }

    /** [holes] without duplicates and without the holes that extend another. */
    private fun outermost(holes: List<List<F>>): List<List<F>> {
        val distinct = holes.distinctBy { path -> path.map(hierarchy::fieldKey) }
        val keys = distinct.keys()
        return distinct.filter { path -> (1 until path.size).none { path.subList(0, it).map(hierarchy::fieldKey) in keys } }
    }

    /**
     * Bring [node], at [path], to the shape with [holes]: open each ancestor of a hole through the field on the way to
     * it, and no further; mark each hole as holding nothing, forgetting what it held; fold everything else.
     */
    private fun settle(sink: FoldSink<R, F, C>, node: FoldNode<F, C>, path: FoldPath<R, F>, holes: List<List<F>>) {
        if (holes.isEmpty()) return closeNode(sink, node, path)
        val cls = node.cls ?: notHeld(sink)
        val byField = holes.groupBy { hierarchy.fieldKey(it.first()) }
        for ((key, group) in byField) {
            val field = group.first().first()
            if (!openThrough(sink, node, path, field)) notHeld(sink)
            val tails = group.map { it.drop(1) }
            if (tails.any { it.isEmpty() }) {
                node.children[key] = field to FoldNode(hierarchy.fieldClass(field), FoldStatus.Absent)
            } else {
                val child = node.children.getOrPut(key) {
                    field to FoldNode(hierarchy.fieldClass(field), FoldStatus.Folded)
                }.second
                if (child.status == FoldStatus.Absent) notHeld(sink)
                settle(sink, child, path + field, tails)
            }
        }
        for (key in node.children.keys - byField.keys) {
            val (field, child) = node.children.remove(key)!!
            if (child.status == FoldStatus.Absent) movedOut(sink)
            closeNode(sink, child, path + field)
        }
        val depth = byField.values.maxOf { hierarchy.chainTo(cls, it.first().first()).size }
        val opened = (node.status as FoldStatus.Open).opened
        for (unneeded in opened.drop(depth).reversed()) sink.fold(path, unneeded)
        node.status = FoldStatus.Open(opened.take(depth))
    }

    /** Put [node] at [path], whose parent must be held. */
    private fun place(sink: FoldSink<R, F, C>, path: FoldPath<R, F>, node: FoldNode<F, C>) {
        val live = roots ?: return
        if (path.fields.isEmpty()) {
            live[hierarchy.rootKey(path.root)] = path.root to node
        } else {
            val field = path.fields.last()
            val parent = reach(sink, path.parent(), field) ?: notHeld(sink)
            parent.children[hierarchy.fieldKey(field)] = field to node
        }
    }

    /** Bring [node], at [path] with static class [own], to holding the folded predicate of its supertype [cls]. */
    private fun exposeNode(sink: FoldSink<R, F, C>, node: FoldNode<F, C>, path: FoldPath<R, F>, own: C, cls: C) {
        val below = hierarchy.chainBelow(own, cls)
            ?: sink.fail("The predicate of a subtype of the path's static class is needed here, which the path does not hold.")
        val opened = (node.status as? FoldStatus.Open)?.opened.orEmpty()
        if (opened.size <= below.size) {
            if (!openChain(sink, node, path) { below }) notHeld(sink)
            return
        }
        val folded = node.children.filterValues { (field, _) -> hierarchy.chainTo(own, field).size > below.size }
        for ((key, entry) in folded) {
            node.children.remove(key)
            val (field, child) = entry
            if (child.status == FoldStatus.Absent) movedOut(sink)
            closeNode(sink, child, path + field)
        }
        for (unneeded in opened.drop(below.size).reversed()) sink.fold(path, unneeded)
        node.status = if (below.isEmpty()) FoldStatus.Folded else FoldStatus.Open(below)
    }

    private fun closeChildren(sink: FoldSink<R, F, C>, node: FoldNode<F, C>, path: FoldPath<R, F>) {
        for ((field, child) in node.children.values) {
            if (child.status == FoldStatus.Absent) movedOut(sink)
            closeNode(sink, child, path + field)
        }
        node.children.clear()
    }

    private fun closeNode(sink: FoldSink<R, F, C>, node: FoldNode<F, C>, path: FoldPath<R, F>) {
        closeChildren(sink, node, path)
        val status = node.status as? FoldStatus.Open ?: return
        for (cls in status.opened.reversed()) sink.fold(path, cls)
        node.status = FoldStatus.Folded
    }

    private fun notHeld(sink: FoldSink<R, F, C>): Nothing = sink.fail("A unique predicate needed here is not held.")

    private fun movedOut(sink: FoldSink<R, F, C>): Nothing =
        sink.fail("A unique path must be folded here, but one of its @Unique fields was moved out.")
}
