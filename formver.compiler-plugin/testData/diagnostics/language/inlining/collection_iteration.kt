// FULL_JDK
// RENDER_PREDICATES
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/AssociativeCollections.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>listIteration<!>(values: @Unique @Borrowed List<Int>) {
    verify(iterationSequence(values).size == values.size)
    if (values.size > 0) verify(iterationSequence(values)[0] == values[0])
}

fun <!VIPER_TEXT!>setIteration<!>(values: @Unique @Borrowed Set<Int>, element: Int) {
    verify(iterationSequence(values).size == values.size)
    verify((element in iterationSequence(values)) == values.contains(element))
}

fun <!VIPER_TEXT!>setDistinct<!>(values: @Unique @Borrowed Set<Int>) {
    if (values.size > 1) verify(iterationSequence(values)[0] != iterationSequence(values)[1])
}

fun <!VIPER_TEXT!>immutableEntry<!>(entry: Map.Entry<String, Int>) {
    val key = entry.key
    val value = entry.value
    verify(entry.key == key, entry.value == value)
}

fun <!VIPER_TEXT!>mapIteration<!>(values: @Unique @Borrowed Map<Int, Int>) {
    verify(iterationSequence(values).size == values.size)
    if (values.size > 0) {
        verify(values.containsKey(iterationSequence(values)[0].key))
        verify(values[iterationSequence(values)[0].key] == iterationSequence(values)[0].value)
    }
}
