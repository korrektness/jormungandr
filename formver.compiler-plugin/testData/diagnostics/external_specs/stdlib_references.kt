// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/AssociativeCollections.kt

import org.jetbrains.kotlin.formver.plugin.*

class Element(val number: Int)

fun <!VIPER_TEXT!>listSharedReferences<!>(first: Element, second: Element) {
    val list = mutableListOf<Element>()
    list.add(first)
    verify(list[0] == first)
    val alias = list[0]
    verify(alias.number == first.number)
    val previous = list.set(0, second)
    verify(previous == first && list[0] == second)
    verify(list.contains(second) && list.indexOf(second) == 0)
    val removed = list.remove(second)
    verify(removed && list.isEmpty())
}

fun <!VIPER_TEXT!>singletonSharedReference<!>(element: Element) {
    val list = listOf(element)
    verify(list[0] == element)
    verify(list[0].number == element.number)
}

fun <!VIPER_TEXT!>mapSharedReferences<!>(key: Element, value: Element) {
    val map = mutableMapOf<Element, Element>()
    map.put(key, value)
    verify(map[key] == value && map.containsKey(key) && map.containsValue(value))
    val removed = map.remove(key)
    verify(removed == value && map.isEmpty())
}

fun <!VIPER_TEXT!>setSharedReferences<!>(element: Element) {
    val set = mutableSetOf<Element>()
    set.add(element)
    val duplicate = set.add(element)
    verify(!duplicate && set.size == 1 && set.contains(element))
}

fun <!VIPER_TEXT!>wrongSharedReference<!>(first: Element, second: Element) {
    preconditions { first != second }
    val list = listOf(first)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>list[0] == second<!>)
}
