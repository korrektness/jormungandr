// FULL_JDK
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Numbers<!>(val values: List<Int>) {
    fun <!VIPER_TEXT!>all<!>(): List<Int> = values
}

fun <!VIPER_TEXT!>sharedNullableMap<!>(xs: List<Int>?) {
    val ys = <!UNCHECKED_LIBRARY_CALL!>xs?.map { 1 }<!>
}

fun <!VIPER_TEXT!>sharedCallResultFilter<!>(n: Numbers) {
    val ys = <!UNCHECKED_LIBRARY_CALL!>n.all().filter { it > 0 }<!>
}

fun <!VIPER_TEXT!>sharedAny<!>(xs: List<Int>) {
    val found = <!OPAQUE_CALL!>xs.any { it > 0 }<!>
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

fun <!VIPER_TEXT!>capturedMapResult<!>(xs: List<Int>): () -> Boolean {
    val mapped = <!UNCHECKED_LIBRARY_CALL!>xs.map { it }<!>
    return {
        val found = <!OPAQUE_CALL!>mapped.any { it > 0 }<!>
        refute(found)
        refute(!found)
        found
    }
}

fun <!VIPER_TEXT!>sharedMapThenLoop<!>(xs: Set<String>): Boolean {
    val ys = <!UNCHECKED_LIBRARY_CALL!>xs.map { it.length }<!>
    for (y in ys) { if (y == 0) return true }
    return false
}

fun <!VIPER_TEXT!>sharedFilterThenAny<!>(xs: List<String>): Boolean {
    val ys = <!UNCHECKED_LIBRARY_CALL!>xs.filter { it.isNotEmpty() }<!>
    return ys.any { it.length == 1 }
}

fun <!VIPER_TEXT!>sharedFilterForEach<!>(xs: List<String>) {
    <!UNCHECKED_LIBRARY_CALL!>xs.filter { it.isNotEmpty() }<!>.forEach { }
}

fun <!VIPER_TEXT!>sharedMapThenCheckedMap<!>(xs: List<Int>) {
    val ys = <!UNCHECKED_LIBRARY_CALL!>xs.map { it }<!>
    val zs = ys.map { 1 }
    verify(zs.size == ys.size)
}

fun <!VERIFICATION_SKIPPED!>sharedOrEmptyAliasesReceiver<!>(xs: List<String>?) {
    <!UNSUPPORTED_OWNERSHIP!><!UNCHECKED_LIBRARY_CALL!>xs.orEmpty()<!>.forEach { }<!>
}
