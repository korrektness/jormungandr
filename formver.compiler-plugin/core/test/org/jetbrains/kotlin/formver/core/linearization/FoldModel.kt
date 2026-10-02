/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import kotlin.test.assertEquals
import kotlin.test.assertTrue

data class TestRoot(val name: String, val cls: Int)

/** A `@Unique` field declared in class [owner] whose static class is [cls]. */
data class TestField(val name: String, val owner: Int, val cls: Int)

typealias TestPath = FoldPath<TestRoot, TestField>

/**
 * A generated program: classes are numbered, and the superclass of a class has a larger number, so the hierarchy is
 * acyclic. Every field is `@Unique` and every class is tracked.
 */
class TestProgram(
    private val superclasses: List<Int?>,
    val fields: List<TestField>,
    val roots: List<TestRoot>,
) : FoldHierarchy<TestRoot, TestField, Int> {
    fun superclassOf(cls: Int): Int? = superclasses[cls]

    fun chainOf(cls: Int): List<Int> = generateSequence(cls) { superclasses[it] }.toList()

    fun fieldsOf(cls: Int): List<TestField> = fields.filter { it.owner == cls }

    /** The fields an instance of [cls] has: those of every class on its chain. */
    fun visibleFields(cls: Int): List<TestField> = chainOf(cls).flatMap(::fieldsOf)

    fun classOf(path: TestPath): Int = path.fields.lastOrNull()?.cls ?: path.root.cls

    /** Every path of at most [MAX_DEPTH] fields. */
    val paths: List<TestPath> by lazy {
        fun extend(path: TestPath): List<TestPath> =
            if (path.fields.size == MAX_DEPTH) listOf(path)
            else listOf(path) + visibleFields(classOf(path)).flatMap { extend(path + it) }
        roots.flatMap { extend(FoldPath(it)) }
    }

    /**
     * The permissions a path of the shape with [holes] holds: without holes its folded predicate; otherwise the
     * predicates of its chain unfolded just far enough to expose the first field of every hole, with each exposed field
     * holding the permissions of the shape of the holes below it, nothing for a hole, and its folded predicate
     * otherwise.
     */
    fun shapeTokens(path: TestPath, holes: List<List<TestField>>): List<Token> {
        val cls = classOf(path)
        if (holes.isEmpty()) return listOf(Pred(path, cls))
        val chain = chainOf(cls)
        val depth = holes.maxOf { chain.indexOf(it.first().owner) + 1 }
        val opened = chain.take(depth).flatMap { declaring ->
            listOf(FieldPerms(path, declaring)) + fieldsOf(declaring).flatMap { field ->
                val below = holes.filter { it.first() == field }.map { it.drop(1) }
                when {
                    below.isEmpty() -> listOf(Pred(path + field, field.cls))
                    below.any { it.isEmpty() } -> emptyList()
                    else -> shapeTokens(path + field, below)
                }
            }
        }
        return opened + listOfNotNull(chain.getOrNull(depth)?.let { Pred(path, it) })
    }

    /** [holes] without duplicates and without the holes that extend another. */
    fun outermost(holes: List<List<TestField>>): Set<List<TestField>> =
        holes.filterTo(mutableSetOf()) { hole -> (1 until hole.size).none { hole.subList(0, it) in holes } }

    override fun rootClass(root: TestRoot): Int = root.cls
    override fun fieldClass(field: TestField): Int = field.cls

    override fun chainTo(cls: Int, field: TestField): List<Int> {
        val chain = chainOf(cls)
        val end = chain.indexOf(field.owner)
        check(end >= 0) { "class $cls has no field ${field.name}" }
        return chain.subList(0, end + 1)
    }

    override fun rootKey(root: TestRoot): Any = root.name
    override fun fieldKey(field: TestField): Any = field.name

    override fun toString() = "TestProgram(superclasses=$superclasses, fields=$fields, roots=$roots)"

    companion object {
        const val MAX_DEPTH = 3

        fun of(superclasses: List<Int?>, fieldClasses: List<List<Int>>, rootClasses: List<Int>) = TestProgram(
            superclasses,
            fieldClasses.flatMapIndexed { owner, classes ->
                classes.mapIndexed { i, cls -> TestField("f${owner}_$i", owner, cls) }
            },
            rootClasses.mapIndexed { i, cls -> TestRoot("r$i", cls) },
        )
    }
}

fun TestPath.parent(): TestPath = FoldPath(root, fields.dropLast(1))

fun TestPath.startsWith(prefix: TestPath): Boolean =
    root == prefix.root && fields.size >= prefix.fields.size && fields.subList(0, prefix.fields.size) == prefix.fields

/** A permission Viper holds. */
sealed interface Token {
    val path: TestPath
}

/** The predicate of class [cls] for [path], folded. */
data class Pred(override val path: TestPath, val cls: Int) : Token

/** The field permissions of the fields [cls] declares, for [path]: what unfolding the predicate of [cls] exposes. */
data class FieldPerms(override val path: TestPath, val cls: Int) : Token

/**
 * The permissions held in Viper, as a multiset. `unfold` and `fold` follow Viper: each consumes exactly the
 * permissions it needs and fails when one is missing.
 */
class HeapModel(private val program: TestProgram, private val tokens: MutableMap<Token, Int> = mutableMapOf()) {
    fun copy() = HeapModel(program, tokens.toMutableMap())

    private fun add(token: Token) {
        tokens[token] = (tokens[token] ?: 0) + 1
    }

    private fun take(token: Token) {
        val count = tokens[token] ?: throw AssertionError("$token is needed but not held")
        if (count == 1) tokens.remove(token) else tokens[token] = count - 1
    }

    /** The body of the predicate of [cls] for [path]. */
    private fun body(path: TestPath, cls: Int): List<Token> =
        listOf(FieldPerms(path, cls)) +
                program.fieldsOf(cls).map { Pred(path + it, it.cls) } +
                listOfNotNull(program.superclassOf(cls)?.let { Pred(path, it) })

    fun unfold(path: TestPath, cls: Int) {
        take(Pred(path, cls))
        body(path, cls).forEach(::add)
    }

    fun fold(path: TestPath, cls: Int) {
        body(path, cls).forEach(::take)
        add(Pred(path, cls))
    }

    /** Exhale the predicate of [cls] for [path] and inhale a fresh one. */
    fun refresh(path: TestPath, cls: Int) {
        take(Pred(path, cls))
        add(Pred(path, cls))
    }

    /** A fresh predicate for [path] arrives. */
    fun inhale(path: TestPath) = add(Pred(path, program.classOf(path)))

    /** Whether the predicate of [cls] for [path] is held folded, alone or nested in another folded predicate. */
    fun folded(path: TestPath, cls: Int): Boolean {
        if (Pred(path, cls) in tokens) return true
        val chain = program.chainOf(program.classOf(path))
        val position = chain.indexOf(cls)
        return when {
            position > 0 -> folded(path, chain[position - 1])
            path.fields.isEmpty() -> false
            else -> folded(path.parent(), path.fields.last().owner)
        }
    }

    /** Whether [path] holds its predicate, folded or opened. */
    fun live(path: TestPath): Boolean {
        val cls = program.classOf(path)
        return folded(path, cls) || FieldPerms(path, cls) in tokens
    }

    /** Whether some field exposed below [path] lost its predicate. */
    fun hasHole(path: TestPath): Boolean = holes(path).isNotEmpty()

    /** The paths below [path], as field lists, that lost their predicates under fields exposed by unfolding. */
    fun holes(path: TestPath): List<List<TestField>> = tokens.keys.filter { it is FieldPerms && it.path == path }
        .flatMap { perms ->
            program.fieldsOf((perms as FieldPerms).cls).flatMap { field ->
                if (live(path + field)) holes(path + field).map { listOf(field) + it } else listOf(listOf(field))
            }
        }

    /** Move every permission at or below [source] to the same place below [target], replacing what [target] held. */
    fun move(source: TestPath, target: TestPath) {
        val moved = tokensBelow(source)
        leak(source)
        leak(target)
        fun TestPath.rebased() = FoldPath(target.root, target.fields + fields.drop(source.fields.size))
        for ((token, count) in moved) {
            val rebased = when (token) {
                is Pred -> token.copy(path = token.path.rebased())
                is FieldPerms -> token.copy(path = token.path.rebased())
            }
            tokens[rebased] = count
        }
    }

    /** Bring [path] to the shape with [holes], forgetting what it held under them. */
    fun settle(path: TestPath, holes: List<List<TestField>>) {
        leak(path)
        program.shapeTokens(path, holes).forEach(::add)
    }

    /** Forget every permission at or below [path]. */
    fun leak(path: TestPath) {
        tokens.keys.removeIf { it.path.startsWith(path) }
    }

    fun roots(): Set<TestRoot> = tokens.keys.mapTo(mutableSetOf()) { it.path.root }

    fun tokensBelow(path: TestPath): Map<Token, Int> = tokens.filterKeys { it.path.startsWith(path) }

    /** The join of two normalized states at a label: a root stays held when both hold the same permissions for it. */
    fun joinAtLabel(other: HeapModel) = HeapModel(
        program,
        tokens.filterKeys { tokensBelow(FoldPath(it.path.root)) == other.tokensBelow(FoldPath(it.path.root)) }
            .toMutableMap(),
    )
}

class Refused(message: String) : Exception(message)

enum class Op { UNFOLD, FOLD, REFRESH }

data class Emitted(val op: Op, val path: TestPath, val cls: Int)

class RecordingSink : FoldSink<TestRoot, TestField, Int> {
    val emitted = mutableListOf<Emitted>()

    override fun unfold(path: TestPath, cls: Int) {
        emitted += Emitted(Op.UNFOLD, path, cls)
    }

    override fun fold(path: TestPath, cls: Int) {
        emitted += Emitted(Op.FOLD, path, cls)
    }

    override fun refresh(path: TestPath, cls: Int) {
        emitted += Emitted(Op.REFRESH, path, cls)
    }

    override fun fail(message: String): Nothing = throw Refused(message)
}

/** Selects a path among the candidates for an operation, or among every path when [wild] is set. */
data class Pick(val index: Int, val wild: Boolean)

enum class Exit { FALL_THROUGH, JUMP, DEAD }

sealed interface Step {
    data class Acquire(val pick: Pick) : Step
    data class Release(val pick: Pick) : Step
    data class Open(val pick: Pick, val field: Int) : Step
    data class Close(val pick: Pick) : Step

    /** Unfold the predicate of a held path's own static class, as before reading an `IntArray` element. */
    data class OpenOwn(val pick: Pick) : Step

    /** Close a held path and refresh its predicate, as after passing it to a borrowed shared parameter. */
    data class Refresh(val pick: Pick) : Step

    /** Fold what can be folded under a held path, keeping its holes, and transfer it to a path of the same class. */
    data class Move(val pick: Pick, val target: Int) : Step
    data object Normalize : Step

    /** An `if`: two arms from the same normalized state, each leaving by [Exit]. */
    data class Branch(val left: List<Step>, val leftExit: Exit, val right: List<Step>, val rightExit: Exit) : Step
}

/**
 * Drives a [FoldTrie] and a [HeapModel] side by side. Every statement the trie emits is replayed on the model, which
 * fails when Viper would reject it, and after every step the trie holds exactly the paths the model holds.
 *
 * With [executeIllegal], operations on paths the model does not hold are run too, and must be refused; a refusal ends
 * the run. Otherwise they are skipped.
 */
class Harness(private val program: TestProgram, private val executeIllegal: Boolean) {
    val trie = FoldTrie(program)

    /** `null` when the state is dead. */
    private var model: HeapModel? = HeapModel(program)

    private val pendingJumps = mutableMapOf<String, HeapModel>()
    private var labels = 0

    data class Saved(val snapshot: FoldTrie.Snapshot<TestRoot, TestField, Int>, val model: HeapModel?)

    fun save() = Saved(trie.snapshot(), model?.copy())

    fun restore(saved: Saved) {
        trie.restore(saved.snapshot)
        model = saved.model?.copy()
    }

    /** Join two saved normalized states into the current state. */
    fun merge(a: Saved, b: Saved) {
        trie.restore(trie.join(a.snapshot, b.snapshot))
        model = joinModels(a.model, b.model)
        checkAgreement()
    }

    private fun joinModels(a: HeapModel?, b: HeapModel?): HeapModel? =
        if (a == null) b?.copy() else if (b == null) a.copy() else a.joinAtLabel(b)

    /** Runs [steps]. Returns false once an operation is refused or the state is dead. */
    fun run(steps: List<Step>): Boolean = steps.all { step ->
        apply(step).also { if (it) checkAgreement() }
    }

    private fun checkAgreement() {
        val heap = model ?: return
        for (path in program.paths) {
            assertEquals(heap.live(path), trie.holds(path), "holds($path)")
        }
    }

    private fun <T> List<T>.pick(index: Int): T? = if (isEmpty()) null else this[index.mod(size)]

    private fun candidates(pick: Pick, legal: (TestPath) -> Boolean) =
        if (pick.wild) program.paths else program.paths.filter(legal)

    /**
     * Runs [action] when it is [legal] or illegal operations are executed, and checks that the trie refuses it exactly
     * when it is illegal. Then replays the emitted statements and applies [effect] to the model.
     */
    private fun attempt(legal: Boolean, action: (RecordingSink) -> Unit, effect: (HeapModel) -> Unit = {}): Boolean {
        if (!legal && !executeIllegal) return true
        val heap = model ?: return false
        val sink = RecordingSink()
        val refused = try {
            action(sink)
            false
        } catch (_: Refused) {
            true
        }
        assertEquals(!legal, refused, "refused")
        if (refused) return false
        for (emitted in sink.emitted) {
            when (emitted.op) {
                Op.UNFOLD -> heap.unfold(emitted.path, emitted.cls)
                Op.FOLD -> heap.fold(emitted.path, emitted.cls)
                Op.REFRESH -> heap.refresh(emitted.path, emitted.cls)
            }
        }
        effect(heap)
        return true
    }

    private fun apply(step: Step): Boolean {
        val heap = model ?: return false
        return when (step) {
            is Step.Acquire -> {
                val acquirable = { p: TestPath -> p.fields.isEmpty() || heap.live(p.parent()) }
                val path = candidates(step.pick, acquirable).pick(step.pick.index) ?: return true
                attempt(acquirable(path), { trie.acquire(it, path) }) {
                    it.leak(path)
                    it.inhale(path)
                }
            }
            is Step.Release -> {
                val path = candidates(step.pick, heap::live).pick(step.pick.index) ?: return true
                val held = heap.live(path)
                attempt(true, { sink ->
                    trie.release(sink, path)
                    if (!held) assertEquals(emptyList(), sink.emitted, "release of an unheld path emits")
                }) { if (held) it.leak(path) }
            }
            is Step.Open -> {
                val path = candidates(step.pick, heap::live).pick(step.pick.index) ?: return true
                val field = program.visibleFields(program.classOf(path)).pick(step.field) ?: return true
                attempt(heap.live(path), { trie.open(it, path, field) }) {
                    assertTrue(it.tokensBelow(path).containsKey(FieldPerms(path, field.owner)), "$field is accessible")
                }
            }
            is Step.OpenOwn -> {
                val path = candidates(step.pick, heap::live).pick(step.pick.index) ?: return true
                val cls = program.classOf(path)
                attempt(heap.live(path), { trie.openOwn(it, path) }) {
                    assertTrue(it.tokensBelow(path).containsKey(FieldPerms(path, cls)), "$path is unfolded")
                }
            }
            is Step.Close -> {
                val path = candidates(step.pick, heap::live).pick(step.pick.index) ?: return true
                close(path)
            }
            is Step.Refresh -> refresh(step, heap)
            is Step.Move -> move(step, heap)
            Step.Normalize -> normalize()
            is Step.Branch -> branch(step)
        }
    }

    private fun refresh(step: Step.Refresh, heap: HeapModel): Boolean {
        val path = candidates(step.pick, heap::live).pick(step.pick.index) ?: return true
        // An illegal close is refused, so the refresh only runs after a legal one.
        if (!closable(path) && !executeIllegal) return true
        return close(path) && attempt(true, { trie.refresh(it, path) }) {
            assertTrue(it.folded(path, program.classOf(path)), "$path holds its predicate")
        }
    }

    private fun move(step: Step.Move, heap: HeapModel): Boolean {
        val source = candidates(step.pick, heap::live).pick(step.pick.index) ?: return true
        val target = program.paths.filter { program.classOf(it) == program.classOf(source) }.pick(step.target)!!
        val holes = heap.holes(source)
        if (!attempt(heap.live(source), { trie.tidy(it, source) }) { tidied ->
                assertEquals(program.shapeTokens(source, holes).groupingBy { it }.eachCount(), tidied.tokensBelow(source))
            }) return false
        // Releasing the source leaves every path outside it as held as before.
        val legal = heap.live(source) && (target.fields.isEmpty() ||
                !target.parent().startsWith(source) && heap.live(target.parent()))
        return attempt(legal, { trie.transfer(it, source, target) }) { it.move(source, target) }
    }

    private fun close(path: TestPath): Boolean {
        val heap = model ?: return false
        return attempt(closable(path), { trie.close(it, path) }) {
            assertTrue(it.tokensBelow(path).keys.none { token -> token is FieldPerms }, "$path is folded")
            assertTrue(it.folded(path, program.classOf(path)), "$path holds its predicate")
        }
    }

    private fun closable(path: TestPath): Boolean = model.let { it != null && it.live(path) && !it.hasHole(path) }

    /** Normalization on the model: every root is brought to the shape of its own holes. */
    private fun normalizedBy(action: (RecordingSink) -> Unit): Boolean {
        val heap = model ?: return true.also { assertEquals(emptyList(), RecordingSink().also(action).emitted) }
        val shapes = heap.roots().associateWith { heap.holes(FoldPath(it)) }
        return attempt(true, action) {
            assertEquals(shapes.keys, it.roots(), "normalize keeps every root")
            for ((root, holes) in shapes) assertShape(it, FoldPath(root), holes)
        }
    }

    private fun assertShape(heap: HeapModel, path: TestPath, holes: List<List<TestField>>) =
        assertEquals(program.shapeTokens(path, holes).groupingBy { it }.eachCount(), heap.tokensBelow(path), "shape of $path")

    /**
     * Bring the state to the shapes two normalized arms merge into: the roots both hold, with the holes either has.
     * Returns false when the state is dead.
     */
    private fun settleForMerge(a: Saved, b: Saved): Boolean {
        val shapes = trie.mergeShapes(a.snapshot, b.snapshot).orEmpty()
        val expected = mergedHoles(a.model, b.model)
        assertEquals(expected, shapes.associate { it.root to it.holes.toSet() }, "merged shapes")
        return attempt(true, { trie.normalizeTo(it, shapes) }) { heap ->
            for (root in heap.roots() - expected.keys) heap.leak(FoldPath(root))
            assertEquals(expected.keys, heap.roots(), "the merge holds the roots both arms hold")
            for ((root, holes) in expected) {
                // The predicates under the holes the other arm has are forgotten.
                for (hole in holes) heap.leak(FoldPath(root, hole))
                assertShape(heap, FoldPath(root), holes.toList())
            }
        }
    }

    private fun mergedHoles(a: HeapModel?, b: HeapModel?): Map<TestRoot, Set<List<TestField>>> {
        if (a == null || b == null) {
            val live = a ?: b ?: return emptyMap()
            return live.roots().associateWith { live.holes(FoldPath(it)).toSet() }
        }
        return (a.roots() intersect b.roots()).associateWith { root ->
            program.outermost(a.holes(FoldPath(root)) + b.holes(FoldPath(root)))
        }
    }

    fun normalize(): Boolean {
        if (!normalizedBy(trie::normalize)) return false
        val again = RecordingSink()
        trie.normalize(again)
        assertEquals(emptyList(), again.emitted, "normalize is idempotent")
        return true
    }

    fun jump(label: String): Boolean {
        if (!normalizedBy { trie.jumpTo(it, label) }) return false
        model?.let { pendingJumps[label] = joinModels(pendingJumps[label], it)!! }
        model = null
        return true
    }

    /** Arrive at [label]. Without a jump to it, nothing changes. */
    fun arrive(label: String): Boolean {
        val jumped = pendingJumps.remove(label)
            ?: return true.also { assertEquals(emptyList(), RecordingSink().also { trie.arriveAt(it, label) }.emitted) }
        if (!normalizedBy { trie.arriveAt(it, label) }) return false
        model = joinModels(model, jumped)
        checkAgreement()
        return true
    }

    fun kill() {
        trie.kill()
        model = null
    }

    private fun branch(step: Step.Branch): Boolean {
        if (!normalize()) return false
        val entry = save()
        val label = "L${labels++}"
        fun arm(steps: List<Step>, exit: Exit): Saved? {
            restore(entry)
            if (!run(steps)) return null
            val leaves = when (exit) {
                Exit.FALL_THROUGH -> normalize()
                Exit.JUMP -> jump(label)
                Exit.DEAD -> true.also { kill() }
            }
            return if (leaves) save() else null
        }
        val left = arm(step.left, step.leftExit) ?: return false
        val right = arm(step.right, step.rightExit) ?: return false
        val settled = listOf(left, right).map { end ->
            restore(end)
            if (model != null && !settleForMerge(left, right)) return false
            save()
        }
        merge(settled[0], settled[1])
        return arrive(label) && model != null
    }
}
