package org.jetbrains.kotlin.formver.viper

import java.io.Closeable
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Shares a bounded verifier budget and returns results in submission order. */
class OrderedVerificationExecutor(private val parallelism: Int) : Closeable {
    private val executor = Executors.newFixedThreadPool(parallelism) { task ->
        Thread(task, "formver-verification").apply { isDaemon = true }
    }

    fun <T, R> map(inputs: List<T>, action: (T) -> R): List<R> {
        val futures = inputs.map { input -> executor.submit(Callable { action(input) }) }
        try {
            return futures.map { it.get() }
        } catch (error: ExecutionException) {
            futures.forEach { it.cancel(true) }
            throw error.cause!!
        } catch (error: InterruptedException) {
            futures.forEach { it.cancel(true) }
            Thread.currentThread().interrupt()
            throw error
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

    override fun close() {
        executor.shutdownNow()
    }

    companion object {
        val shared = OrderedVerificationExecutor(VerificationConcurrency.jobs)
    }
}
