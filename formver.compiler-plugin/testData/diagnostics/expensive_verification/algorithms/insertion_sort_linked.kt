// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Node(val value: Int, @property:Unique var next: Node?)

@Pure
@AlwaysVerify
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>isSorted<!>(n: @Unique Node?): Boolean {
    if (n == null) {
        return true
    }
    val m = n.next
    return if (m == null) {
        true
    } else {
        n.value <= m.value && isSorted(<!UNIQUENESS_MISMATCH!>m<!>)
    }
}

@AlwaysVerify
@Unique
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>insertSorted<!>(sorted: @Unique Node?, node: @Unique Node): Node {
    preconditions {
        isSorted(sorted)
        node.next == null
    }
    postconditions<Node> { r -> isSorted(<!UNIQUENESS_MISMATCH!>r<!>) && (r.value == node.value || (sorted != null && r.value == sorted.value)) }
    if (sorted == null || node.value <= sorted.value) {
        node.next = sorted
        return node
    }
    sorted.next = insertSorted(<!UNIQUENESS_MISMATCH!>sorted.next<!>, node)
    return sorted
}

@AlwaysVerify
@Unique
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>insertionSort<!>(head: @Unique Node?): Node? {
    var sorted: @Unique Node? = null
    var cur: @Unique Node? = head
    while (cur != null) {
        loopInvariants { isSorted(sorted) }
        val next: @Unique Node? = <!UNIQUENESS_MISMATCH!>cur.next<!>
        cur.next = null
        sorted = <!UNIQUENESS_MISMATCH!>insertSorted(sorted, cur)<!>
        cur = next
    }
    return sorted
}
