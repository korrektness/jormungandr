// FULL_JDK
// WITH_STDLIB
// TRUST_REPORT
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>usesBoundLibraryFunction<!>() {
    val list = mutableListOf<Int>()
    list.add(3)
    verify(!list.isEmpty() && list[0] == 3)
}
