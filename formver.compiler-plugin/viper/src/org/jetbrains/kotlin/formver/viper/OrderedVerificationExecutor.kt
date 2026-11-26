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
        val batch = CompletionBatch<R>()
        return awaitBatch(batch) { inputs.forEach { submit(batch, it, action) } }
    }

    private fun <T, R> submit(batch: CompletionBatch<R>, input: T, action: (T) -> R) {
        val task = CompletionTask(WorkerCall(input, action)) { finished ->
            synchronized(lifecycle) { active.remove(finished) }
            // Cancellation must publish completion even if shutdown discards the queued task.
            batch.publish(finished)
        }
        batch.add(task)
        synchronized(lifecycle) {
            if (closed) throw RejectedExecutionException("Verification executor is closed")
            active += task
            executor.execute(task)
        }
    }

    private fun <R> awaitBatch(batch: CompletionBatch<R>, submit: () -> Unit): List<R> {
        try {
            submit()
            return batch.results()
        } catch (error: ExecutionException) {
            throw error.cause!!
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } finally {
            // Also cancel accepted jobs when submission fails partway through the batch.
            batch.cancel()
        }
    }

    private class CompletionBatch<R> {
        private val completions = LinkedBlockingQueue<Future<R>>()
        private val futures = mutableListOf<Future<R>>()

        fun add(task: Future<R>) {
            futures.add(task)
        }

        fun publish(task: Future<R>) {
            completions.add(task)
        }

        fun results(): List<R> {
            // Observe failures in completion order, so a blocked earlier job cannot hide a later job's failure.
            repeat(futures.size) { completions.take().get() }
            return futures.map { it.get() }
        }

        fun cancel() = futures.forEach { it.cancel(true) }
    }

    private class WorkerCall<T, R>(private val input: T, private val action: (T) -> R) : Callable<R> {
        override fun call(): R = action(input)
    }

    private class CompletionTask<R>(action: Callable<R>, private val onDone: (Future<R>) -> Unit) : FutureTask<R>(action) {
        override fun done() = onDone(this)
    }

    /** Each active worker owns its resource until all its jobs finish. */
    fun <T, R, C : Closeable> mapWithResource(inputs: List<T>, resource: () -> C, action: (C, T) -> R): List<R> {
        val batch = ResourceBatch(inputs, resource, action)
        val workers = (0 until minOf(parallelism, inputs.size)).toList()
        return map(workers) { batch.runWorker() }.flatten().sortedBy { it.first }.map { it.second }
    }

    private class ResourceBatch<T, R, C : Closeable>(
        private val inputs: List<T>,
        private val resource: () -> C,
        private val action: (C, T) -> R,
    ) {
        private val next = AtomicInteger()

        fun runWorker(): List<Pair<Int, R>> = resource().use { owned ->
            buildList {
                while (true) {
                    val index = next.getAndIncrement()
                    if (index >= inputs.size) break
                    add(index to action(owned, inputs[index]))
                }
            }
        }
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
