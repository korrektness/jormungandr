// FULL_JDK
// WITH_STDLIB
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

class A {
    var x: @Unique Any = Any()
}

class B {
    var y: @Unique A = A()
}

fun nondet(): Boolean = false

fun consume(a: @Unique Any) {}

fun borrow(a: @Borrowed Any) {}

inline fun runWhileNondet(block: () -> Unit) {
    while (nondet()) block()
}

fun `consume in run then consume after`(b: @Unique B) {
    run { consume(b) }
    consume(<!INVALID_MOVED_ACCESS!>b<!>)
}

fun `consume before run then borrow in run`(b: @Unique B) {
    consume(b)
    run { borrow(<!INVALID_MOVED_ACCESS!>b<!>) }
}

fun `consume in lambda run in a loop`(a: @Unique A) {
    runWhileNondet { consume(<!INVALID_MOVED_ACCESS!>a<!>) }
}

fun `consume in repeat`(a: @Unique A) {
    repeat(2) { consume(<!INVALID_MOVED_ACCESS!>a<!>) }
}

fun `borrow in run then consume after`(b: @Unique B) {
    run {
        borrow(b)
        borrow(b.y)
    }
    consume(b)
}

fun `borrow in lambda run in a loop then consume after`(a: @Unique A) {
    runWhileNondet { borrow(a.x) }
    repeat(2) { borrow(a) }
    consume(a)
}
