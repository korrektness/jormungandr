// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Pure
import org.jetbrains.kotlin.formver.plugin.Unique
import org.jetbrains.kotlin.formver.plugin.postconditions

class Node(val value: Boolean) {
    var next: @Unique Node? = null
}

@Pure
fun isUnique(n: @Unique Node?): Boolean = true

fun fresh(value: Boolean): @Unique Node {
    postconditions<Node> { r -> isUnique(r) }
    return Node(value)
}

fun freshImplicit(value: Boolean): @Unique Node {
    postconditions<Node> { isUnique(it) }
    return Node(value)
}

fun shared(n: Node): Node {
    postconditions<Node> { r -> isUnique(<!UNIQUENESS_MISMATCH!>r<!>) }
    return n
}
