// FULL_JDK
// WITH_STDLIB
// TRUST_REPORT
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/AssociativeCollections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Strings.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>listVarargs<!>() = <!OPAQUE_CALL!>listOf(1, 2)<!>
fun <!VIPER_TEXT!>mutableListVarargs<!>() = <!OPAQUE_CALL!>mutableListOf(1, 2)<!>
fun <!VIPER_TEXT!>listIterator<!>(list: List<Int>) { <!UNCHECKED_LIBRARY_CALL!>list.iterator()<!> }
fun <!VIPER_TEXT!>listSubList<!>(list: List<Int>) { <!UNCHECKED_LIBRARY_CALL!>list.subList(0, 0)<!> }
fun <!VIPER_TEXT!>mapEntries<!>(map: Map<Int, Int>) { <!OPAQUE_CALL!>map.entries<!> }
fun <!VIPER_TEXT!>stringUppercase<!>() = <!OPAQUE_CALL!>"text".uppercase()<!>

fun <!VIPER_TEXT!>keepsOnlyInvariants<!>(): Int {
    val list = mutableListOf<Int>()
    list.add(4)
    val total = <!OPAQUE_CALL!>list.sum()<!>
    val before = list.size
    verify(before >= 0)
    val removed = list.remove(total)
    verify(list.size == if (removed) before - 1 else before)
    return list.size
}

fun <!VIPER_TEXT!>reliesOnChangedState<!>() {
    val list = mutableListOf<Int>()
    list.add(4)
    <!OPAQUE_CALL!>list.sum()<!>
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>list.size == 1<!>)
}

fun <!VIPER_TEXT!>aliasingResult<!>(): Int {
    val list = mutableListOf<Int>()
    list.add(4)
    val view = <!OPAQUE_CALL!>list.asReversed()<!>
    <!OPAQUE_CALL!>view.clear()<!>
    return <!UNCHECKED_LIBRARY_CALL!>list.size<!>
}

fun <!VIPER_TEXT!>storingArgument<!>(): Int {
    val inner = mutableListOf<Int>()
    val outer = mutableListOf<MutableList<Int>>()
    <!OPAQUE_CALL!>outer.fill(inner)<!>
    return <!UNCHECKED_LIBRARY_CALL!>inner.size<!>
}

fun <!VIPER_TEXT!>arrayReceiver<!>(): Int {
    val numbers = IntArray(2)
    <!OPAQUE_CALL!>numbers.average()<!>
    return numbers.size
}

fun <!VIPER_TEXT!>readsStaticField<!>(): Boolean {
    val separator: String? = <!OPAQUE_CALL!>java.io.File.separator<!>
    return separator == null
}
