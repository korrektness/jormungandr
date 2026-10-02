// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Node(val value: Int, @property:Unique var next: Node?)

<!VIPER_VERIFICATION_ERROR, VIPER_VERIFICATION_ERROR, VIPER_VERIFICATION_ERROR!>@Pure
@AlwaysVerify
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
}<!>

<!VIPER_VERIFICATION_ERROR!>@AlwaysVerify
@Unique
fun <!VIPER_TEXT!>insertSorted<!>(sorted: @Unique Node?, node: @Unique Node): Node {
    preconditions {
        isSorted(sorted)
        node.next == null
    }
    postconditions<Node> { r -> isSorted(r) && (r.value == node.value || (sorted != null && r.value == sorted.value)) }
    if (sorted == null || node.value <= sorted.value) {
        node.next = sorted
        return node
    }
    sorted.next = insertSorted(sorted.next, node)
    return sorted
}<!>

<!VIPER_VERIFICATION_ERROR!>@AlwaysVerify
@Unique
fun <!VIPER_TEXT!>insertionSort<!>(head: @Unique Node?): Node? {
    var sorted: @Unique Node? = null
    var cur: @Unique Node? = head
    <!VIPER_VERIFICATION_ERROR!>while (cur != null) {
        loopInvariants { isSorted(sorted) }
        val next: @Unique Node? = cur.next
        cur.next = null
        sorted = insertSorted(sorted, cur)
        cur = next
    }<!>
    return sorted
}<!>
