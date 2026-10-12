// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>requirePositive<!>(x: Int): Int {
    postconditions<Int> { it > 0 }
    if (x <= 0) throw IllegalArgumentException("not positive")
    return x
}

fun <!VIPER_TEXT!>throwFromElvis<!>(x: Int?): Int {
    postconditions<Int> { it == x }
    val y = x ?: throw IllegalStateException()
    return y
}

fun <!VIPER_TEXT!>falseAssertionAfterThrow<!>(x: Int) {
    if (x > 0) {
        throw IllegalArgumentException()
        verify(false)
    }
    verify(x <= 0)
}

fun <!VIPER_TEXT!>throwCaughtWithState<!>() {
    var x = 0
    try {
        x = 1
        throw IllegalArgumentException()
    } catch (e: IllegalArgumentException) {
        verify(x == 0 || x == 1)
    }
}

fun <!VIPER_TEXT!>throwCaughtSkipsState<!>() {
    var x = 0
    try {
        x = 1
        throw IllegalArgumentException()
    } catch (e: IllegalArgumentException) {
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x == 0<!>)
    }
}

fun <!VIPER_TEXT!>throwReachesOuterCatch<!>() {
    var x = 0
    try {
        try {
            x = 1
            throw IllegalStateException()
        } catch (e: IllegalArgumentException) {
            x = 2
        }
    } catch (e: IllegalStateException) {
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x != 1<!>)
    }
}

fun <!VIPER_TEXT!>rethrowFromCatch<!>(): Int {
    postconditions<Int> { it == 1 }
    try {
        return 1
    } catch (e: IllegalArgumentException) {
        throw e
    }
}

fun <!VERIFICATION_SKIPPED!>throwInsideTryWithFinally<!>() {
    <!UNSUPPORTED_FEATURE!>try {
        throw IllegalArgumentException()
    } finally {
        verify(false)
    }<!>
}
