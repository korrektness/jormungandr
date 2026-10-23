package org.jetbrains.kotlin.formver.plugin.services

import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Bounds retained and checked-out resources together, across configuration keys. */
class TestVerifierPool<K, V : Closeable>(
    private val capacity: Int,
    private val awaitAvailable: (Condition) -> Unit = { it.await() },
    private val create: (K) -> V,
) : Closeable {
    init {
        require(capacity > 0)
    }

    private val lock = ReentrantLock()
    private val available = lock.newCondition()
    private val idle = mutableListOf<Pair<K, V>>()
    private var owned = 0
    private var closed = false

    /** The caller must close the lease; a lease belongs exclusively to that caller. */
    fun checkout(key: K): Lease = lock.withLock {
        while (true) {
            check(!closed) { "Verifier pool is closed." }
            val index = idle.indexOfFirst { it.first == key }
            if (index >= 0) return Lease(key, idle.removeAt(index).second)
            if (owned == capacity && idle.isNotEmpty()) {
                val evicted = idle.removeAt(0).second
                try {
                    evicted.close()
                } finally {
                    owned--
                    available.signalAll()
                }
            }
            if (owned < capacity) {
                val resource = create(key)
                owned++
                return Lease(key, resource)
            }
            awaitAvailable(available)
        }
        @Suppress("UNREACHABLE_CODE")
        error("Unreachable")
    }

    inner class Lease internal constructor(private val key: K, private val resource: V) : Closeable {
        private var returned = false
        private var failed = false

        /** Any exceptional operation makes this resource ineligible for reuse. */
        fun <R> run(action: (V) -> R): R {
            check(!returned && !failed)
            try {
                return action(resource)
            } catch (error: Throwable) {
                failed = true
                throw error
            }
        }

        override fun close() = lock.withLock {
            if (returned) return
            returned = true
            if (failed || closed) {
                try {
                    resource.close()
                } finally {
                    owned--
                    available.signalAll()
                }
            } else {
                idle.add(key to resource)
                available.signalAll()
            }
        }

    }

    /** Checked-out resources close on return; idle resources close immediately. */
    override fun close(): Unit = lock.withLock {
        closed = true
        val resources = idle.map { it.second }
        idle.clear()
        owned -= resources.size
        available.signalAll()
        var failure: Throwable? = null
        resources.forEach {
            try {
                it.close()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}

/** Every constructor input that can vary between test cases participates in reuse. */
data class TestVerifierConfiguration(
    val z3: String,
    val counterexamples: Boolean,
    val timeoutSeconds: Int,
    val cacheDirectory: Path?,
    val cacheReportDirectory: Path?,
    val cacheRefresh: Boolean,
    val cacheColdSample: String?,
)
