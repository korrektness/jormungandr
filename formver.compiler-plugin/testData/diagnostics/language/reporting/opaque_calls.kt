// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

val TABLE: Array<String?> = arrayOfNulls(4)

fun <!VIPER_TEXT!>appendRange<!>(s: String): String {
    preconditions { s.length >= 2 }
    val sb = StringBuilder()
    <!OPAQUE_CALL!>sb.append(s, 0, 2)<!>
    return sb.toString()
}

fun <!VIPER_TEXT!>tableEntry<!>(): String? {
    val table = <!OPAQUE_CALL!>TABLE<!>
    return <!POSSIBLE_INDEX_OUT_OF_BOUND!>table[0]<!>
}

fun <!VIPER_TEXT!>firstQuote<!>(s: String): Int = <!OPAQUE_CALL!>s.indexOf('"')<!>

fun <!VIPER_TEXT!>codePoint<!>(c: Int): Boolean = <!OPAQUE_CALL!>Character.isValidCodePoint(c)<!>

data <!VIPER_TEXT!>class Point<!>(val x: Int, val y: Int)

fun <!VIPER_TEXT!>generatedMembers<!>(p: Point): Int {
    val q = p.copy(y = 0)
    return <!INTEGER_OVERFLOW!>p.component1() + q.component2()<!>
}
