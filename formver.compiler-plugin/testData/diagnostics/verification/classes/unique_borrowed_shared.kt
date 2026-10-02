// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Box(var value: Int)

fun <!VIPER_TEXT!>borrow<!>(a: @Borrowed Box) {}

fun <!VIPER_TEXT!>borrowNullable<!>(a: @Borrowed Box?) {}

fun <!VIPER_TEXT!>borrowUnique<!>(a: @Unique @Borrowed Box) {}

fun <!VIPER_TEXT!>passToBorrowed<!>(b: @Unique @Borrowed Box) {
    val x1 = b.value
    borrow(b)
    val x2 = b.value
    verify(<!VIPER_VERIFICATION_ERROR!>x1 == x2<!>)
}

fun <!VIPER_TEXT!>passToBorrowedNullable<!>(b: @Unique @Borrowed Box?) {
    borrowNullable(b)
}

fun <!VIPER_TEXT!>readAfterBorrowed<!>(b: @Unique @Borrowed Box) {
    borrow(b)
    val x = b.value
    verify(x == b.value)
}

fun <!VIPER_TEXT!>passToUniqueBorrowed<!>(b: @Unique @Borrowed Box) {
    borrowUnique(b)
    val x = b.value
    verify(x == b.value)
}

// The write through `b` is dropped, so `a.value == 1` holds in Viper whatever `b` is. That is
// sound only because the checker rejects a call that passes one object as both arguments.
fun <!VIPER_TEXT!>writeThroughBothParameters<!>(a: @Unique @Borrowed Box, b: Box) {
    a.value = 1
    b.value = 2
    verify(a.value == 1)
}

fun <!VIPER_TEXT!>passDistinctObjects<!>(u: @Unique Box, v: Box) {
    writeThroughBothParameters(u, v)
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>passOneObjectTwice<!>(u: @Unique Box) {
    writeThroughBothParameters(<!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>u<!>, <!INVALID_DUPLICATE_UNIQUE_ARGUMENT!>u<!>)
}
