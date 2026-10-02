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
