// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Node(val value: Int, val next: Node?)

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>isSorted<!>(n: Node?): Boolean =
    if (n == null) {
        true
    } else if (n.next == null) {
        true
    } else {
        n.value <= n.next.value && isSorted(n.next)
    }

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>contents<!>(n: Node?): Multiset<Int> =
    if (n == null) multisetOf() else multisetOf(n.value) + contents(n.next)

@AlwaysVerify
fun <!VIPER_TEXT!>insert<!>(x: Int, l: Node?): Node {
    preconditions { isSorted(l) }
    postconditions<Node> { r ->
        isSorted(r) && (r.value == x || (l != null && r.value == l.value))
        contents(r) == contents(l) + multisetOf(x)
    }
    refute(false)
    if (l == null || x <= l.value) {
        return Node(x, l)
    }
    return Node(l.value, insert(x, l.next))
}

@AlwaysVerify
fun <!VIPER_TEXT!>insertionSort<!>(l: Node?): Node? {
    postconditions<Node?> { r -> isSorted(r) && contents(r) == contents(l) }
    if (l == null) {
        return null
    }
    return insert(l.value, insertionSort(l.next))
}
