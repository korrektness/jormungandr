// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>templateInThrow<!>(x: Int, c: Char): Int {
    postconditions<Int> { it >= 0 }
    if (x < 0) throw IllegalArgumentException("bad $x at $c")
    return x
}

fun <!VIPER_TEXT!>templateOfStringsAndChars<!>(s: String, c: Char) {
    preconditions { s.length <= 2147483645 }
    verify(
        "<$s>" == "<" + s + ">",
        "$c$c" == "" + c + c,
        "${s}x".length == s.length + 1,
        "$s$c"[s.length] == c,
    )
}

fun <!VIPER_TEXT!>templateOfLiterals<!>() {
    verify("a${'b'}${1}" == "ab1")
}
