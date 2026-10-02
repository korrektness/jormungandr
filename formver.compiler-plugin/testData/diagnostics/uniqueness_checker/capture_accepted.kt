// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Pure
import org.jetbrains.kotlin.formver.plugin.Unique
import org.jetbrains.kotlin.formver.plugin.postconditions
import org.jetbrains.kotlin.formver.plugin.preconditions

class Node {
    var value: Int = 0
}

fun borrow(node: @Borrowed Node) {}

fun later(block: () -> Unit) {}

fun `in place lambda captures unique parameter`(node: @Unique Node) {
    run { borrow(node) }
}

fun `separate lambda captures shared parameter`(node: Node) {
    later { node.value = 1 }
    val obj = object {
        fun touch() { node.value = 2 }
    }
}

fun `separate lambda declares its own unique local`() {
    later {
        val local: @Unique Node = Node()
        borrow(local)
    }
}

fun `non-capturing lambda beside unique parameter`(node: @Unique Node) {
    later { borrow(Node()) }
    borrow(node)
}

@Pure
fun isZero(node: @Unique Node): Boolean = node.value == 0

fun `specifications capture unique parameter`(node: @Unique Node): Int {
    preconditions { isZero(node) }
    postconditions<Int> { isZero(node) }
    borrow(node)
    return 0
}
