/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.api.Tuple
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoldTrieProperties {
    @Property(tries = 300)
    fun `emitted statements replay on the held permissions`(
        @ForAll("programs") program: TestProgram,
        @ForAll("steps") steps: List<Step>,
    ) {
        Harness(program, executeIllegal = true).run(steps)
    }

    @Property(tries = 200)
    fun `join of normalized states is a lattice join with dead as identity`(
        @ForAll("programs") program: TestProgram,
        @ForAll("arms") a: List<Step>,
        @ForAll("arms") b: List<Step>,
        @ForAll("arms") c: List<Step>,
    ) {
        val trie = FoldTrie<TestRoot, TestField, Int, Unit>(program)
        val (x, y, z) = listOf(a, b, c).map { normalizedState(program, it).snapshot }
        val dead = FoldTrie<TestRoot, TestField, Int, Unit>(program).apply { kill() }.snapshot()
        fun FoldTrie.Snapshot<TestRoot, TestField, Int, Unit>.shape() = roots?.mapValues { it.value.second.status }

        assertEquals(trie.join(x, y, Unit).shape(), trie.join(y, x, Unit).shape(), "commutative")
        assertEquals(trie.join(trie.join(x, y, Unit), z, Unit).shape(), trie.join(x, trie.join(y, z, Unit), Unit).shape(), "associative")
        assertEquals(x.shape(), trie.join(x, x, Unit).shape(), "idempotent")
        assertEquals(x.shape(), trie.join(x, dead, Unit).shape(), "dead is a right identity")
        assertEquals(x.shape(), trie.join(dead, x, Unit).shape(), "dead is a left identity")
        val joined = FoldTrie<TestRoot, TestField, Int, Unit>(program).apply { restore(join(x, y, Unit)) }
        val left = FoldTrie<TestRoot, TestField, Int, Unit>(program).apply { restore(x) }
        val right = FoldTrie<TestRoot, TestField, Int, Unit>(program).apply { restore(y) }
        for (path in program.paths) {
            if (joined.holds(path)) assertTrue(left.holds(path) && right.holds(path), "join holds no more than $path")
        }
    }

    @Property(tries = 200)
    fun `jumps to a label join like normalized arms in any order`(
        @ForAll("programs") program: TestProgram,
        @ForAll("arms") prefix: List<Step>,
        @ForAll("armLists") arms: List<List<Step>>,
        @ForAll seed: Long,
    ) {
        val jumping = Harness(program, executeIllegal = false)
        jumping.run(prefix)
        val entry = jumping.save()
        for (arm in arms.dropLast(1)) {
            jumping.restore(entry)
            jumping.run(arm)
            jumping.jump("exit")
        }
        jumping.restore(entry)
        jumping.run(arms.last())
        jumping.arrive("exit")
        jumping.normalize()

        val merging = Harness(program, executeIllegal = false)
        merging.run(prefix)
        val start = merging.save()
        val ends = arms.map { arm ->
            merging.restore(start)
            merging.run(arm)
            merging.normalize()
            merging.save()
        }.shuffled(Random(seed))
        merging.restore(ends.first())
        for (end in ends.drop(1)) merging.merge(merging.save(), end)

        for (path in program.paths) {
            assertEquals(merging.trie.holds(path), jumping.trie.holds(path), "holds($path)")
        }
    }

    private fun normalizedState(program: TestProgram, steps: List<Step>): Harness.Saved =
        with(Harness(program, executeIllegal = false)) {
            run(steps)
            normalize()
            save()
        }

    @Provide
    fun programs(): Arbitrary<TestProgram> = Arbitraries.integers().between(1, 3).flatMap { classes ->
        val superclasses = (0 until classes).map { cls ->
            Arbitraries.integers().between(cls + 1, classes).map { it.takeIf { it < classes } }
        }
        val fieldClasses = (0 until classes).map {
            Arbitraries.integers().between(0, classes - 1).list().ofMaxSize(2)
        }
        val rootClasses = Arbitraries.integers().between(0, classes - 1).list().ofMinSize(1).ofMaxSize(3)
        Combinators.combine(
            Combinators.combine(superclasses).`as` { it },
            Combinators.combine(fieldClasses).`as` { it },
            rootClasses,
        ).`as`(TestProgram::of)
    }

    private fun picks(): Arbitrary<Pick> = Combinators.combine(
        Arbitraries.integers().between(0, 1000),
        Arbitraries.frequency(Tuple.of(1, true), Tuple.of(9, false)),
    ).`as`(::Pick)

    private fun simpleSteps(): Arbitrary<Step> {
        val index = Arbitraries.integers().between(0, 1000)
        return Arbitraries.frequencyOf(
            Tuple.of(3, picks().map(Step::Acquire)),
            Tuple.of(2, picks().map(Step::Release)),
            Tuple.of(5, Combinators.combine(picks(), index).`as`(Step::Open)),
            Tuple.of(2, picks().map(Step::Close)),
            Tuple.of(1, picks().map(Step::OpenOwn)),
            Tuple.of(1, picks().map(Step::Refresh)),
            Tuple.of(2, Combinators.combine(picks(), index).`as`(Step::Expose)),
            Tuple.of(3, Combinators.combine(picks(), index).`as`(Step::Borrow)),
            Tuple.of(2, Combinators.combine(picks(), index).`as`(Step::Move)),
            Tuple.of(1, Arbitraries.just(Step.Normalize)),
        )
    }

    @Provide
    fun arms(): Arbitrary<List<Step>> = simpleSteps().list().ofMaxSize(8)

    @Provide
    fun armLists(): Arbitrary<List<List<Step>>> = arms().list().ofMinSize(1).ofMaxSize(3)

    @Provide
    fun steps(): Arbitrary<List<Step>> {
        val exits = Arbitraries.of(Exit::class.java)
        val branches = Combinators.combine(arms(), exits, arms(), exits).`as`(Step::Branch)
        return Arbitraries.frequencyOf(Tuple.of(9, simpleSteps()), Tuple.of(1, branches)).list().ofMaxSize(25)
    }
}
