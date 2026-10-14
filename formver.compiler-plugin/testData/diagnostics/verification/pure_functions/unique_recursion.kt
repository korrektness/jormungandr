// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Node<!>(var value: Int, var next: @Unique Node?)

// A list has no bound on its length, so the addition in `length` is an overflow-negative test and callers see no
// definition of `length`.
@Pure
fun <!VIPER_TEXT!>length<!>(n: @Unique Node?): Int {
    preconditions { decreases(n) }
    return if (n == null) 0 else <!INTEGER_OVERFLOW, INTEGER_OVERFLOW, INTEGER_OVERFLOW!>1 + length(n.next)<!>
}

// `capLength` saturates, so `cappedLength` stays within Int on any list.
@Pure
fun <!VIPER_TEXT!>capLength<!>(k: Int): Int = if (k > 1000000) 1000000 else k

@Pure
fun <!VIPER_TEXT!>cappedLength<!>(n: @Unique Node?): Int {
    preconditions { decreases(n) }
    postconditions<Int> { r -> 0 <= r && r <= 1000000 }
    return if (n == null) 0 else capLength(1 + cappedLength(n.next))
}

fun <!VIPER_TEXT!>appendGhost<!>(l: @Unique Node?, v: Int, n: Int): @Unique Node {
    preconditions { cappedLength(l) == n && n < 1000000 }
    postconditions<Node> { r -> cappedLength(r) == n + 1 }
    if (l == null) {
        return Node(v, null)
    }
    l.next = appendGhost(l.next, v, n - 1)
    return l
}

fun <!VIPER_TEXT!>append<!>(l: @Unique Node?, v: Int): @Unique Node {
    postconditions<Node> { r -> cappedLength(r) == capLength(old(cappedLength(l)) + 1) }
    if (l == null) {
        return Node(v, null)
    }
    l.next = append(l.next, v)
    return l
}

// Unrestricted node values leave per-node increments unrepresentable at Int.MAX.
fun <!VIPER_TEXT!>incAllRec<!>(l: @Unique Node?): @Unique Node? {
    postconditions<Node?> { r -> cappedLength(r) == old(cappedLength(l)) }
    if (l == null) {
        return null
    }
    l.value = <!INTEGER_OVERFLOW!>l.value + 1<!>
    l.next = incAllRec(l.next)
    return l
}

fun <!VIPER_TEXT!>prepend<!>(l: @Unique Node?, v: Int): @Unique Node {
    postconditions<Node> { r -> cappedLength(r) == capLength(old(cappedLength(l)) + 1) }
    return Node(v, l)
}

// A prefix observer bounds its result without assuming a bound on the whole list.
@Pure
fun <!VIPER_TEXT!>prefixLength<!>(n: @Unique Node?, budget: Int): Int {
    preconditions { decreases(budget); budget >= 0 }
    postconditions<Int> { r -> 0 <= r && r <= budget }
    if (n == null || budget == 0) return 0
    return 1 + prefixLength(n.next, budget - 1)
}

fun <!VIPER_TEXT!>twoNodes<!>(v: Int): @Unique Node {
    postconditions<Node> { r -> prefixLength(r, 2) == 2 }
    return Node(v, Node(v, null))
}

// `m` joins the two branches of a condition on `n`: it is bound outside the `unfolding` that reads `n.next`, and
// carries no access invariants for the call.
@Pure
fun <!VIPER_TEXT!>nextLength<!>(n: @Unique Node?): Int {
    val m = if (n == null) null else n.next
    return <!OWNERSHIP_NOT_ESTABLISHED!>length(m)<!>
}

fun <!VIPER_TEXT!>tail<!>(l: @Unique Node): @Unique Node? {
    postconditions<Node?> { r -> length(r) == old(nextLength(l)) }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return l.next<!>
}

<!VIPER_TEXT!>class VNode<!>(val value: Int, val next: @Unique VNode?)

@Pure
fun <!VIPER_TEXT!>vLength<!>(n: @Unique VNode?): Int {
    preconditions { decreases(n) }
    return if (n == null) 0 else <!INTEGER_OVERFLOW!>1 + vLength(n.next)<!>
}

fun <!VIPER_TEXT!>vTail<!>(l: @Unique VNode, n: Int): @Unique VNode? {
    preconditions { n > 0 && prefixVLength(l, n) == n }
    postconditions<VNode?> { r -> prefixVLength(r, n - 1) == n - 1 }
    return l.next
}

@Pure
fun <!VIPER_TEXT!>prefixVLength<!>(n: @Unique VNode?, budget: Int): Int {
    preconditions { decreases(budget); budget >= 0 }
    postconditions<Int> { r -> 0 <= r && r <= budget }
    if (n == null || budget == 0) return 0
    return 1 + prefixVLength(n.next, budget - 1)
}

// The `null` case of `n` reads `d`, so `valueOr` gets no postcondition for it.
@Pure
fun <!VIPER_TEXT!>valueOr<!>(n: @Unique Node?, d: @Unique Node): Int = if (n == null) d.value else n.value

fun <!VIPER_TEXT!>oneNode<!>(v: Int): @Unique Node {
    postconditions<Node> { r -> prefixLength(r, 1) == 1 }
    return Node(v, null)
}

@Pure
fun <!VIPER_TEXT!>sum<!>(n: @Unique Node?): Int {
    preconditions { decreases(n) }
    if (n == null) return 0
    return <!INTEGER_OVERFLOW!>n.value + sum(n.next)<!>
}

@Pure
fun <!VIPER_TEXT!>leafSum<!>(n: @Unique Node?): Int {
    preconditions { n == null || n.next == null }
    return if (n == null) 0 else n.value
}

fun <!VIPER_TEXT!>oneSum<!>(v: Int): @Unique Node {
    postconditions<Node> { r -> r.next == null; leafSum(r) == v }
    val node: @Unique Node = Node(v, null)
    node.next = null
    return node
}

@Pure
fun <!VIPER_TEXT!>lengthFrom<!>(k: Int, n: @Unique Node?): Int {
    preconditions { decreases(n) }
    return if (n == null) k else lengthFrom(<!INTEGER_OVERFLOW!>k + 1<!>, n.next)
}

@Pure
fun <!VIPER_TEXT!>prefixLengthFrom<!>(k: Int, n: @Unique Node?, budget: Int): Int {
    preconditions { decreases(budget); budget >= 0 && k <= 2147483647 - budget }
    postconditions<Int> { r -> k <= r && r <= k + budget }
    if (n == null || budget == 0) return k
    return prefixLengthFrom(k + 1, n.next, budget - 1)
}

fun <!VIPER_TEXT!>oneFrom<!>(v: Int): @Unique Node {
    postconditions<Node> { r -> prefixLengthFrom(0, r, 1) == 1 }
    return Node(v, null)
}

// The `null` case of `a` calls `length(b)`, which reads the heap, so `lengthOf` gets no postcondition for `a`.
@Pure
fun <!VIPER_TEXT!>lengthOf<!>(a: @Unique Node?, b: @Unique Node?): Int = if (a == null) length(b) else length(a)

// The precondition excludes the `null` case of `n`.
@Pure
fun <!VIPER_TEXT!>headValue<!>(n: @Unique Node?): Int {
    preconditions { n != null }
    return if (n == null) -1 else n.value
}

@Pure
fun <!VIPER_TEXT!>lengthWhen<!>(n: @Unique Node?): Int {
    preconditions { decreases(n) }
    return when (n) {
        null -> 0
        else -> <!INTEGER_OVERFLOW!>1 + lengthWhen(n.next)<!>
    }
}

fun <!VIPER_TEXT!>oneNodeWhen<!>(v: Int): @Unique Node {
    postconditions<Node> { r -> prefixLengthWhen(r, 1) == 1 }
    return Node(v, null)
}

@Pure
fun <!VIPER_TEXT!>prefixLengthWhen<!>(n: @Unique Node?, budget: Int): Int {
    preconditions { decreases(budget); budget >= 0 }
    postconditions<Int> { r -> 0 <= r && r <= budget }
    return when (n) {
        null -> 0
        else -> if (budget == 0) 0 else 1 + prefixLengthWhen(n.next, budget - 1)
    }
}

@Pure
fun <!VIPER_TEXT!>lengthPlus<!>(n: @Unique Node?, k: Int): Int {
    preconditions { decreases(n); k < 2147483647 }
    return if (n == null) k + 1 else <!INTEGER_OVERFLOW!>1 + lengthPlus(n.next, k)<!>
}

@Pure
fun <!VIPER_TEXT!>prefixLengthPlus<!>(n: @Unique Node?, k: Int, budget: Int): Int {
    preconditions { budget >= 0 && k <= 2147483646 - budget }
    return k + 1 + prefixLength(n, budget)
}

fun <!VIPER_TEXT!>oneNodePlus<!>(v: Int, k: Int): @Unique Node {
    preconditions { k <= 2147483645 }
    postconditions<Node> { r -> prefixLengthPlus(r, k, 1) == k + 2 }
    return Node(v, null)
}

@Pure
fun <!VIPER_TEXT!>sumFrom<!>(n: @Unique Node?, k: Int): Int {
    preconditions { decreases(n); -1073741824 <= k && k <= 1073741823 }
    val base = k * 2
    if (n == null) return base
    return <!INTEGER_OVERFLOW!>n.value + sumFrom(n.next, k)<!>
}

@Pure
fun <!VIPER_TEXT!>leafSumFrom<!>(n: @Unique Node?, k: Int): Int {
    preconditions {
        -1073741824 <= k && k <= 1073741823
        n == null || n.next == null
        n == null || (-2147483648L <= n.value.toLong() + 2L * k.toLong() &&
            n.value.toLong() + 2L * k.toLong() <= 2147483647L)
    }
    val base = k * 2
    return if (n == null) base else n.value + base
}

fun <!VIPER_TEXT!>oneNodeSum<!>(v: Int): @Unique Node {
    preconditions { v <= 2147483641 }
    postconditions<Node> { r -> r.next == null; r.value == v; leafSumFrom(r, 3) == v + 6 }
    val node: @Unique Node = Node(v, null)
    node.next = null
    return node
}

fun <!VIPER_TEXT!>prependUnrolled<!>(l: @Unique Node?, v: Int): @Unique Node {
    postconditions<Node> { r -> sum(r) == <!INTEGER_OVERFLOW!>r.value + sum(r.next)<!> }
    return Node(v, l)
}

fun <!VIPER_TEXT!>sumPlusValue<!>(n: @Unique @Borrowed Node): Int {
    preconditions { -2147483648L <= sum(n).toLong() + n.value.toLong() && sum(n).toLong() + n.value.toLong() <= 2147483647L }
    postconditions<Int> { r ->
        r == old(sum(n)) + old(n.value)
        r - old(n.value) == if (old(n.value) > 0) old(sum(n)) else old(sum(n))
    }
    return sum(n) + n.value
}

@Pure
fun <!VIPER_TEXT!>sumAndValue<!>(n: @Unique Node): Int {
    preconditions { -2147483648L <= sum(n).toLong() + n.value.toLong() && sum(n).toLong() + n.value.toLong() <= 2147483647L }
    return sum(n) + n.value
}

@Pure
fun <!VIPER_TEXT!>valueThenSum<!>(n: @Unique Node): Int {
    preconditions { -2147483648L <= sum(n).toLong() + n.value.toLong() && sum(n).toLong() + n.value.toLong() <= 2147483647L }
    val v = n.value
    return v + sum(n)
}
