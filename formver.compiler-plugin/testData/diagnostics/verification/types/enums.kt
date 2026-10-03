// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

enum class Direction { NORTH, SOUTH }

@AlwaysVerify
fun <!VIPER_TEXT!>isControl<!>(c: Char): Boolean = c in CharCategory.CONTROL

@AlwaysVerify
fun <!VIPER_TEXT!>callsIsControl<!>(c: Char): Boolean = isControl(c)

@AlwaysVerify
fun <!VIPER_TEXT!>north<!>(): Direction = Direction.NORTH

@AlwaysVerify
fun <!VIPER_TEXT!>isNorth<!>(d: Direction): Boolean = d == Direction.NORTH

@Pure
fun crashes(x: Int): Int {
    <!UNSUPPORTED_FEATURE, UNSUPPORTED_FEATURE!>class L(<!INTERNAL_ERROR!>val y: Int<!>)<!>
    return L(x).y
}

<!INTERNAL_ERROR!>@AlwaysVerify
fun callsCrashing(x: Int): Int = crashes(x)<!>
