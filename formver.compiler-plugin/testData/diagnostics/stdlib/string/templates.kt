// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

@AlwaysVerify
fun <!VIPER_TEXT!>templateInThrow<!>(x: Int, c: Char): Int {
    postconditions<Int> { it >= 0 }
    if (x < 0) throw IllegalArgumentException("bad $x at $c")
    return x
}

@AlwaysVerify
fun <!VIPER_TEXT!>templateOfStringsAndChars<!>(s: String, c: Char) {
    verify(
        "<$s>" == "<" + s + ">",
        "$c$c" == "" + c + c,
        "${s}x".length == s.length + 1,
        "$s$c"[s.length] == c,
    )
}

@AlwaysVerify
fun <!VIPER_TEXT!>templateOfLiterals<!>() {
    verify("a${'b'}${1}" == "ab1")
}
