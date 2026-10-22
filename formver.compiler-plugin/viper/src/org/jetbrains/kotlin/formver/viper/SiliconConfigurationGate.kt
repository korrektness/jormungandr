package org.jetbrains.kotlin.formver.viper

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Verification shares Silicon's JVM-global configuration only with identical options. Backend initialization and
 * teardown take an exclusive lease: they start, iterate and clear the JVM-global recorder collections.
 * [awaitRelease] blocks on the gate's condition until conflicting leases finish.
 */
class SiliconConfigurationGate(private val awaitRelease: (Condition) -> Unit = { it.await() }) {
    private val lock = ReentrantLock(true)
    private val available = lock.newCondition()
    private var configuration: List<String>? = null
    private var active = 0
    private var exclusive = false

    fun <T> withConfiguration(options: List<String>, action: () -> T): T = withLease(options, false, action)

    fun <T> withExclusiveConfiguration(options: List<String>, action: () -> T): T = withLease(options, true, action)

    private fun <T> withLease(options: List<String>, exclusive: Boolean, action: () -> T): T {
        lock.withLock {
            while (active != 0) {
                if (!exclusive && !this.exclusive && configuration == options) break
                awaitRelease(available)
            }
            configuration = options
            this.exclusive = exclusive
            active++
        }
        try {
            return action()
        } finally {
            lock.withLock {
                active--
                if (active == 0) available.signalAll()
            }
        }
    }
}
