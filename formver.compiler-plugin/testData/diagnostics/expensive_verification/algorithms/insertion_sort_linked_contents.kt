// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

// The contents specifications of the linked insertion sort. They do not verify yet: the pure function body reads
// `n.next` under an `unfolding` of its own rather than the one around the recursive call.

class Node(val value: Int, var next: @Unique Node?)

@Pure
fun <!VIPER_TEXT!>isSorted<!>(n: @Unique Node?): Boolean {
    if (n == null) {
        return true
    }
    val m = n.next
    return if (m == null) {
        true
    } else {
        n.value <= m.value && isSorted(m)
    }
}

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>contents<!>(n: @Unique Node?): Multiset<Int> =
    if (n == null) multisetOf() else multisetOf(n.value) + contents(n.next)

<!VIPER_VERIFICATION_ERROR!>@AlwaysVerify
fun <!VIPER_TEXT!>insertSorted<!>(sorted: @Unique Node?, node: @Unique Node): @Unique Node {
    preconditions {
        isSorted(sorted)
    }
    postconditions<Node> { r ->
        isSorted(r) && (r.value == node.value || (sorted != null && r.value == sorted.value))
        contents(r) == old(contents(sorted)) + multisetOf(node.value)
    }
    if (sorted == null || node.value <= sorted.value) {
        node.next = sorted
        return node
    }
    sorted.next = insertSorted(sorted.next, node)
    return sorted
}<!>

@AlwaysVerify
fun <!VIPER_TEXT!>insertionSort<!>(head: @Unique Node?): @Unique Node? {
    postconditions<Node?> { r -> isSorted(r) && contents(r) == old(contents(head)) }
    var sorted: @Unique Node? = null
    var cur: @Unique Node? = head
    <!VIPER_VERIFICATION_ERROR!>while (cur != null) {
        loopInvariants {
            isSorted(sorted)
            contents(sorted) + contents(cur) == old(contents(head))
        }
        val next: @Unique Node? = cur.next
        cur.next = null
        sorted = insertSorted(sorted, cur)
        cur = next
    }<!>
    return sorted
}
