package external.library

class Counter(private var current: Int) {
    init { require(current >= 0) }
    fun value(): Int = current
    val label: Int get() = current
    fun advance(step: Int): Int {
        require(step >= 0 && current <= Int.MAX_VALUE - step)
        current += step
        return current
    }
    fun bounded(limit: Int): Int = current.coerceAtMost(limit)
    fun bounded(enabled: Boolean): Int = if (enabled) current else 0
}

fun create(initial: Int): Counter = Counter(initial)
fun twice(x: Int): Int = x * 2
fun Counter.shift(amount: Int): Int = advance(amount)
fun <T> identity(x: T): T = x

class Cell<T>(private var item: T) {
    fun get(): T = item
    fun set(value: T) { item = value }
}

interface Gauge { fun read(): Int }
open class DefaultGauge : Gauge { override fun read(): Int = 1 }
