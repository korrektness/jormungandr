// FULL_JDK
// WITH_STDLIB

import org.jetbrains.kotlin.formver.plugin.*


fun <!VIPER_TEXT!>empty_list_expr_get<!>() {
    val s = <!VIPER_VERIFICATION_ERROR!>emptyList<Int>()[0]<!>
}

fun <!VIPER_TEXT!>empty_list_get<!>() {
    val myList: List<Int> = emptyList()
    val s = <!UNCHECKED_LIBRARY_CALL!>myList[0]<!>
}

fun <!VIPER_TEXT!>unsafe_last<!>(l: @Unique @Borrowed List<Int>) : Int {
    return <!VIPER_VERIFICATION_ERROR!>l[l.size - 1]<!>
}

fun <!VIPER_TEXT!>add_get<!>(l: @Unique @Borrowed MutableList<Int>) {
    preconditions { l.size < 2147483647 }
    l.add(1)
    val n = <!VIPER_VERIFICATION_ERROR!>l[1]<!>
}

fun <!VERIFICATION_SKIPPED!>empty_list_sub<!>() {
    val l = <!UNSUPPORTED_FEATURE!>emptyList<Int>().subList(0, 1)<!>
}

fun <!VERIFICATION_SKIPPED!>empty_list_sub_negative<!>() {
    val l = <!UNSUPPORTED_FEATURE!>emptyList<Int>().subList(-1, 1)<!>
}
