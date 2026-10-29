package stand.vlib

import org.jetbrains.kotlin.formver.plugin.*

fun half(x: Int): Int {
    preconditions { x >= 0 }
    postconditions<Int> { result -> result <= x }
    return x / 2
}

class Box {
    val size: Int
        get() = 1
}
