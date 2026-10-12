// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>stringIsEmpty<!>(s: String): Boolean = s.isEmpty()

fun <!VIPER_TEXT!>stringIsNotEmpty<!>(s: String): Boolean = s.isNotEmpty()

fun <!VIPER_TEXT!>lastIndexBound<!>(s: String): Char {
    preconditions { s.length > 0 }
    return s[s.lastIndex]
}

fun <!VIPER_TEXT!>repeatLength<!>(s: String, n: Int): String {
    preconditions { n >= 0 }
    postconditions<String> { r -> r.length == s.length * n }
    return s.repeat(n)
}

fun <!VIPER_TEXT!>takesCharSequence<!>(cs: CharSequence) {}

fun <!VIPER_TEXT!>passStringAsCharSequence<!>(s: String) {
    takesCharSequence(s)
}
