// FULL_JDK
// LANGUAGE: +MultiPlatformProjects

// MODULE: common
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/AssociativeCollections.kt
// FILE: common.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>commonForEach<!>(values: @Unique @Borrowed List<Int>) {
    preconditions { forAll<Int> { i -> (0 <= i && i < values.size) implies (values[i] > 0) } }
    values.forEach { verify(it > 0) }
}

// MODULE: jvm()()(common)
// FILE: jvm.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>jvmForEach<!>(values: @Unique @Borrowed List<Int>) {
    preconditions { forAll<Int> { i -> (0 <= i && i < values.size) implies (values[i] > 0) } }
    values.forEach { verify(it > 0) }
}
