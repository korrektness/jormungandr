// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/AssociativeCollections.kt

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Counter<!> {
    private val counts = LinkedHashMap<String, Int>()

    fun <!VIPER_TEXT!>record<!>(key: String) {
        <!OPAQUE_CALL!>counts.put(key, 1)<!>
    }

    fun <!VIPER_TEXT!>recordThenDivide<!>(key: String): Int {
        <!OPAQUE_CALL!>counts.put(key, 1)<!>
        return <!DIVISION_BY_ZERO!>100 / counts.size<!>
    }
}

fun <!VIPER_TEXT!>fresh<!>(): Int {
    val counts = LinkedHashMap<String, Int>()
    <!OPAQUE_CALL!>counts.put("a", 1)<!>
    return counts.size
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>demandsOwnership<!>(): LinkedHashMap<String, Int> {
    val counts: @Unique LinkedHashMap<String, Int> = <!UNIQUENESS_MISMATCH!>LinkedHashMap()<!>
    return counts
}
