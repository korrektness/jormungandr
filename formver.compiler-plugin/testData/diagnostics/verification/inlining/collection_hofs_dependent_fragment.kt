// FULL_JDK
// LANGUAGE: +MultiPlatformProjects

// MODULE: common
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/AssociativeCollections.kt
// FILE: common.kt

fun <!VIPER_TEXT!>commonPlaceholder<!>() {}

// MODULE: jvm()()(common)
// FILE: jvm.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VERIFICATION_SKIPPED!>positives<!>(values: List<Int>): List<Int> = <!UNCHECKED_LIBRARY_CALL!>values.filter <!UNSUPPORTED_FEATURE!>{ it > 0 }<!><!>

fun <!VIPER_TEXT!>filteredPositive<!>(values: @Unique @Borrowed List<Int>) {
    val positive = values.filter { it > 0 }
    positive.forEach { verify(it > 0) }
}

fun <!VIPER_TEXT!>filteredWrong<!>(values: @Unique @Borrowed List<Int>) {
    val positive = values.filter { it > 0 }
    positive.forEach { verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>it > 1<!>) }
}
