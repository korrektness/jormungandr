// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>count<!>(vararg xs: Int): Int {
    postconditions<Int> { it == xs.size }
    return xs.size
}

fun <!VIPER_TEXT!>wrongCount<!>() {
    val n = count(1, 2, 3)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>n == 2<!>)
}

fun <!VIPER_TEXT!>sharedSpreadSize<!>(a: IntArray) {
    val n = count(1, *a)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>n == 1<!>)
}

fun <!VIPER_TEXT!>overflowingElement<!>(x: Int) {
    count(1, <!INTEGER_OVERFLOW!>x + 1<!>)
}
