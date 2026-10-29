/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.core.linearization

import net.jqwik.api.Example
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private typealias Failure = FoldFailure<TestRoot, TestField, Int, String>

/** Each failure of a [FoldTrie] names the path it needs, the path that holds nothing, and the site that emptied it. */
class FoldFailures {
    private class Failed(val failure: Failure) : Exception(failure.toString())

    /** A sink at a mutable site, recording nothing. */
    private class SiteSink : FoldSink<TestRoot, TestField, Int, String> {
        override var site = "start"
        override fun unfold(path: TestPath, cls: Int) {}
        override fun fold(path: TestPath, cls: Int) {}
        override fun refresh(path: TestPath, cls: Int) {}
        override fun refreshOwn(path: TestPath, cls: Int) {}
        override fun fail(failure: Failure): Nothing = throw Failed(failure)
    }

    // Class 0 has a field of class 1; neither has a superclass. Roots `a` and `b` are of class 0, `c` of class 1.
    private val program = TestProgram.of(listOf(null, null), listOf(listOf(1), listOf()), listOf(0, 0, 1))
    private val field = program.fields.single()
    private val a = FoldPath<TestRoot, TestField>(program.roots[0])
    private val b = FoldPath<TestRoot, TestField>(program.roots[1])
    private val c = FoldPath<TestRoot, TestField>(program.roots[2])
    private val trie = FoldTrie<TestRoot, TestField, Int, String>(program)
    private val sink = SiteSink()

    private fun failure(at: String, action: () -> Unit): Failure {
        sink.site = at
        return assertFailsWith<Failed> { action() }.failure
    }

    @Example
    fun `a root never acquired is not held`() {
        assertEquals<Failure>(
            FoldFailure.NotHeld(a, a, Absence.NeverHeld),
            failure("read") { trie.open(sink, a, field) },
        )
    }

    @Example
    fun `a released root names the release site`() {
        trie.acquire(sink, a)
        sink.site = "consume"
        trie.release(sink, a)
        assertEquals<Failure>(
            FoldFailure.NotHeld(a + field, a, Absence.Released("consume")),
            failure("read") { trie.openOwn(sink, a + field) },
        )
    }

    @Example
    fun `a moved root names the target and the move site`() {
        trie.acquire(sink, a)
        sink.site = "move"
        trie.transfer(sink, a, b)
        assertEquals<Failure>(
            FoldFailure.NotHeld(a, a, Absence.Moved(b, "move")),
            failure("read") { trie.open(sink, a, field) },
        )
    }

    @Example
    fun `a root held on one arm only is forgotten at the join`() {
        val entry = trie.snapshot()
        trie.acquire(sink, a)
        val held = trie.snapshot()
        trie.restore(trie.join(held, entry, "join"))
        assertEquals<Failure>(
            FoldFailure.NotHeld(a, a, Absence.Forgotten("join")),
            failure("read") { trie.open(sink, a, field) },
        )
    }

    @Example
    fun `folding over a moved-out field names the hole and its move site`() {
        trie.acquire(sink, a)
        sink.site = "move field"
        trie.transfer(sink, a + field, c)
        assertEquals<Failure>(
            FoldFailure.HoleBelow(a, a + field, Absence.Moved(c, "move field")),
            failure("fold") { trie.close(sink, a) },
        )
    }

    @Example
    fun `exposing a class that is not a supertype names it`() {
        trie.acquire(sink, a)
        assertEquals<Failure>(
            FoldFailure.NotASupertype(a, 1),
            failure("expose") { trie.expose(sink, a, 1) },
        )
    }
}
