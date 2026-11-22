// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Strings.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>afterFirst<!>(s: String): String {
    val i = s.indexOf(',')
    return if (i != -1) s.substring(i + 1) else s
}

fun <!VIPER_TEXT!>beforeLast<!>(s: String): String {
    val i = s.lastIndexOf('/')
    return if (i >= 0) s.substring(0, i) else s
}

fun <!VIPER_TEXT!>notFoundIsMinusOne<!>(s: String) {
    val i = s.indexOf('a', ignoreCase = true)
    verify(i >= -1 && i < s.length)
    if (i < 0) verify(i == -1)
}

fun <!VIPER_TEXT!>secondField<!>(s: String): String {
    val i = s.indexOf(',')
    if (i == -1) return s
    val j = s.indexOf(',', i + 1)
    return if (j == -1) s.substring(i + 1) else s.substring(i + 1, j)
}

fun <!VIPER_TEXT!>lastBeforeStart<!>(s: String, start: Int) {
    val i = s.lastIndexOf('/', start)
    if (i != -1) verify(i <= start && i < s.length)
}

fun <!VIPER_TEXT!>afterSeparator<!>(s: String): String {
    val i = s.indexOf("::")
    return if (i != -1) s.substring(i + 2) else s
}

fun <!VIPER_TEXT!>lastSeparator<!>(s: String): String {
    val i = s.lastIndexOf("::")
    return if (i != -1) s.substring(i + 2) else s
}

fun <!VIPER_TEXT!>emptyNeedleFitsAtLength<!>(s: String, t: String): String {
    val i = s.indexOf(t)
    return if (i != -1) <!POSSIBLE_INDEX_OUT_OF_BOUND!>s.substring(<!INTEGER_OVERFLOW!>i + 1<!>)<!> else s
}

fun <!VIPER_TEXT!>emptyNeedleBeforeStart<!>(s: String, t: String, start: Int) {
    val i = s.indexOf(t, start)
    if (i != -1) verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>i >= start<!>)
}

fun <!VIPER_TEXT!>foundMayBeLast<!>(s: String): Char {
    val i = s.indexOf(',')
    return if (i != -1) <!POSSIBLE_INDEX_OUT_OF_BOUND!>s[i + 1]<!> else ' '
}
