// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

// A non-negative dividend keeps each division within Int.
@Pure
fun <!VIPER_TEXT!>eagerAnd<!>(a: Int, b: Int): Boolean {
    preconditions { a >= 0 }
    return (b != 0) and (<!DIVISION_BY_ZERO!>a / b<!> > 0)
}

@Pure
fun <!VIPER_TEXT!>eagerOr<!>(a: Int, b: Int): Boolean {
    preconditions { a >= 0 }
    return (b == 0) or (<!DIVISION_BY_ZERO!>a / b<!> > 0)
}

fun <!VIPER_TEXT!>eagerAndInBody<!>(a: Int, b: Int): Boolean {
    preconditions { a >= 0 }
    return (b != 0) and (<!DIVISION_BY_ZERO!>a / b<!> > 0)
}

fun <!VIPER_TEXT!>eagerOrInPostcondition<!>(a: Int, b: Int): Int {
    preconditions { a >= 0 }
    postconditions<Int> { r -> (b == 0) or (<!DIVISION_BY_ZERO!>a / b<!> == r) }
    return if (b == 0) 0 else a / b
}

fun <!VIPER_TEXT!>shortCircuitOrInPostcondition<!>(a: Int, b: Int): Int {
    preconditions { a >= 0 }
    postconditions<Int> { r -> b == 0 || a / b == r }
    return if (b == 0) 0 else a / b
}

@Pure
fun <!VIPER_TEXT!>guardedEagerAnd<!>(a: Int, b: Int): Boolean {
    preconditions { a >= 0 && b != 0 }
    return (b != 0) and (a / b > 0)
}
