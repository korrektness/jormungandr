// FULL_JDK
// WITH_STDLIB
// EXTERNAL_TEST_LIBRARY: formver.compiler-plugin/testData/support/libraries/lateinitBase.kt

import external.library.LateBase
import kotlin.properties.ObservableProperty

<!VIPER_TEXT!>class Counter<!>(start: Int) : ObservableProperty<Int>(start) {
    private var changes = 0
}

<!VIPER_TEXT!>class Share<!>(total: Int, parts: Int) : ObservableProperty<Int>(0) {
    private var each = <!DIVISION_BY_ZERO, INTEGER_OVERFLOW!>total / parts<!>
}

fun <!VIPER_TEXT!>makeCounter<!>(): Int {
    val c = Counter(3)
    return 1
}

<!OWNERSHIP_NOT_ESTABLISHED!><!VIPER_TEXT!>class Named<!> : LateBase() {
    private var uses = 0
}<!>
