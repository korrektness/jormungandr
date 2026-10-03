// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Node(var value: Int, var next: @Unique Node?)

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>length<!>(n: @Unique Node?): Int = if (n == null) 0 else 1 + length(n.next)

@AlwaysVerify
fun <!VIPER_TEXT!>appendGhost<!>(l: @Unique Node?, v: Int, n: Int): @Unique Node {
    preconditions { length(l) == n }
    postconditions<Node> { r -> length(r) == n + 1 }
    if (l == null) {
        return Node(v, null)
    }
    l.next = appendGhost(l.next, v, n - 1)
    return l
}

@AlwaysVerify
fun <!VIPER_TEXT!>append<!>(l: @Unique Node?, v: Int): @Unique Node {
    postconditions<Node> { r -> length(r) == old(length(l)) + 1 }
    if (l == null) {
        return Node(v, null)
    }
    l.next = append(l.next, v)
    return l
}

@AlwaysVerify
fun <!VIPER_TEXT!>incAllRec<!>(l: @Unique Node?): @Unique Node? {
    postconditions<Node?> { r -> length(r) == old(length(l)) }
    if (l == null) {
        return null
    }
    l.value = l.value + 1
    l.next = incAllRec(l.next)
    return l
}

@AlwaysVerify
fun <!VIPER_TEXT!>prepend<!>(l: @Unique Node?, v: Int): @Unique Node {
    postconditions<Node> { r -> length(r) == old(length(l)) + 1 }
    return Node(v, l)
}

@AlwaysVerify
fun <!VIPER_TEXT!>twoNodes<!>(v: Int): @Unique Node {
    postconditions<Node> { r -> length(r) == 2 }
    return Node(v, Node(v, null))
}

// `m` joins the two branches of a condition on `n`: it is bound outside the `unfolding` that reads `n.next`, and
// carries no access invariants for the call.
@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>nextLength<!>(n: @Unique Node?): Int {
    val m = if (n == null) null else n.next
    return <!OWNERSHIP_NOT_ESTABLISHED!>length(m)<!>
}

<!VIPER_VERIFICATION_ERROR!>@AlwaysVerify
fun <!VIPER_TEXT!>tail<!>(l: @Unique Node): @Unique Node? {
    postconditions<Node?> { r -> length(r) == old(nextLength(l)) }
    return l.next
}<!>

class VNode(val value: Int, val next: @Unique VNode?)

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>vLength<!>(n: @Unique VNode?): Int = if (n == null) 0 else 1 + vLength(n.next)

@AlwaysVerify
fun <!VIPER_TEXT!>vTail<!>(l: @Unique VNode, n: Int): @Unique VNode? {
    preconditions { vLength(l) == n }
    postconditions<VNode?> { r -> vLength(r) == n - 1 }
    return l.next
}

// The `null` case of `n` reads `d`, so `valueOr` gets no postcondition for it.
@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>valueOr<!>(n: @Unique Node?, d: @Unique Node): Int = if (n == null) d.value else n.value

@AlwaysVerify
fun <!VIPER_TEXT!>oneNode<!>(v: Int): @Unique Node {
    postconditions<Node> { r -> length(r) == 1 }
    return Node(v, null)
}

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>sum<!>(n: @Unique Node?): Int {
    if (n == null) return 0
    return n.value + sum(n.next)
}

@AlwaysVerify
fun <!VIPER_TEXT!>oneSum<!>(v: Int): @Unique Node {
    postconditions<Node> { r -> sum(r) == v }
    return Node(v, null)
}

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>lengthFrom<!>(k: Int, n: @Unique Node?): Int = if (n == null) k else lengthFrom(k + 1, n.next)

@AlwaysVerify
fun <!VIPER_TEXT!>oneFrom<!>(v: Int): @Unique Node {
    postconditions<Node> { r -> lengthFrom(0, r) == 1 }
    return Node(v, null)
}

// The `null` case of `a` calls `length(b)`, which reads the heap, so `lengthOf` gets no postcondition for `a`.
@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>lengthOf<!>(a: @Unique Node?, b: @Unique Node?): Int = if (a == null) length(b) else length(a)

// The precondition excludes the `null` case of `n`.
@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>headValue<!>(n: @Unique Node?): Int {
    preconditions { n != null }
    return if (n == null) -1 else n.value
}

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>lengthWhen<!>(n: @Unique Node?): Int = when (n) {
    null -> 0
    else -> 1 + lengthWhen(n.next)
}

@AlwaysVerify
fun <!VIPER_TEXT!>oneNodeWhen<!>(v: Int): @Unique Node {
    postconditions<Node> { r -> lengthWhen(r) == 1 }
    return Node(v, null)
}
