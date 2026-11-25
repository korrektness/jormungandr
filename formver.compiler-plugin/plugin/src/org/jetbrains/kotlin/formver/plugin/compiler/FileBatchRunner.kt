/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.formver.plugin.compiler

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray

/** File-scoped workers. Returning, including exceptionally, means every started thread has exited. */
internal object FileBatchRunner {
    /** Results in input order, including terminal job failures. Null slots have no result.
     * [interruption] carries the caller's cleared interrupt; the caller must restore it after delivery.
     */
    data class Batch<R : Any>(val results: List<R?>, val failures: List<Throwable>, val interruption: InterruptedException?)

    fun <T, R : Any, S : AutoCloseable> run(
        inputs: List<T>, workers: Int, resource: () -> S,
        terminalFailure: (R) -> Throwable? = { null }, execute: (S, T) -> R,
    ): Batch<R> {
        require(workers > 0)
        if (Thread.interrupted()) {
            return Batch(List(inputs.size) { null }, emptyList(), InterruptedException("File verification interrupted"))
        }
        val next = AtomicInteger()
        val stopped = AtomicBoolean()
        val results = AtomicReferenceArray<R>(inputs.size)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val threads = mutableListOf<Thread>()
        try {
            repeat(minOf(workers, inputs.size)) {
                val thread = Thread {
                    var interrupted = false
                    try {
                        resource().use { owned ->
                            while (!stopped.get()) {
                                val index = next.getAndIncrement()
                                if (index >= inputs.size) break
                                val result = execute(owned, inputs[index])
                                results.set(index, result)
                                val failure = terminalFailure(result)
                                if (failure != null) {
                                    stopped.set(true)
                                    val flagged = Thread.interrupted()
                                    interrupted = flagged || failure is InterruptedException
                                    break
                                }
                            }
                        }
                    } catch (error: Throwable) {
                        failures.add(error)
                        stopped.set(true)
                        interrupted = interrupted || error is InterruptedException
                    } finally {
                        if (interrupted) Thread.currentThread().interrupt()
                    }
                }
                thread.name = "formver-file-worker"
                threads.add(thread)
                thread.start()
            }
        } catch (error: Throwable) {
            failures.add(error)
            stopped.set(true)
        }

        var interrupted: InterruptedException? = null
        fun recordInterruption(error: InterruptedException) {
            val first = interrupted
            if (first == null) interrupted = error else first.addSuppressed(error)
        }
        for (thread in threads) {
            while (thread.isAlive) {
                try {
                    thread.join()
                } catch (error: InterruptedException) {
                    recordInterruption(error)
                    stopped.set(true)
                }
            }
        }
        if (Thread.interrupted()) recordInterruption(InterruptedException("File verification interrupted"))
        return Batch(inputs.indices.map { results.get(it) }, failures.toList(), interrupted)
    }
}
