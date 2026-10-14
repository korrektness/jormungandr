// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>fill<!>(arr: @Unique @Borrowed IntArray, v: Int) {
    postconditions<Unit> {
        forAll<Int> { k -> (0 <= k && k < arr.size) implies (arr[k] == v) }
    }
    for (i in 0 until arr.size) {
        loopInvariants {
            forAll<Int> { k -> (0 <= k && k < i) implies (arr[k] == v) }
        }
        arr[i] = v
    }
}

fun <!VIPER_TEXT!>sum<!>(arr: @Unique @Borrowed IntArray): Int {
    preconditions {
        arr.size <= 214748364
        forAll<Int> { k -> (0 <= k && k < arr.size) implies (0 <= arr[k] && arr[k] <= 10) }
    }
    postconditions<Int> { result -> 0 <= result && result <= 10 * arr.size }
    var total = 0
    for (i in 0 ..< arr.size) {
        loopInvariants {
            arr.size <= 214748364
            forAll<Int> { k -> (0 <= k && k < arr.size) implies (0 <= arr[k] && arr[k] <= 10) }
            0 <= total && total <= 10 * i
        }
        total += arr[i]
    }
    return total
}

// The bound is evaluated once, so changing `n` in the body does not change the iterations.
fun <!VIPER_TEXT!>countInclusive<!>(n: Int): Int {
    preconditions { 0 <= n && n < 2147483647 }
    postconditions<Int> { result -> result == n + 1 }
    var m = n
    var count = 0
    for (i in 0..m) {
        loopInvariants { count == i && i <= n + 1 }
        m = 0
        count++
    }
    return count
}

fun <!VIPER_TEXT!>clearBackwards<!>(arr: @Unique @Borrowed IntArray) {
    postconditions<Unit> {
        forAll<Int> { k -> (0 <= k && k < arr.size) implies (arr[k] == 0) }
    }
    for (i in arr.size - 1 downTo 0) {
        loopInvariants {
            forAll<Int> { k -> (i < k && k < arr.size) implies (arr[k] == 0) }
        }
        arr[i] = 0
    }
}

fun <!VIPER_TEXT!>evenSlots<!>(arr: @Unique @Borrowed IntArray, s: Int) {
    preconditions { arr.size <= 2147483645 }
    for (i in 0 until arr.size step 2) {
        loopInvariants { arr.size <= 2147483645 }
        arr[i] = 1
    }
    for (i in arr.size - 1 downTo 0 step s) {
        arr[i] = 2
    }
}

// `continue` still steps the variable, and `break` leaves with it in bounds.
fun <!VIPER_TEXT!>skipAndStop<!>(arr: @Unique @Borrowed IntArray, stop: Int): Int {
    postconditions<Int> { result -> 0 <= result && result <= arr.size }
    var last = 0
    for (i in 0 until arr.size) {
        loopInvariants { 0 <= last && last <= i }
        if (arr[i] == 0) continue
        last = i
        if (i == stop) break
    }
    return last
}

// A range with no elements leaves the variable at its start.
fun <!VIPER_TEXT!>emptyRange<!>(): Int {
    postconditions<Int> { result -> result == 0 }
    var count = 0
    for (i in 5 until 3) {
        loopInvariants { count == i - 5 }
        count++
    }
    return count
}

fun <!VIPER_TEXT!>writePastEnd<!>(arr: @Unique @Borrowed IntArray) {
    preconditions { arr.size < 2147483647 }
    for (i in 0..arr.size) {
        loopInvariants { arr.size < 2147483647 }
        <!POSSIBLE_INDEX_OUT_OF_BOUND!>arr[i] = 0<!>
    }
}

// Kotlin stops at the last element without stepping, so a range ending at `Int.MAX_VALUE` does not overflow.
fun <!VIPER_TEXT!>rangeToMax<!>(): Int {
    postconditions<Int> { it == 3 }
    var count = 0
    for (i in Int.MAX_VALUE - 2..Int.MAX_VALUE) {
        loopInvariants { count == i - (Int.MAX_VALUE - 2) }
        count++
    }
    return count
}

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>fun <!VIPER_TEXT!>rangeToMaxMiscounted<!>(): Int {
    postconditions<Int> { it == 2 }
    var count = 0
    for (i in Int.MAX_VALUE - 2..Int.MAX_VALUE) {
        loopInvariants { count == i - (Int.MAX_VALUE - 2) }
        count++
    }
    return count
}<!>

fun <!VIPER_TEXT!>downToMin<!>(): Int {
    postconditions<Int> { it == 3 }
    var count = 0
    for (i in Int.MIN_VALUE + 2 downTo Int.MIN_VALUE) {
        loopInvariants { count == (Int.MIN_VALUE + 2) - i }
        count++
    }
    return count
}

fun <!VIPER_TEXT!>stepPastMax<!>(): Int {
    postconditions<Int> { it == 2 }
    var count = 0
    for (i in Int.MAX_VALUE - 3 until Int.MAX_VALUE step 2) {
        loopInvariants { 0 <= count && count <= 1 && i == Int.MAX_VALUE - 3 + 2 * count }
        count++
    }
    return count
}

// The last element is still visited.
fun <!VIPER_TEXT!>lastElementVisited<!>(): Int {
    postconditions<Int> { it == Int.MAX_VALUE }
    var last = 0
    for (i in Int.MAX_VALUE - 2..Int.MAX_VALUE) {
        last = i
    }
    return last
}
