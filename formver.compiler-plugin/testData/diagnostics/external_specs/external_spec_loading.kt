// NEVER_VALIDATE
// EXTERNAL_TEST_LIBRARY: formver.compiler-plugin/testData/externalSpecLoading/loadingLibrary.kt
// EXTERNAL_SPEC: formver.compiler-plugin/testData/externalSpecLoading/loadingSpecs.kt

import loading.library.Counter
import loading.library.increment
import loading.specs.CounterModel
import loading.specs.incrementModel

fun <!VIPER_TEXT!>useLibrary<!>(counter: Counter) {
    increment(counter)
}

fun <!VIPER_TEXT!>callSpec<!>(counter: Counter) {
    <!EXTERNAL_SPEC_REFERENCE!>incrementModel(counter)<!>
}

fun <!VIPER_TEXT!>typeSpec<!>(model: <!EXTERNAL_SPEC_REFERENCE!>CounterModel<!>): Int = <!EXTERNAL_SPEC_REFERENCE!>model.count<!>
