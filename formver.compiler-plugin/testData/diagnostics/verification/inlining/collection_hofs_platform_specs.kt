// FULL_JDK
// LANGUAGE: +MultiPlatformProjects

// MODULE: common
// FILE: common.kt

import org.jetbrains.kotlin.formver.plugin.*

// The collection specifications are in the platform fragment, so common code has no collection view: the call is an
// opaque library call, whose lambda argument is unsupported.
fun <!VERIFICATION_SKIPPED!>commonForEach<!>(values: @Unique @Borrowed List<Int>) {
    values.forEach <!UNSUPPORTED_FEATURE!>{ }<!>
}

// MODULE: jvm()()(common)
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Iterable.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Collections.kt
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/AssociativeCollections.kt
// FILE: jvm.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>jvmForEach<!>(values: @Unique @Borrowed List<Int>) {
    preconditions { forAll<Int> { i -> (0 <= i && i < values.size) implies (values[i] > 0) } }
    values.forEach { verify(it > 0) }
}
