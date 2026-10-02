// FULL_JDK
// WITH_STDLIB
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

class A {
    var x: @Unique Any = Any()
}

fun nondet(): Boolean = false

fun consume(a: @Unique Any) {}

fun borrow(a: @Borrowed Any) {}

fun take(a: @Unique A) {}

fun `every use after one move is one error`(a: @Unique A) {
    consume(a)
    borrow(<!INVALID_MOVED_ACCESS!>a<!>)
    borrow(a)
    consume(a)
}

fun `every use of each moved path is one error`(a: @Unique A, b: @Unique A) {
    consume(a)
    consume(b)
    borrow(<!INVALID_MOVED_ACCESS!>a<!>)
    borrow(<!INVALID_MOVED_ACCESS!>b<!>)
    borrow(a)
    borrow(b)
}

fun `a move on either branch is one error naming both`(a: @Unique A) {
    if (nondet()) {
        consume(a)
    } else {
        take(a)
    }
    borrow(<!INVALID_MOVED_ACCESS!>a<!>)
    borrow(a)
}

fun `a later move is a new error`(a: @Unique A) {
    var c: @Unique A = a
    consume(c)
    borrow(<!INVALID_MOVED_ACCESS!>c<!>)
    c = A()
    consume(c)
    borrow(<!INVALID_MOVED_ACCESS!>c<!>)
    borrow(c)
}

fun `a move later in a loop reaches an earlier use`(a: @Unique A) {
    while (nondet()) {
        borrow(<!INVALID_MOVED_ACCESS!>a<!>)
        borrow(a)
        consume(a)
    }
}
