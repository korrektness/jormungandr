// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Pure
import org.jetbrains.kotlin.formver.plugin.Unique
import org.jetbrains.kotlin.formver.plugin.postconditions
import org.jetbrains.kotlin.formver.plugin.preconditions

class Node(val value: Boolean) {
    var next: @Unique Node? = null
}

// Sorted means no `true` value precedes a `false` one.
@Pure
fun isSorted(n: @Unique Node?): Boolean {
    if (n == null) return true
    val next = n.next
    if (next == null) return true
    val ordered = if (n.value) next.value else true
    return ordered && isSorted(next)
}

fun consume(n: @Unique Node?) {}

fun `consume sorted`(n: @Unique Node?) {
    preconditions {
        isSorted(n)
    }
    postconditions<Unit> {
        isSorted(n)
    }
    consume(n)
}
