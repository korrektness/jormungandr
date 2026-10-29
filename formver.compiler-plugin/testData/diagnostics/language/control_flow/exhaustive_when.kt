// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

enum class Kind { FIRST, SECOND }

open <!VIPER_TEXT!>class Base<!>
<!VIPER_TEXT!>class A<!> : Base()
<!VIPER_TEXT!>class B<!> : Base()

sealed <!VIPER_TEXT!>class Shape<!>
<!VIPER_TEXT!>class Circle<!> : Shape()
sealed <!VIPER_TEXT!>class Polygon<!> : Shape()
<!VIPER_TEXT!>class Square<!> : Polygon()
<!VIPER_TEXT!>class Triangle<!> : Polygon()

fun <!VIPER_TEXT!>exhaustiveEnumWhen<!>(k: Kind): Base = when (k) {
    Kind.FIRST -> A()
    Kind.SECOND -> B()
}

fun <!VIPER_TEXT!>exhaustiveNullableEnumWhen<!>(k: Kind?): Base = when (k) {
    Kind.FIRST -> A()
    Kind.SECOND -> B()
    null -> Base()
}

fun <!VIPER_TEXT!>exhaustiveSealedWhen<!>(s: Shape): Base = when (s) {
    is Circle -> A()
    is Polygon -> B()
}

fun <!VIPER_TEXT!>exhaustiveNestedSealedWhen<!>(s: Shape): Base = when (s) {
    is Circle -> A()
    is Square -> B()
    is Triangle -> Base()
}

fun <!VIPER_TEXT!>exhaustiveBooleanWhen<!>(b: Boolean): Base = when (b) {
    true -> A()
    false -> B()
}

fun <!VIPER_TEXT!>exhaustiveStatementWhen<!>(k: Kind): Int {
    var result = 0
    when (k) {
        Kind.FIRST -> result = 1
        Kind.SECOND -> result = 2
    }
    verify(result != 0)
    return result
}

@Pure
fun <!VIPER_TEXT!>pureEnumWhen<!>(k: Kind): Int = when (k) {
    Kind.FIRST -> 1
    Kind.SECOND -> 2
}

fun <!VIPER_TEXT!>callPureEnumWhen<!>(): Int {
    postconditions<Int> { it == 1 }
    return pureEnumWhen(Kind.FIRST)
}

// A `when` over an Int is not exhaustive, so its fall-through stays reachable.
fun <!VIPER_TEXT!>nonExhaustiveWhen<!>(x: Int): Int {
    var result = 0
    when (x) {
        1 -> result = 1
        2 -> result = 2
    }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>result != 0<!>)
    return result
}

sealed interface Signal
<!VIPER_TEXT!>object On<!> : Signal
<!VIPER_TEXT!>object Off<!> : Signal

// SnaKt does not identify the values of an object type, so matching by equality with an object is not established.
fun <!VIPER_TEXT!>objectWhen<!>(s: Signal): Int = <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>when (s) {
    On -> 1
    Off -> 0
}<!>
