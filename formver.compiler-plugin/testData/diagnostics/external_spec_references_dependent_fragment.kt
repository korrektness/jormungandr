// FULL_JDK
// LANGUAGE: +MultiPlatformProjects
// EXTERNAL_TEST_LIBRARY: formver.compiler-plugin/testData/externalLibrary.kt

// MODULE: common
// EXTERNAL_SPEC: formver.compiler-plugin/testData/externalCounterSpecs.kt
// FILE: common.kt

fun <!VIPER_TEXT!>commonPlaceholder<!>() {}

// MODULE: jvm()()(common)
// FILE: jvm.kt

import external.models.*

fun <!VIPER_TEXT!>forbiddenCall<!>(): Int = <!EXTERNAL_SPEC_REFERENCE!>twiceModel(3)<!>
fun <!VIPER_TEXT!>forbiddenType<!>(value: <!EXTERNAL_SPEC_REFERENCE!>CounterModel<!>): <!EXTERNAL_SPEC_REFERENCE!>CounterModel<!> = value
