// Run with the ArrayDeque class under test first on the JVM classpath.
private class ContainsFailure : RuntimeException()

private class ThrowingContains(private val throwAt: Int) : AbstractCollection<Int>() {
    private var calls = 0
    override val size: Int get() = 1
    override fun iterator(): Iterator<Int> = listOf(0).iterator()
    override fun contains(element: Int): Boolean {
        if (calls++ == throwAt) throw ContainsFailure()
        return element % 2 == 0
    }
}

fun main() {
    for (wrapped in listOf(false, true)) {
        for (retain in listOf(false, true)) {
            val deque = ArrayDeque<Int>(8)
            if (wrapped) {
                repeat(5) { deque.addLast(0) }
                repeat(5) { deque.removeFirst() }
            }
            deque.addAll(0..6)
            try {
                if (retain) deque.retainAll(ThrowingContains(3))
                else deque.removeAll(ThrowingContains(3))
                error("contains did not throw")
            } catch (expected: ContainsFailure) {
                println("wrapped=$wrapped retain=$retain: $deque size=${deque.size}")
            }
            try {
                println("sum=${deque.sum()}")
            } catch (failure: NullPointerException) {
                println("sum: ${failure.javaClass.simpleName}")
            }
        }
    }
    for (retain in listOf(false, true)) {
        val list = java.util.ArrayList((0..6).toList())
        try {
            if (retain) list.retainAll(ThrowingContains(3))
            else list.removeAll(ThrowingContains(3))
            error("contains did not throw")
        } catch (expected: ContainsFailure) {
            println("java.util.ArrayList retain=$retain: $list size=${list.size}")
        }
    }
}
