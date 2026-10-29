// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>sharedSize<!>(xs: List<Int>): Int = <!UNCHECKED_LIBRARY_CALL!>xs.size<!>

fun <!VIPER_TEXT!>sharedIsEmpty<!>(xs: List<Int>): Boolean = <!UNCHECKED_LIBRARY_CALL!>xs.isEmpty()<!>

fun <!VIPER_TEXT!>sharedIndex<!>(xs: List<Int>, i: Int): Int = <!UNCHECKED_LIBRARY_CALL!>xs[i]<!>

fun <!VIPER_TEXT!>sharedGet<!>(xs: List<Int>, i: Int): Int = <!UNCHECKED_LIBRARY_CALL!>xs.get(i)<!>

fun <!VIPER_TEXT!>sharedNullableSize<!>(xs: List<Int>?): Int? = <!UNCHECKED_LIBRARY_CALL!>xs?.size<!>

fun <!VIPER_TEXT!>sharedAdd<!>(xs: MutableList<Int>) {
    <!UNCHECKED_LIBRARY_CALL!>xs.add(1)<!>
}

fun <!VIPER_TEXT!>sharedGetCaught<!>(xs: List<Int>, i: Int): Int {
    var result = 0
    try {
        result = <!UNCHECKED_LIBRARY_CALL!>xs[i]<!>
    } catch (e: IndexOutOfBoundsException) {
        result = -1
    }
    return result
}

// A shared receiver gets no facts from the specification.
fun <!VIPER_TEXT!>sharedSizeUnknown<!>(xs: List<Int>) {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!UNCHECKED_LIBRARY_CALL!>xs.size<!> >= 0<!>)
}

fun <!VIPER_TEXT!>uniqueMembers<!>(xs: @Unique @Borrowed List<Int>) {
    preconditions { xs.size > 1 }
    verify(xs.size >= 0)
    verify(!xs.isEmpty())
    verify(xs[1] == xs.get(1))
}
