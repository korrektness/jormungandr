// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Box(var value: Int)

class Cell(var count: Int, var box: @Unique Box?)

@AlwaysVerify
fun <!VIPER_TEXT!>readSafeCall<!>(b: @Unique @Borrowed Box?): Int {
    return b?.value ?: 0
}

@AlwaysVerify
fun <!VIPER_TEXT!>readFieldSafeCall<!>(c: @Unique @Borrowed Cell): Int {
    return c.box?.value ?: 0
}

@AlwaysVerify
fun <!VIPER_TEXT!>readAfterEarlyReturn<!>(b: @Unique @Borrowed Box?): Int {
    if (b == null) {
        return 0
    }
    b.value = b.value + 1
    return b.value
}

@AlwaysVerify
fun <!VIPER_TEXT!>clearBox<!>(c: @Unique @Borrowed Cell) {
    c.box = null
    c.count = 0
}

@AlwaysVerify
fun <!VIPER_TEXT!>classify<!>(b: @Unique @Borrowed Box): Int {
    when (b.value) {
        0 -> return 0
        1 -> b.value = 2
        else -> b.value = b.value + 1
    }
    return b.value
}
