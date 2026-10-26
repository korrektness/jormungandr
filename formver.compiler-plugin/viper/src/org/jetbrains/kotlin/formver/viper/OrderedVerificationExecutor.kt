package org.jetbrains.kotlin.formver.viper

import java.io.Closeable
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/** Shares a bounded verifier budget and returns results in submission order. */
class OrderedVerificationExecutor(private val parallelism: Int) : Closeable {
    private val executor = Executors.newFixedThreadPool(parallelism) { task ->
        Thread(task, "formver-verification").apply { isDaemon = true }
    }

    private val lifecycle = Any()
    private val active = mutableSetOf<Future<*>>()
    private var closed = false

    fun <T, R> map(inputs: List<T>, action: (T) -> R): List<R> {
        val completions = LinkedBlockingQueue<Future<R>>()
        val futures = mutableListOf<Future<R>>()
        try {
            inputs.forEach { input ->
                val task = object : FutureTask<R>(Callable { action(input) }) {
                    override fun done() {
                        synchronized(lifecycle) { active.remove(this) }
                        // Cancellation must publish completion even if shutdown discards the queued task.
                        completions.add(this)
                    }
                }
                futures += task
                synchronized(lifecycle) {
                    if (closed) throw RejectedExecutionException("Verification executor is closed")
                    active += task
                    executor.execute(task)
                }
            }
            // Observe failures in completion order, so a blocked earlier job cannot hide a later job's failure.
            repeat(futures.size) { completions.take().get() }
            return futures.map { it.get() }
        } catch (error: ExecutionException) {
            throw error.cause!!
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } finally {
            // Also cancel accepted jobs when submission fails partway through the batch.
            futures.forEach { it.cancel(true) }
        }
    }

    /** Each active worker owns its resource until all its jobs finish. */
    fun <T, R, C : Closeable> mapWithResource(inputs: List<T>, resource: () -> C, action: (C, T) -> R): List<R> {
        val next = AtomicInteger()
        return map((0 until minOf(parallelism, inputs.size)).toList()) {
            resource().use { owned ->
                buildList {
                    while (true) {
                        val index = next.getAndIncrement()
                        if (index >= inputs.size) break
                        add(index to action(owned, inputs[index]))
                    }
                }
            }
        }.flatten().sortedBy { it.first }.map { it.second }
    }

    /** Cancels every accepted job and wakes its caller without waiting for worker cleanup. */
    override fun close() {
        synchronized(lifecycle) {
            closed = true
            executor.shutdownNow()
            // done() removes tasks from active synchronously during cancellation.
            active.toList().forEach { it.cancel(true) }
        }
    }

    companion object {
        val shared = OrderedVerificationExecutor(VerificationConcurrency.jobs)
    }
}
