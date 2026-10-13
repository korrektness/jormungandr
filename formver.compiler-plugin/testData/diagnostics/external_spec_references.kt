// EXTERNAL_SPEC: formver.compiler-plugin/testData/externalCounterSpecs.kt
// EXTERNAL_TEST_LIBRARY: formver.compiler-plugin/testData/externalLibrary.kt

import external.models.*

fun <!VIPER_TEXT!>forbiddenCall<!>(): Int = <!EXTERNAL_SPEC_REFERENCE!>twiceModel(3)<!>
fun <!VIPER_TEXT!>forbiddenType<!>(value: <!EXTERNAL_SPEC_REFERENCE!>CounterModel<!>): <!EXTERNAL_SPEC_REFERENCE!>CounterModel<!> = value
val forbiddenReference = <!EXTERNAL_SPEC_REFERENCE!>::twiceModel<!>
