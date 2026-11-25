// FULL_JDK
// WITH_STDLIB
// UNIQUE_CHECK_ONLY
// LANGUAGE: +ContextParameters
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt

import org.jetbrains.kotlin.formver.plugin.*

private fun takesList(values: @Unique List<Int>) {}
private fun (@Unique List<Int>).consume() {}
private fun borrowList(values: @Borrowed List<Int>) {}

context(values: @Unique List<Int>)
private fun takesListContext() {}

class OwnedLists(xs: List<Int>?) {
    var values: @Unique List<Int> = <!UNIQUENESS_MISMATCH!>xs.orEmpty()<!>

    @Unique @Borrowed
    fun replace(xs: List<Int>?) {
        values = <!UNIQUENESS_MISMATCH!>xs.orEmpty()<!>
    }
}

fun argument(xs: List<Int>?) {
    takesList(<!UNIQUENESS_MISMATCH!>xs.orEmpty()<!>)
}

fun receiver(xs: List<Int>?) {
    <!UNIQUENESS_MISMATCH!>xs.orEmpty().orEmpty()<!>.consume()
}

fun initializer(xs: List<Int>?) {
    val values: @Unique List<Int> = <!UNIQUENESS_MISMATCH!>xs.orEmpty()<!>
}

fun reassignedLocal(xs: List<Int>?) {
    var values = listOf(7)
    values = xs.orEmpty()
    takesList(<!UNIQUENESS_MISMATCH!>values<!>)
}

fun borrowedResult(xs: List<Int>?) {
    val values = xs.orEmpty()
    borrowList(values)
    takesList(<!UNIQUENESS_MISMATCH!>values<!>)
}

fun contextArgument(xs: List<Int>?) {
    val values = xs.orEmpty()
    context(values) { <!CONTEXT_UNIQUENESS_MISMATCH!>takesListContext()<!> }
}

fun joinedResult(xs: List<Int>?, owned: @Unique List<Int>?, flag: Boolean): @Unique List<Int> =
    <!UNIQUENESS_MISMATCH!>if (flag) owned.orEmpty() else xs.orEmpty()<!>

fun assertedResult(xs: List<Int>?, flag: Boolean): @Unique List<Int> =
    <!UNIQUENESS_MISMATCH!>(if (flag) null else xs.orEmpty())!!<!>

fun borrowOnlyReceiver(xs: @Borrowed List<Int>?): @Unique List<Int> =
    <!EXIT_UNIQUENESS_INCONSISTENCY, UNIQUENESS_MISMATCH!><!LOCALITY_MISMATCH!>xs<!>.orEmpty()<!>
