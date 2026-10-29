// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Unique

class Node(var next: @Unique Node?)

fun consume(n: @Unique Node?) {}

fun `reversal keeps the list whole`(head: @Unique Node?): @Unique Node? {
    var acc: @Unique Node? = null
    var cur: @Unique Node? = head
    while (cur != null) {
        val nxt: @Unique Node? = cur.next
        cur.next = acc
        acc = cur
        cur = nxt
    }
    return acc
}

fun `reversal onto a list with a hole keeps the hole`(head: @Unique Node, tail: @Unique Node): @Unique Node? {
    consume(tail.next)
    var acc: @Unique Node? = tail
    val first: @Unique Node? = head.next
    head.next = acc
    acc = head
    var cur: @Unique Node? = first
    while (cur != null) {
        val nxt: @Unique Node? = cur.next
        cur.next = acc
        acc = cur
        cur = nxt
    }
    return <!ESCAPE_UNIQUENESS_INCONSISTENCY!>acc<!>
}

fun `reading a path that a join truncated is a use after move`(head: @Unique Node, tail: @Unique Node) {
    consume(tail.next)
    var acc: @Unique Node? = tail
    val first: @Unique Node? = head.next
    head.next = acc
    acc = head
    var cur: @Unique Node? = first
    while (cur != null) {
        val nxt: @Unique Node? = cur.next
        cur.next = acc
        acc = cur
        cur = nxt
    }
    consume(<!INVALID_MOVED_ACCESS!>acc.next<!>)
}
