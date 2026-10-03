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
    val f = <!INTERNAL_ERROR!>fun(y: Int): Int { return y }<!>
    return x
}

<!INTERNAL_ERROR!>@AlwaysVerify
fun callsCrashing(x: Int): Int = crashes(x)<!>
