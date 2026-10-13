// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Box<!>(var value: Int)

fun <!VIPER_TEXT!>boundAll<!>(b: @Unique @Borrowed Box) {
    postconditions<Unit> {
        b.value == 0
        forAll<Int> { k -> (0 <= k && k < 3 && b.value <= 2147483645) implies (b.value + k >= k) }
    }
    b.value = 0
}

fun <!VIPER_TEXT!>clearIfPresent<!>(b: @Unique @Borrowed Box?) {
    postconditions<Unit> {
        b == null || b.value == 0
    }
    if (b != null) b.value = 0
}

fun <!VIPER_TEXT!>inRange<!>(b: @Unique @Borrowed Box) {
    postconditions<Unit> {
        0 <= b.value && b.value <= 10
    }
    b.value = 5
}

<!VIPER_TEXT!>class ArrayHolder<!>(var arr: @Unique IntArray)

fun <!VIPER_TEXT!>sizeGuardThroughVar<!>(h: @Unique @Borrowed ArrayHolder) {
    postconditions<Unit> {
        forAll<Int> { j -> (0 <= j && j < h.arr.size) implies (h.arr[j] == h.arr[j]) }
    }
}
