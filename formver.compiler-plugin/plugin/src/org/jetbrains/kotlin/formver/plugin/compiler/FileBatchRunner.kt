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
    fun <T, R : Any, S : AutoCloseable> run(
        inputs: List<T>, workers: Int, resource: () -> S, execute: (S, T) -> R,
    ): List<R> {
        require(workers > 0)
        if (Thread.currentThread().isInterrupted) throw InterruptedException("File verification interrupted")
        if (inputs.isEmpty()) return emptyList()
        val next = AtomicInteger()
        val stopped = AtomicBoolean()
        val results = AtomicReferenceArray<R>(inputs.size)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val threads = mutableListOf<Thread>()
        try {
            repeat(minOf(workers, inputs.size)) {
                val thread = Thread {
                    try {
                        resource().use { owned ->
                            while (!stopped.get()) {
                                val index = next.getAndIncrement()
                                if (index >= inputs.size) break
                                results.set(index, execute(owned, inputs[index]))
                            }
                        }
                    } catch (error: Throwable) {
                        failures.add(error)
                        stopped.set(true)
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
        for (thread in threads) {
            while (thread.isAlive) {
                try {
                    thread.join()
                } catch (error: InterruptedException) {
                    if (interrupted == null) interrupted = error
                    // Let in-flight verification and close finish without interrupting cleanup.
                    stopped.set(true)
                }
            }
        }
        if (Thread.interrupted() && interrupted == null) interrupted = InterruptedException("File verification interrupted")
        if (interrupted != null) {
            failures.forEach { interrupted.addSuppressed(it) }
            Thread.currentThread().interrupt()
            throw interrupted
        }
        val failure = failures.poll()
        if (failure != null) {
            failures.forEach { failure.addSuppressed(it) }
            throw failure
        }
        return inputs.indices.map { checkNotNull(results.get(it)) }
    }
}
