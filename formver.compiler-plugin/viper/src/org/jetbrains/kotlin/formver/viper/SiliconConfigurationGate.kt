package org.jetbrains.kotlin.formver.viper

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Silicon's JVM-global configuration permits concurrent operations only with identical options.
 * [awaitRelease] blocks on the gate's condition while another configuration holds it.
 */
class SiliconConfigurationGate(private val awaitRelease: (Condition) -> Unit = { it.await() }) {
    private val lock = ReentrantLock()
    private val available = lock.newCondition()
    private var configuration: List<String>? = null
    private var active = 0

    fun <T> withConfiguration(options: List<String>, action: () -> T): T {
        lock.withLock {
            while (active != 0 && configuration != options) awaitRelease(available)
            configuration = options
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
