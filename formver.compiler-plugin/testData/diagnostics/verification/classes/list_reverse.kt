// FULL_JDK
// NEVER_VALIDATE

import org.jetbrains.kotlin.formver.plugin.*

class Node(var value: Int, var next: @Unique Node?)

@Pure
fun <!VIPER_TEXT!>length<!>(n: @Unique Node?): Int = if (n == null) 0 else 1 + length(n.next)

fun <!VIPER_TEXT!>reverseWithOld<!>(head: @Unique Node?): @Unique Node? {
    postconditions<Node?> { r -> length(r) == old(length(head)) }
    var acc: @Unique Node? = null
    var cur: @Unique Node? = head
    while (cur != null) {
        loopInvariants { length(acc) + length(cur) == old(length(head)) }
        val nxt: @Unique Node? = cur.next
        cur.next = acc
        acc = cur
        cur = nxt
    }
    return acc
}

fun <!VIPER_TEXT!>reverseWithGhost<!>(head: @Unique Node?, n: Int): @Unique Node? {
    preconditions { length(head) == n }
    postconditions<Node?> { r -> length(r) == n }
    var acc: @Unique Node? = null
    var cur: @Unique Node? = head
    while (cur != null) {
        loopInvariants { length(acc) + length(cur) == n }
        val nxt: @Unique Node? = cur.next
        cur.next = acc
        acc = cur
        cur = nxt
    }
    return acc
}
