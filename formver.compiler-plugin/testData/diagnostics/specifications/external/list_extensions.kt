// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>lastIndexAfterMap<!>(xs: @Unique @Borrowed List<Int>) {
    val ys = xs.map { it + 0 }
    val last = ys.lastIndex
    ys.forEachIndexed { i, _ -> verify(i <= last) }
}

// Each extension borrows its receiver, so the receiver is still owned after the call.
fun <!VIPER_TEXT!>receiversStayOwned<!>(xs: @Unique @Borrowed List<Int>) {
    preconditions { xs.size == 1 }
    xs.lastIndex
    xs.indices
    xs.isNotEmpty()
    xs.count()
    xs.first()
    xs.last()
    xs.single()
    xs.elementAt(0)
    xs.firstOrNull()
    xs.lastOrNull()
    xs.singleOrNull()
    xs.getOrNull(0)
    xs.elementAtOrNull(0)
    verify(xs.size == 1)
}

fun <!VIPER_TEXT!>sizeFacts<!>(xs: @Unique @Borrowed List<Int>) {
    verify(xs.lastIndex == xs.size - 1)
    verify(xs.isNotEmpty() == (xs.size > 0))
    verify(xs.count() == xs.size)
}

fun <!VIPER_TEXT!>elementFacts<!>(xs: @Unique @Borrowed List<Int>) {
    preconditions { xs.size >= 2 }
    verify(xs.first() == xs[0])
    verify(xs.last() == xs[xs.size - 1])
    verify(xs.elementAt(1) == xs[1])
    verify(xs.firstOrNull() == xs[0])
    verify(xs.lastOrNull() == xs[xs.lastIndex])
    verify(xs.getOrNull(1) == xs[1])
    verify(xs.elementAtOrNull(1) == xs[1])
    verify(xs.singleOrNull() == null)
}

fun <!VIPER_TEXT!>outOfRange<!>(xs: @Unique @Borrowed List<Int>) {
    verify(xs.getOrNull(-1) == null)
    verify(xs.getOrNull(xs.size) == null)
    verify(xs.elementAtOrNull(xs.size) == null)
    if (xs.isEmpty()) {
        verify(xs.firstOrNull() == null && xs.lastOrNull() == null)
    }
}

fun <!VIPER_TEXT!>singleElement<!>() {
    val xs = listOf(7)
    verify(xs.single() == 7)
    verify(xs.singleOrNull() == 7)
}

fun <!VIPER_TEXT!>firstOfEmpty<!>(xs: @Unique @Borrowed List<Int>) {
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>xs.first()<!>
}

fun <!VIPER_TEXT!>singleOfTwo<!>(xs: @Unique @Borrowed List<Int>) {
    preconditions { xs.size == 2 }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>xs.single()<!>
}

// `orEmpty` returns its receiver, so it takes ownership of it.
fun <!VIPER_TEXT!>orEmptyMoves<!>(xs: @Unique List<Int>?): @Unique List<Int> = xs.orEmpty()

fun <!VIPER_TEXT!>orEmptyOfNull<!>(xs: @Unique List<Int>?) {
    preconditions { xs == null }
    verify(xs.orEmpty().size == 0)
}

// A shared receiver gets the opaque library call.
fun <!VIPER_TEXT!>sharedReceivers<!>(xs: List<Int>) {
    <!UNCHECKED_LIBRARY_CALL!>xs.lastIndex<!>
    <!UNCHECKED_LIBRARY_CALL!>xs.indices<!>
    <!UNCHECKED_LIBRARY_CALL!>xs.isNotEmpty()<!>
    <!UNCHECKED_LIBRARY_CALL!>xs.first()<!>
    <!UNCHECKED_LIBRARY_CALL!>xs.getOrNull(0)<!>
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!><!UNCHECKED_LIBRARY_CALL!>xs.lastIndex<!> >= -1<!>)
}

fun <!VIPER_TEXT!>sharedOrEmpty<!>(xs: List<Int>?): List<Int> = <!UNCHECKED_LIBRARY_CALL!>xs.orEmpty()<!>
