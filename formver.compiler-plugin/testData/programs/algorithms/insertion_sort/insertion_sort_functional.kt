// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Node<!>(val value: Int, val next: Node?)

@Pure
fun <!MISSING_TERMINATION_MEASURE, VERIFICATION_SKIPPED, VIPER_TEXT!>isSorted<!>(n: Node?): Boolean =
    if (n == null) {
        true
    } else if (n.next == null) {
        true
    } else {
        n.value <= n.next.value && isSorted(n.next)
    }

@Pure
fun <!MISSING_TERMINATION_MEASURE, VERIFICATION_SKIPPED, VIPER_TEXT!>contents<!>(n: Node?): Multiset<Int> =
    if (n == null) multisetOf() else multisetOf(n.value) + contents(n.next)

fun <!VIPER_TEXT!>insert<!>(x: Int, l: Node?): Node {
    preconditions { isSorted(l) }
    postconditions<Node> { r ->
        isSorted(r) && (r.value == x || (l != null && r.value == l.value))
        contents(r) == contents(l) + multisetOf(x)
    }
    refute(false)
    if (l == null || x <= l.value) {
        <!VERIFICATION_CONDITION_NOT_ESTABLISHED, VERIFICATION_CONDITION_NOT_ESTABLISHED!>return Node(x, l)<!>
    }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED, VERIFICATION_CONDITION_NOT_ESTABLISHED!>return Node(l.value, <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>insert(x, l.next)<!>)<!>
}

fun <!VIPER_TEXT!>insertionSort<!>(l: Node?): Node? {
    postconditions<Node?> { r -> isSorted(r) && contents(r) == contents(l) }
    if (l == null) {
        <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return null<!>
    }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return insert(l.value, insertionSort(l.next))<!>
}
