// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

class Counter(var n: Int) {
    @Unique fun own() {}
}

class Slot(var item: @Unique Counter)

fun make(): @Unique Counter = Counter(0)

fun keep(c: Counter) {}

fun borrow(c: @Borrowed Counter) {}

fun makeSlot(): @Unique Slot = Slot(Counter(0))

fun consume(c: @Unique Counter) {}

fun `annotated local is moved by sharing`(): Int {
    val c: @Unique Counter = make()
    keep(c)
    return <!INVALID_MOVED_ACCESS!>c<!>.n
}

fun `annotated local assigned a shared value`(other: Counter) {
    var c: @Unique Counter = make()
    c = <!UNIQUENESS_MISMATCH!>other<!>
}

fun `shared inferred local passed to an owning parameter`() {
    val c = make()
    keep(c)
    consume(<!UNIQUENESS_MISMATCH!>c<!>)
}

fun `shared inferred local returned as unique`(): @Unique Counter {
    val c = make()
    keep(c)
    return <!UNIQUENESS_MISMATCH!>c<!>
}

fun `shared inferred local written to an annotated local`() {
    val c = make()
    keep(c)
    val d: @Unique Counter = <!UNIQUENESS_MISMATCH!>c<!>
}

fun `inferred local assigned a borrowed value passed to an owning parameter`(b: @Borrowed Counter) <!EXIT_UNIQUENESS_INCONSISTENCY!>{
    var c = make()
    c = <!LOCALITY_MISMATCH!>b<!>
    consume(<!UNIQUENESS_MISMATCH!>c<!>)
}<!>

fun `shared inferred local as an owned receiver`() {
    val c = make()
    keep(c)
    <!UNIQUENESS_MISMATCH!>c<!>.own()
}

fun `shared inferred local stored in a unique property`(s: @Unique Slot) {
    val c = make()
    keep(c)
    s.item = <!UNIQUENESS_MISMATCH!>c<!>
}

fun `property of an inferred local is moved by sharing`(): Int {
    val s = makeSlot()
    keep(s.item)
    return <!INVALID_MOVED_ACCESS!>s.item<!>.n
}

fun `borrowing an inferred local does not share it`() {
    val c = make()
    borrow(c)
    consume(c)
}

fun `inferred local copied into an inferred local passed to an owning parameter`() {
    val c = make()
    val d = c
    consume(<!UNIQUENESS_MISMATCH!>d<!>)
}

fun `inferred local copied into an annotated local is moved`(): Int {
    val c = make()
    val d: @Unique Counter = c
    return <!INVALID_MOVED_ACCESS!>c<!>.n
}

fun `borrowed inferred local passed to a shared parameter`(b: @Borrowed Counter): Int {
    val c = b
    keep(<!LOCALITY_MISMATCH!>c<!>)
    <!EXIT_UNIQUENESS_INCONSISTENCY!>return c.n<!>
}
