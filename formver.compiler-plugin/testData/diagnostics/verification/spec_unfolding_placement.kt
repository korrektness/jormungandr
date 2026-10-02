// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Box(var value: Int)

@AlwaysVerify
fun <!VIPER_TEXT!>boundAll<!>(b: @Unique @Borrowed Box) {
    postconditions<Unit> {
        forAll<Int> { k -> (0 <= k && k < 3) implies (b.value + k >= k) }
    }
    b.value = 0
}

@AlwaysVerify
fun <!VIPER_TEXT!>clearIfPresent<!>(b: @Unique @Borrowed Box?) {
    postconditions<Unit> {
        b == null || b.value == 0
    }
    if (b != null) b.value = 0
}

@AlwaysVerify
fun <!VIPER_TEXT!>inRange<!>(b: @Unique @Borrowed Box) {
    postconditions<Unit> {
        0 <= b.value && b.value <= 10
    }
    b.value = 5
}
