// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.*

class Node {
    var next: @Unique Node? = null
}

fun consume(n: @Unique Node) {}

fun store(a: Any) {}

fun `ghost builtins move nothing`(n: @Unique Node) {
    unfold(UniquePred(n))
    fold(UniquePred(n))
    verify(acc(n.next), old(n) == n)
    consume(n)
}

fun `ghost builtins accept a borrowed value`(n: @Unique @Borrowed Node) {
    unfold(UniquePred(n), write())
    fold(UniquePred(n), write())
}

fun `ghost builtins reject a moved value`(n: @Unique Node) {
    consume(n)
    unfold(UniquePred(<!INVALID_MOVED_ACCESS!>n<!>))
}

fun `a predicate constructed outside a specification would carry a borrowed value`(n: @Unique @Borrowed Node) {
    val p = <!INVALID_UNIQUE_PRED_PLACEMENT!>UniquePred(n)<!>
    store(p)
}
