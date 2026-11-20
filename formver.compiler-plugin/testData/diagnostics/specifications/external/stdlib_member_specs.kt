// FULL_JDK
// TRUST_REPORT
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/AssociativeCollections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Strings.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>mapSet<!>() {
    val map = mutableMapOf<Int, Int>()
    map[2] = 4
    verify(map.size == 1 && map.containsKey(2) && map[2] == 4)
    map[2] = 6
    verify(map.size == 1 && map[2] == 6)
}

fun <!VIPER_TEXT!>joinKeepsList<!>(): Int {
    val list = mutableListOf<Int>()
    list.add(3)
    val text = list.joinToString()
    verify(list.size == 1 && list[0] == 3)
    return text.length
}

fun <!VIPER_TEXT!>prefixes<!>(s: String) {
    verify(s.startsWith(s, ignoreCase = false))
    if (s.startsWith("ab", ignoreCase = true)) verify(s.length >= 2)
    verify(!"a".startsWith("ab"))
}

fun <!VIPER_TEXT!>wrongPrefix<!>(s: String) {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>s.startsWith("a", ignoreCase = true)<!>)
}

fun <!VIPER_TEXT!>stringEquality<!>(a: String?, b: String?) {
    verify(a.equals(a, ignoreCase = true))
    if (a.equals(b, ignoreCase = false)) verify(a == b)
    if (a.equals(b, ignoreCase = true) && a == null) verify(b == null)
}

fun <!VIPER_TEXT!>caseInsensitiveIsNotEquality<!>(a: String, b: String) {
    if (a.equals(b, ignoreCase = true)) verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>a == b<!>)
}

fun <!VIPER_TEXT!>copies<!>() {
    val list = mutableListOf<Int>()
    list.add(5)
    val copy = list.toList()
    verify(copy.size == 1 && copy[0] == 5)
}

fun <!VIPER_TEXT!>appended<!>() {
    val list = listOf(1)
    val longer = list + 2
    verify(longer.size == 2 && longer[0] == 1 && longer[1] == 2)
    val joined = longer + listOf(3)
    verify(joined.size >= 2 && joined[1] == 2)
}

fun <!VIPER_TEXT!>iterates<!>() {
    val list = listOf(7)
    val iterator = list.iterator()
    verify(iterator.hasNext())
    val first = iterator.next()
    verify(first == 7)
    verify(!iterator.hasNext())
}

fun <!VIPER_TEXT!>iteratesTooFar<!>() {
    val iterator = listOf(7).iterator()
    iterator.next()
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>iterator.next()<!>
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>iteratesMutableList<!>(): Int {
    val list = mutableListOf<Int>()
    list.add(7)
    val iterator = <!OPAQUE_CALL!>list.iterator()<!>
    <!INVALID_MOVED_ACCESS!>list<!>.add(8)
    return <!UNCHECKED_LIBRARY_CALL!>iterator.next()<!>
}

fun <!VIPER_TEXT!>finds<!>() {
    val list = mutableListOf<Int>()
    list.add(4)
    list.add(9)
    val large = list.find { it > 5 }
    verify(large == 9)
    val huge = list.find { it > 10 }
    verify(huge == null)
}

fun <!VIPER_TEXT!>parses<!>(s: String): Int {
    val n = s.toInt()
    verify(s.length > 0)
    return n
}

fun <!VIPER_TEXT!>mappedValues<!>() {
    val map = mutableMapOf<Int, Int>()
    map[1] = 2
    val doubled = map.mapValues { <!INTEGER_OVERFLOW!>it.value * 2<!> }
    verify(doubled.size == 1)
}

fun <!VIPER_TEXT!>sliced<!>() {
    val list = listOf(1)
    list.subList(0, 1)
    verify(list.size == 1)
}

fun <!VIPER_TEXT!>slicedTooFar<!>() {
    val list = listOf(1)
    <!VIPER_VERIFICATION_ERROR!>list.subList(0, 2)<!>
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>slicedMutable<!>(): Int {
    val list = mutableListOf<Int>()
    list.add(1)
    <!OPAQUE_CALL!>list.subList(0, 1)<!>
    return <!INVALID_MOVED_ACCESS!>list<!>.size
}
