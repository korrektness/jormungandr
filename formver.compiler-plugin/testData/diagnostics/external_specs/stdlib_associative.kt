// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/AssociativeCollections.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>setBasics<!>() {
    val empty = setOf<Int>()
    verify(empty.isEmpty() && !empty.contains(4))
    val single = setOf(4)
    verify(single.size == 1 && single.contains(4))
    val set = mutableSetOf<Int>()
    val added = set.add(4)
    verify(added && set.contains(4) && set.size == 1)
    val duplicate = set.add(4)
    verify(!duplicate && set.size == 1)
    val removed = set.remove(4)
    verify(removed && set.isEmpty() && !set.contains(4))
    val missing = set.remove(4)
    verify(!missing && set.isEmpty())
}

fun <!VIPER_TEXT!>mapBasics<!>() {
    val empty = mapOf<Int, Int>()
    verify(empty.isEmpty() && empty[2] == null && !empty.containsValue(4))
    val map = mutableMapOf<Int, Int>()
    val previous = map.put(2, 4)
    verify(previous == null)
    verify(map.size == 1 && map.containsKey(2) && map[2] == 4)
    verify(map.containsValue(4))
    val replaced = map.put(2, 6)
    verify(replaced == 4 && map.size == 1 && map[2] == 6)
    val removed = map.remove(2)
    verify(removed == 6 && map.isEmpty() && map[2] == null)
}

fun <!VIPER_TEXT!>nullIsMappedValue<!>() {
    val map = mutableMapOf<Int, Int?>()
    map.put(2, null)
    verify(map.containsKey(2) && map[2] == null && map.size == 1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>!map.containsKey(2)<!>)
}

fun <!VIPER_TEXT!>duplicateSetDoesNotGrow<!>() {
    val set = mutableSetOf<Int>()
    set.add(4)
    set.add(4)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>set.size == 2<!>)
}
