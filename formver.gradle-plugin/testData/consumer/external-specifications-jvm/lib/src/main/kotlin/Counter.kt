package stand.lib

class Counter(private var current: Int) {
    fun value(): Int = current
    fun advance(step: Int): Int {
        current += step
        return current
    }
}

fun create(initial: Int): Counter = Counter(initial)
fun twice(x: Int): Int = x * 2
fun thrice(x: Int): Int = x * 3
