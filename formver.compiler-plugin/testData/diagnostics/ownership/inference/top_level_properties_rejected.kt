// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Unique

class Counter(var n: Int)

fun make(): @Unique Counter = Counter(0)

fun consume(c: @Unique Counter) {}

val root = make()

fun `inferred top-level property passed to an owning parameter`() {
    consume(<!UNIQUENESS_MISMATCH!>root<!>)
}

fun `inferred top-level property copied into an annotated local`() {
    val c: @Unique Counter = <!UNIQUENESS_MISMATCH!>root<!>
}

fun keep(c: Counter) {}

val owned: @Unique Counter = make()

fun `annotated top-level property is moved`() {
    keep(owned)
    keep(<!INVALID_MOVED_ACCESS!>owned<!>)
}

object Paths {
    val root = make()
}

fun `inferred object property passed to an owning parameter`() {
    consume(<!UNIQUENESS_MISMATCH!>Paths.root<!>)
}

fun `inferred property of an anonymous object passed to an owning parameter`() {
    val holder = object {
        val item = make()
    }
    consume(<!UNIQUENESS_MISMATCH!>holder.item<!>)
}
