/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.uniqueness.plugin

import kotlinx.collections.immutable.persistentMapOf
import net.jqwik.api.Arbitraries
import net.jqwik.api.Assume
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UniquenessTrieProperties {
    @Property
    fun `join is commutative, associative and idempotent`(
        @ForAll("states") a: UniquenessTrie<String>,
        @ForAll("states") b: UniquenessTrie<String>,
        @ForAll("states") c: UniquenessTrie<String>,
    ) {
        assertEquals(a.join(b), b.join(a))
        assertEquals(a.join(b).join(c), a.join(b.join(c)))
        assertEquals(a, a.join(a))
        assertEquals(a, a.join(UniquenessTrie(Uniqueness.Unique)), "the empty state is the identity")
    }

    @Property
    fun `join is an upper bound`(
        @ForAll("states") a: UniquenessTrie<String>,
        @ForAll("states") b: UniquenessTrie<String>,
    ) {
        val joined = a.join(b)
        for (path in joined.allPaths()) {
            val data = listOfNotNull(a.find(path)?.data, b.find(path)?.data)
            assertTrue(data.isNotEmpty(), "$path comes from an input")
            assertEquals(data.reduce(Uniqueness::join), joined.find(path)!!.data, "$path")
        }
        assertEquals(a.data.join(b.data), joined.data)
    }

    @Property
    fun `insert then find round-trips`(
        @ForAll("states") state: UniquenessTrie<String>,
        @ForAll("paths") path: List<String>,
        @ForAll("states") child: UniquenessTrie<String>,
    ) {
        assertEquals(child, state.insert(path, child).find(path))
    }

    @Property
    fun `inconsistent paths are exactly the moved ones`(@ForAll("states") state: UniquenessTrie<String>) {
        val moved = state.allPaths().filter { state.find(it)!!.data == Uniqueness.Moved }.toList()
        assertEquals(moved.toSet(), state.enumerateInconsistentPaths().toSet())
        assertEquals(moved.size, state.enumerateInconsistentPaths().count(), "no path twice")
        val withRoot = if (state.data == Uniqueness.Moved) moved + listOf(emptyList()) else moved
        assertEquals(withRoot.toSet(), state.enumerateInconsistentPaths(includeRoot = true).toSet())
    }

    /**
     * `target = source`: the target holds the source's old substate at its declared uniqueness, the source moves unless
     * it is overwritten, and nothing else changes. A source below the target (`p = p.next`) is read before the write.
     */
    @Property
    fun `assignment moves the source, then writes its old substate to the target`(
        @ForAll("states") state: UniquenessTrie<String>,
        @ForAll("paths") target: List<String>,
        @ForAll("paths") other: List<String>,
        @ForAll sourceBelowTarget: Boolean,
        @ForAll movesSource: Boolean,
        @ForAll("declared") declared: Map<String, Uniqueness>,
    ) {
        val source = if (sourceBelowTarget) target + other.first() else other
        val result = state.assign(accessOf(target), accessOf(source), movesSource, declared::getValue)

        val written = UniquenessTrie(declared.getValue(target.last()), state.find(source)?.children ?: persistentMapOf())
        assertEquals(written, result.find(target), "target")

        if (movesSource && !source.startsWith(target)) {
            val before = state.effectiveUniqueness(source, declared)
            val expected = if (before <= Uniqueness.Unknown) Uniqueness.Moved else before
            assertEquals(expected, result.find(source)!!.data, "source")
        }

        for (path in state.allPaths()) {
            if (path.startsWith(target) || movesSource && path == source) continue
            assertEquals(state.find(path)!!.data, result.find(path)?.data, "$path")
        }
    }

    /** `(if (c) p else q).f = source`: either path may be written, so each joins its old substate with the written one. */
    @Property
    fun `assignment to one of several paths joins each with the written substate`(
        @ForAll("states") state: UniquenessTrie<String>,
        @ForAll("paths") first: List<String>,
        @ForAll("paths") second: List<String>,
        @ForAll("paths") source: List<String>,
        @ForAll("declared") declared: Map<String, Uniqueness>,
    ) {
        Assume.that(!first.startsWith(second) && !second.startsWith(first))
        Assume.that(listOf(first, second).none { source.startsWith(it) || it.startsWith(source) })
        val target = accessOf(first).join(accessOf(second))
        val result = state.assign(target, accessOf(source), movesSource = true, declared::getValue)

        val sourceChildren = state.find(source)?.children ?: persistentMapOf()
        for (path in listOf(first, second)) {
            val written = UniquenessTrie(declared.getValue(path.last()), sourceChildren)
            val old = state.find(path) ?: UniquenessTrie(Uniqueness.Unique)
            assertEquals(old.join(written), result.find(path), "$path")
        }
    }

    /** `a.p = source` through a setter: the setter's receiver `a` moves after the source and before the write. */
    @Property
    fun `a setter's receivers move between the source move and the write`(
        @ForAll("states") state: UniquenessTrie<String>,
        @ForAll("paths") target: List<String>,
        @ForAll("paths") source: List<String>,
        @ForAll("declared") declared: Map<String, Uniqueness>,
    ) {
        Assume.that(target.size > 1)
        val receiver = accessOf(target.dropLast(1))
        var seen: UniquenessTrie<String>? = null
        val result = state.assign(accessOf(target), accessOf(source), movesSource = true, declared::getValue) {
            seen = it
            receiver.move(it, declared::getValue)
        }

        assertEquals(accessOf(source).move(state, declared::getValue), seen, "the hook sees the moved source")
        val afterHook = receiver.move(seen!!, declared::getValue)
        val written = UniquenessTrie(declared.getValue(target.last()), state.find(source)?.children ?: persistentMapOf())
        val expected = accessOf(target).initialize(afterHook.insert(target, written), declared::getValue)
        assertEquals(expected, result)
    }

    private fun <Key> UniquenessTrie<Key>.allPaths(): Sequence<List<Key>> = enumerate { true }

    private fun <T> List<T>.startsWith(prefix: List<T>) = size >= prefix.size && subList(0, prefix.size) == prefix

    /** The uniqueness of [path], with a missing component taking its declared uniqueness joined with its parent's. */
    private fun UniquenessTrie<String>.effectiveUniqueness(path: List<String>, declared: Map<String, Uniqueness>): Uniqueness =
        path.fold(this) { node, key ->
            node.children[key] ?: UniquenessTrie(declared.getValue(key).join(node.data))
        }.data

    private fun accessOf(path: List<String>): AccessTrie<String> =
        path.foldRight(AccessTrie(Access.Terminal)) { key, inner ->
            AccessTrie(Access.Intermediate, persistentMapOf(key to inner))
        }

    private val keys = listOf("a", "b", "c")

    private fun uniqueness() = Arbitraries.of(Uniqueness::class.java)

    private fun states(depth: Int): Arbitrary<UniquenessTrie<String>> {
        if (depth == 0) return uniqueness().map { UniquenessTrie(it) }
        val child = states(depth - 1).injectNull(0.6)
        return Combinators.combine(uniqueness(), child, child, child).`as` { data, a, b, c ->
            val children = keys.zip(listOf(a, b, c)).filter { it.second != null }.associate { it.first to it.second!! }
            UniquenessTrie(data, persistentMapOf<String, UniquenessTrie<String>>().putAll(children))
        }
    }

    @Provide
    fun states(): Arbitrary<UniquenessTrie<String>> = states(3)

    @Provide
    fun paths(): Arbitrary<List<String>> = Arbitraries.of(keys).list().ofMinSize(1).ofMaxSize(3)

    @Provide
    fun declared(): Arbitrary<Map<String, Uniqueness>> =
        uniqueness().list().ofSize(keys.size).map { keys.zip(it).toMap() }
}
