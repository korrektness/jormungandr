// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>addition<!>(x: Int) {
    preconditions { -1073741824 <= x && x <= 1073741823 }
    val y = x + x
}

fun <!VIPER_TEXT!>subtraction<!>(x: Int) {
    val y = x - x
}

fun <!VIPER_TEXT!>multiplication<!>(x: Int) {
    preconditions { -46340 <= x && x <= 46340 }
    val y = x * x
}

// Zero is allowed to exercise division-by-zero verification.
fun <!VIPER_TEXT!>division<!>(x: Int) {
    val y = <!DIVISION_BY_ZERO!>x / x<!>
}

// Zero is allowed to exercise remainder-by-zero verification.
fun <!VIPER_TEXT!>remainder<!>(x: Int) {
    val y = <!DIVISION_BY_ZERO!>x % x<!>
}

fun <!VIPER_TEXT!>unary_minus<!>(x: Int) {
    preconditions { x > -2147483648 }
    val y = -x
}

fun <!VIPER_TEXT!>less<!>(x: Int, y: Int): Boolean {
    return x < y
}

fun <!VIPER_TEXT!>lessEqual<!>(x: Int, y: Int): Boolean {
    return x <= y
}

fun <!VIPER_TEXT!>greater<!>(x: Int, y: Int): Boolean {
    return x > y
}

fun <!VIPER_TEXT!>greaterEqual<!>(x: Int, y: Int): Boolean {
    return x >= y
}

fun <!VIPER_TEXT!>negation<!>(x: Boolean): Boolean {
    return !x
}

fun <!VIPER_TEXT!>conjunction<!>(x: Boolean, y: Boolean): Boolean {
    return x && y
}

fun <!VIPER_TEXT!>conjunctionSideEffects<!>(x: Boolean, y: Boolean): Boolean {
    // This does not actually have side effects, but the code should compile as if it might.
    return negation(x) && negation(y)
}

fun <!VIPER_TEXT!>disjunction<!>(x: Boolean, y: Boolean): Boolean {
    return x || y
}

fun <!VIPER_TEXT!>test_simple<!>() {
    var x = 10
    x++
    x--
    ++x
    --x
}

fun <!VIPER_TEXT!>test_postincvrement<!>() {
    var x = 10
    val first = x++
    val second = x--
    val unary = x
}
