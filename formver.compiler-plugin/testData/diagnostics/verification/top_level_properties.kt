// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

const val PREFIX = 'u'
const val LEN = 4
val NON_CONST = 4

@AlwaysVerify
fun <!VIPER_TEXT!>constChar<!>(c: Char): Boolean = c == PREFIX

@AlwaysVerify
fun <!VIPER_TEXT!>constInt<!>(): Int {
    postconditions<Int> { r -> r == 4 }
    return LEN
}

@AlwaysVerify
fun <!VIPER_TEXT!>constInWhen<!>(c: Char): Int = when (c) {
    PREFIX -> 1
    else -> 0
}

@AlwaysVerify
fun <!VIPER_TEXT!>nonConst<!>(): Int = NON_CONST
