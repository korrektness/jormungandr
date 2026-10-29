// FULL_JDK
// ALWAYS_VALIDATE
// COUNTEREXAMPLES

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>integer<!>(x: Int) {
    preconditions { x == 7 }
    val local = x + 1
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>local != 8<!>)
}

fun <!VIPER_TEXT!>boolean<!>(x: Boolean) {
    preconditions { !x }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x<!>)
}

fun <!VIPER_TEXT!>character<!>(x: Char) {
    preconditions { x == '\n' }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x != '\n'<!>)
}

fun <!VIPER_TEXT!>nullable<!>(x: Int?) {
    preconditions { x == null }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x != null<!>)
}

enum class Choice { ONLY }

fun <!VIPER_TEXT!>enumLike<!>(x: Choice) {
    preconditions { x == Choice.ONLY }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x != Choice.ONLY<!>)
}

<!VIPER_TEXT!>class CounterexampleBox<!>(var value: Int)

fun <!VIPER_TEXT!>fields<!>(box: @Unique @Borrowed CounterexampleBox) <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{
    preconditions { box.value == 3 }
    postconditions<Unit> { box.value == old(box.value) }
    box.value = 4
}<!>

fun (@Unique @Borrowed CounterexampleBox).<!VIPER_TEXT!>receiver<!>() {
    preconditions { value == 3 }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>value != 3<!>)
}

fun <!VIPER_TEXT!>declarationOrder<!>(zeta: Int, alpha: Int) {
    preconditions { zeta == 1; alpha == 2 }
    val middle = zeta + alpha
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>middle != 3<!>)
}

fun <!VIPER_TEXT!>returned<!>(x: Int): Int {
    preconditions { x == 5 }
    postconditions<Int> { it != 6 }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return x + 1<!>
}

fun <!VIPER_TEXT!>widths<!>(l: Long, s: Short, b: Byte) {
    preconditions { l == 5000000000L; s == 300.toShort(); b == (-7).toByte() }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>l != 5000000000L<!>)
}

fun <!VIPER_TEXT!>sizes<!>(xs: @Unique @Borrowed List<Int>, arr: IntArray) {
    preconditions { xs.size == 2; arr.size == 3 }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>xs.size != 2<!>)
}

fun <!VIPER_TEXT!>confirmedOverflow<!>(x: Int): Int {
    preconditions { x == 1073741824 }
    return <!INTEGER_OVERFLOW!>x * 2<!>
}

fun <!VIPER_TEXT!>confirmedDivision<!>(x: Int, y: Int): Int {
    preconditions { x == 0; y == 0 }
    return <!DIVISION_BY_ZERO!>x / y<!>
}

fun <!VIPER_TEXT!>nonlinearOverflow<!>(i: Int): Int {
    preconditions { i >= 0; i <= 50000 }
    return <!INTEGER_OVERFLOW!>i * (i - 1)<!>
}

fun <!VIPER_TEXT!>heapOverflow<!>(box: @Unique @Borrowed CounterexampleBox) {
    preconditions { box.value == 1073741824 }
    postconditions<Unit> { <!INTEGER_OVERFLOW!>old(box.value) * 2<!> != 0 }
}
