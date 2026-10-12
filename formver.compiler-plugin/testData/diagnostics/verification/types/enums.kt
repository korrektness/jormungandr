// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

enum class Direction { NORTH, SOUTH }

fun <!VIPER_TEXT!>isControl<!>(c: Char): Boolean = c in CharCategory.CONTROL

fun <!VIPER_TEXT!>callsIsControl<!>(c: Char): Boolean = isControl(c)

fun <!VIPER_TEXT!>north<!>(): Direction = Direction.NORTH

fun <!VIPER_TEXT!>isNorth<!>(d: Direction): Boolean = d == Direction.NORTH

@NeverConvert
inline fun applyTo(x: Int, f: (Int) -> Int): Int = f(x)

@Pure
fun crashes(x: Int): Int = applyTo(x, fun(y: Int): Int { <!INTERNAL_ERROR!>return y<!> })

<!INTERNAL_ERROR!>fun callsCrashing(x: Int): Int = crashes(x)<!>
