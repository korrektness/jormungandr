// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Node(val value: Int, var next: @Unique Node?)

@Pure
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
}

@AlwaysVerify
fun <!VIPER_TEXT!>insertSorted<!>(sorted: @Unique Node?, node: @Unique Node): @Unique Node {
    preconditions {
        isSorted(sorted)
    }
    postconditions<Node> { r -> isSorted(r) && (r.value == node.value || (sorted != null && r.value == sorted.value)) }
    if (sorted == null || node.value <= sorted.value) {
        node.next = sorted
        return node
    }
    sorted.next = insertSorted(sorted.next, node)
    return sorted
}

@AlwaysVerify
fun <!VIPER_TEXT!>insertionSort<!>(head: @Unique Node?): @Unique Node? {
    postconditions<Node?> { r -> isSorted(r) }
    var sorted: @Unique Node? = null
    var cur: @Unique Node? = head
    while (cur != null) {
        loopInvariants { isSorted(sorted) }
        val next: @Unique Node? = cur.next
        cur.next = null
        sorted = insertSorted(sorted, cur)
        cur = next
    }
    return sorted
}

@AlwaysVerify
fun <!VIPER_TEXT!>insertSortedProbeStart<!>(sorted: @Unique Node?, node: @Unique Node): @Unique Node {
    preconditions {
        isSorted(sorted)
    }
    postconditions<Node> { r -> isSorted(r) && (r.value == node.value || (sorted != null && r.value == sorted.value)) }
    verify(<!VIPER_VERIFICATION_ERROR!>false<!>)
    if (sorted == null || node.value <= sorted.value) {
        node.next = sorted
        return node
    }
    sorted.next = insertSorted(sorted.next, node)
    return sorted
}

@AlwaysVerify
fun <!VIPER_TEXT!>insertionSortProbeLoopBody<!>(head: @Unique Node?): @Unique Node? {
    postconditions<Node?> { r -> isSorted(r) }
    var sorted: @Unique Node? = null
    var cur: @Unique Node? = head
    while (cur != null) {
        loopInvariants { isSorted(sorted) }
        val next: @Unique Node? = cur.next
        cur.next = null
        sorted = insertSorted(sorted, cur)
        cur = next
        verify(<!VIPER_VERIFICATION_ERROR!>false<!>)
    }
    return sorted
}

@AlwaysVerify
fun <!VIPER_TEXT!>insertionSortProbeExit<!>(head: @Unique Node?): @Unique Node? {
    postconditions<Node?> { r -> isSorted(r) }
    var sorted: @Unique Node? = null
    var cur: @Unique Node? = head
    while (cur != null) {
        loopInvariants { isSorted(sorted) }
        val next: @Unique Node? = cur.next
        cur.next = null
        sorted = insertSorted(sorted, cur)
        cur = next
    }
    verify(<!VIPER_VERIFICATION_ERROR!>false<!>)
    return sorted
}
