// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/testData/support/specifications/externalPartiallyVacuousSpecs.kt
// EXTERNAL_TEST_LIBRARY: formver.compiler-plugin/testData/support/libraries/externalLibrary.kt

import external.library.*
import org.jetbrains.kotlin.formver.plugin.*

// The spec contradicts itself for `x == 3` only. docs/soundness.md, "External specifications", says why the check misses it.
fun <!VIPER_TEXT!>partiallyVacuous<!>() {
    verify(twice(3) == 7)
}
