// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Box(var value: Int)

class Holder(var count: Int, var box: @Unique Box)

fun <!VIPER_TEXT!>bump<!>(b: @Unique @Borrowed Box) {
    preconditions { b.value < 2147483647 }
    b.value = b.value + 1
}

fun <!VIPER_TEXT!>useHolder<!>(h: @Unique @Borrowed Holder) {
    preconditions { h.box.value < 2147483647 }
    h.count = 3
    val before = h.count
    bump(h.box)
    verify(before == 3, h.count == 3)
}
