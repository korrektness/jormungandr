// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Node(var value: Int, var next: @Unique Node?)

// A list has no bound on its length, so the addition in `length` is an overflow-negative test and callers see no
// definition of `length`.
@Pure
fun <!VIPER_TEXT!>length<!>(n: @Unique Node?): Int {
    preconditions { decreases(n) }
    return if (n == null) 0 else <!INTEGER_OVERFLOW!>1 + length(n.next)<!>
}

// `capLength` saturates, so `cappedLength` stays within Int on any list. A saturated sum of capped lengths is
// conserved as nodes move from one list to the other.
@Pure
fun <!VIPER_TEXT!>capLength<!>(k: Int): Int = if (k > 1000000) 1000000 else k

@Pure
fun <!VIPER_TEXT!>cappedLength<!>(n: @Unique Node?): Int {
    preconditions { decreases(n) }
    postconditions<Int> { r -> 0 <= r && r <= 1000000 }
    return if (n == null) 0 else capLength(1 + cappedLength(n.next))
}

fun <!VIPER_TEXT!>reverse<!>(head: @Unique Node?): @Unique Node? {
    postconditions<Node?> { r -> cappedLength(r) == old(cappedLength(head)) }
    var acc: @Unique Node? = null
    var cur: @Unique Node? = head
    while (cur != null) {
        loopInvariants { capLength(cappedLength(acc) + cappedLength(cur)) == old(cappedLength(head)) }
        val nxt: @Unique Node? = cur.next
        cur.next = acc
        acc = cur
        cur = nxt
    }
    return acc
}
