// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Registry<!> {
    val items = mutableListOf<Int>()

    fun <!VIPER_TEXT!>register<!>(x: Int) {
        <!UNCHECKED_LIBRARY_CALL!>items.add(x)<!>
    }

    fun <!VIPER_TEXT!>registerThenDivide<!>(x: Int): Int {
        <!UNCHECKED_LIBRARY_CALL!>items.add(x)<!>
        return <!DIVISION_BY_ZERO!>100 / <!UNCHECKED_LIBRARY_CALL!>items.size<!><!>
    }
}

val topLevelItems = mutableListOf<Int>()

fun <!VIPER_TEXT!>registerTopLevel<!>(x: Int) {
    <!UNCHECKED_LIBRARY_CALL!><!OPAQUE_CALL!>topLevelItems<!>.add(x)<!>
}

<!VIPER_TEXT!>class Counters<!> {
    val counts = IntArray(4)

    fun <!VIPER_TEXT!>bump<!>() {
        if (counts.size > 0) counts[0] = 1
    }

    fun <!VIPER_TEXT!>readAfterWrite<!>(): Int {
        if (counts.size == 0) return 0
        counts[0] = 1
        return <!DIVISION_BY_ZERO!>100 / counts[0]<!>
    }
}
