// FULL_JDK
// WITH_STDLIB
// TRUST_REPORT
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/IntRanges.kt
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>minimumCapacity<!>(requested: Int, minimum: Int) {
    val capacity = requested.coerceAtLeast(minimum)
    verify(capacity >= requested, capacity >= minimum)
    verify(capacity == if (requested < minimum) minimum else requested)
}

fun <!VIPER_TEXT!>strictMinimum<!>(requested: Int) {
    val capacity = requested.coerceAtLeast(10)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>capacity > requested<!>)
}
