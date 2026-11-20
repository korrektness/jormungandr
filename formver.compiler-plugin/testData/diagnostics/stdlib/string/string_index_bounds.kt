// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Strings.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>getNegative<!>(s: String, i: Int): Char {
    preconditions { i < s.length }
    return <!POSSIBLE_INDEX_OUT_OF_BOUND!>s[i]<!>
}

fun <!VIPER_TEXT!>getPastLength<!>(s: String, i: Int): Char {
    preconditions { i >= 0 }
    return <!POSSIBLE_INDEX_OUT_OF_BOUND!>s[i]<!>
}

fun <!VIPER_TEXT!>getGuarded<!>(s: String, i: Int): Char {
    preconditions { i >= 0 && i < s.length }
    return s[i]
}

fun <!VIPER_TEXT!>substringNegativeStart<!>(s: String, a: Int, b: Int): String {
    preconditions { a <= b && b <= s.length }
    return <!POSSIBLE_INDEX_OUT_OF_BOUND!>s.substring(a, b)<!>
}

fun <!VIPER_TEXT!>substringStartAfterEnd<!>(s: String, a: Int, b: Int): String {
    preconditions { a >= 0 && b <= s.length }
    return <!POSSIBLE_INDEX_OUT_OF_BOUND!>s.substring(a, b)<!>
}

fun <!VIPER_TEXT!>substringEndPastLength<!>(s: String, a: Int, b: Int): String {
    preconditions { a >= 0 && a <= b }
    return <!POSSIBLE_INDEX_OUT_OF_BOUND!>s.substring(a, b)<!>
}

fun <!VIPER_TEXT!>substringGuarded<!>(s: String, a: Int, b: Int): String {
    preconditions { a >= 0 && a <= b && b <= s.length }
    return s.substring(a, b)
}

fun <!VIPER_TEXT!>suffixNegative<!>(s: String, a: Int): String {
    preconditions { a <= s.length }
    return <!POSSIBLE_INDEX_OUT_OF_BOUND!>s.substring(a)<!>
}

fun <!VIPER_TEXT!>suffixPastLength<!>(s: String, a: Int): String {
    preconditions { a >= 0 }
    return <!POSSIBLE_INDEX_OUT_OF_BOUND!>s.substring(a)<!>
}

fun <!VIPER_TEXT!>suffixGuarded<!>(s: String, a: Int): String {
    preconditions { a >= 0 && a <= s.length }
    return s.substring(a)
}
