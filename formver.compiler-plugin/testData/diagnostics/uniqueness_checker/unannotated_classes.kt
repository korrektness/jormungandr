// UNIQUE_CHECK_ONLY
// FULL_JDK

// No annotation anywhere: ordinary Kotlin that both checkers accept.

abstract class Node(open val lineNo: Int) {
    abstract var key: String
}

class Leaf(override var key: String, override val lineNo: Int) : Node(lineNo) {
    val name: String = key
    val line: Int = lineNo + 1
}

class Parser(first: String) {
    private var depth = 0

    init {
        track(first)
    }

    private fun track(s: String) {
        depth += s.length
    }
}

class Value(val s: String, val n: Int)

private inline fun String.tryParse(n: Int, make: (String, Int) -> Value): Value? = make(this, n)

fun parse(s: String, n: Int): Value? = s.tryParse(n, ::Value)
