// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

class Node {
    var value: Int = 0
}

fun borrow(node: @Borrowed Node) {}

fun later(block: () -> Unit) {}

fun `lambda captures unique parameter`(node: @Unique Node) {
    later <!INVALID_UNIQUENESS_CAPTURE!>{ borrow(node) }<!>
}

fun `lambda captures borrowed parameter`(node: @Borrowed Node) {
    later <!INVALID_UNIQUENESS_CAPTURE, LOCALITY_MISMATCH!>{ borrow(node) }<!>
}

fun `anonymous object captures unique local`() {
    val node: @Unique Node = Node()
    val obj = <!INVALID_UNIQUENESS_CAPTURE!>object<!> {
        fun touch() { node.value = 1 }
    }
}

fun `local class captures unique parameter`(node: @Unique Node) {
    <!INVALID_UNIQUENESS_CAPTURE!>class Local {
        fun touch() { node.value = 1 }
    }<!>
}

fun `local function captures unique parameter`(node: @Unique Node) {
    <!INVALID_UNIQUENESS_CAPTURE!>fun touch() { node.value = 1 }<!>
}

fun `separate lambda inside in place lambda captures unique parameter`(node: @Unique Node) {
    run {
        later <!INVALID_UNIQUENESS_CAPTURE!>{ borrow(node) }<!>
    }
}

fun @Unique Node.`lambda captures unique receiver`() {
    later <!INVALID_UNIQUENESS_CAPTURE!>{ borrow(this) }<!>
}
