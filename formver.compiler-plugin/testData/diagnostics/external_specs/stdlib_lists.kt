// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>listObservers<!>(list: @Unique @Borrowed List<Int>) {
    verify(list.size >= 0)
    verify(list.isEmpty() == (list.size == 0))
}

fun <!VIPER_TEXT!>listFactories<!>() {
    val empty = listOf<Int>()
    verify(empty.isEmpty())
    val single = listOf(7)
    verify(single.size == 1)
    verify(single[0] == 7)
    verify(single.contains(7))
    verify(single.indexOf(7) == 0)
}

fun <!VIPER_TEXT!>listMutations<!>() {
    val list = mutableListOf<Int>()
    verify(list.isEmpty())
    val added = list.add(3)
    verify(added)
    verify(list.size == 1)
    verify(list[0] == 3)
    list.add(5)
    verify(list[0] == 3 && list[1] == 5)
    val previous = list.set(0, 8)
    verify(previous == 3)
    verify(list[0] == 8 && list[1] == 5)
    val removed = list.remove(8)
    verify(removed)
    verify(list.size == 1 && list[0] == 5)
    val missing = list.remove(8)
    verify(!missing)
    verify(list.size == 1 && list[0] == 5)
}

fun <!VIPER_TEXT!>insertAndRemoveFirstOccurrence<!>() {
    val list = mutableListOf<Int>()
    list.add(4)
    list.add(9)
    list.add(0, 9)
    verify(list.size == 3 && list[0] == 9 && list[1] == 4 && list[2] == 9)
    verify(list.indexOf(9) == 0)
    val removed = list.remove(9)
    verify(removed && list.size == 2 && list[0] == 4 && list[1] == 9)
    verify(list.indexOf(9) == 1)
}

fun <!VIPER_TEXT!>wrongFirstOccurrence<!>() {
    val list = mutableListOf<Int>()
    list.add(9)
    list.add(9)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>list.indexOf(9) == 1<!>)
}

fun <!VIPER_TEXT!>setBounds<!>() {
    val list = mutableListOf<Int>()
    <!VIPER_VERIFICATION_ERROR!>list.set(0, 4)<!>
}

fun <!VIPER_TEXT!>insertBounds<!>() {
    val list = mutableListOf<Int>()
    <!VIPER_VERIFICATION_ERROR!>list.add(1, 4)<!>
}

fun <!VIPER_TEXT!>wrongListElement<!>() {
    val list = listOf(7)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>list[0] == 8<!>)
}

fun <!VIPER_TEXT!>listBounds<!>(list: @Unique @Borrowed List<Int>) {
    <!VIPER_VERIFICATION_ERROR!>list[list.size]<!>
}
