// FULL_JDK
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Numbers<!>(val values: List<Int>) {
    fun <!VIPER_TEXT!>all<!>(): List<Int> = values
}

fun <!VERIFICATION_SKIPPED!>sharedNullableMap<!>(xs: List<Int>?) {
    val ys = <!UNCHECKED_LIBRARY_CALL!>xs?.map <!UNSUPPORTED_FEATURE!>{ 1 }<!><!>
}

fun <!VERIFICATION_SKIPPED!>sharedCallResultFilter<!>(n: Numbers) {
    val ys = <!UNCHECKED_LIBRARY_CALL!>n.all().filter <!UNSUPPORTED_FEATURE!>{ it > 0 }<!><!>
}

fun <!VERIFICATION_SKIPPED!>sharedAny<!>(xs: List<Int>) {
    val found = xs.any <!UNSUPPORTED_FEATURE!>{ it > 0 }<!>
}

fun <!VIPER_TEXT!>uniqueAny<!>(xs: @Unique @Borrowed List<Int>) {
    preconditions { xs.size > 0 }
    val found = xs.any { true }
    verify(found)
}

fun <!VIPER_TEXT!>uniqueMap<!>(xs: @Unique @Borrowed List<Int>) {
    val ys: @Unique List<Int> = xs.map { 1 }
    verify(ys.size == xs.size)
}

fun <!VIPER_TEXT!>uniqueCallResultFilter<!>(xs: @Unique @Borrowed List<Int>) {
    val ys: @Unique List<Int> = xs.map { it }.filter { true }
    verify(ys.size == xs.size)
}
