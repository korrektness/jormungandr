// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/testData/externalVacuousSpecs.kt
// EXTERNAL_TEST_LIBRARY: formver.compiler-plugin/testData/externalLibrary.kt

import external.library.*
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>doubled<!>() {
    verify(twice(3) == 6)
}
