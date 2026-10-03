// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

@AlwaysVerify
fun <!VIPER_TEXT!>stringIsEmpty<!>(s: String): Boolean = s.isEmpty()

@AlwaysVerify
fun <!VIPER_TEXT!>stringIsNotEmpty<!>(s: String): Boolean = s.isNotEmpty()

@AlwaysVerify
fun <!VIPER_TEXT!>lastIndexBound<!>(s: String): Char {
    preconditions { s.length > 0 }
    return s[s.lastIndex]
}

@AlwaysVerify
fun <!VIPER_TEXT!>repeatLength<!>(s: String, n: Int): String {
    preconditions { n >= 0 }
    postconditions<String> { r -> r.length == s.length * n }
    return s.repeat(n)
}

fun <!VIPER_TEXT!>takesCharSequence<!>(cs: CharSequence) {}

@AlwaysVerify
fun <!VIPER_TEXT!>passStringAsCharSequence<!>(s: String) {
    takesCharSequence(s)
}
