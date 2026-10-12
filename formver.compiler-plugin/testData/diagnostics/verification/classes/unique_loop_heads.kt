// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Box(var value: Int)

fun <!VIPER_TEXT!>reset<!>(b: @Unique Box): @Unique Box {
    b.value = 0
    return b
}

fun <!VIPER_TEXT!>sink<!>(b: @Unique Box) {}

fun <!VIPER_TEXT!>bumpEach<!>(b: @Unique @Borrowed Box, n: Int) {
    var i = 0
    while (i < n) {
        b.value = b.value + 1
        i = i + 1
    }
}

fun <!VIPER_TEXT!>cycle<!>(n: Int) {
    var b: @Unique Box = Box(0)
    var i = 0
    while (i < n) {
        b = reset(b)
        i = i + 1
    }
    sink(b)
}

fun <!VIPER_TEXT!>jumps<!>(b: @Unique @Borrowed Box?, n: Int) {
    var i = 0
    while (i < n) {
        i = i + 1
        if (b == null) break
        if (b.value < 0) continue
        b.value = b.value + 1
    }
}

// The loop moves `b` on every iteration, so the checker has it Moved at the head although it is Unique on entry.
fun <!VIPER_TEXT!>movedAtHead<!>(n: Int) {
    var b: @Unique Box = Box(0)
    var i = 0
    while (i < n) {
        b = Box(i)
        sink(b)
        i = i + 1
    }
}

// The checker has `b` Unique at the head, but the fold state does not follow a move out of an `if` expression.
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>pickThenLoop<!>(x: @Unique Box, y: @Unique Box, c: Boolean, n: Int) {
    val b: @Unique Box = if (c) x else y
    var i = 0
    <!UNSUPPORTED_OWNERSHIP!>while (i < n) {
        i = i + 1
    }<!>
    sink(b)
}
