// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

@AlwaysVerify
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

@AlwaysVerify
fun <!VIPER_TEXT!>sum<!>(arr: @Unique @Borrowed IntArray): Int {
    preconditions {
        forAll<Int> { k -> (0 <= k && k < arr.size) implies (0 <= arr[k] && arr[k] <= 10) }
    }
    postconditions<Int> { result -> 0 <= result && result <= 10 * arr.size }
    var total = 0
    for (i in 0 ..< arr.size) {
        loopInvariants {
            forAll<Int> { k -> (0 <= k && k < arr.size) implies (0 <= arr[k] && arr[k] <= 10) }
            0 <= total && total <= 10 * i
        }
        total += arr[i]
    }
    return total
}

// The bound is evaluated once, so changing `n` in the body does not change the iterations.
@AlwaysVerify
fun <!VIPER_TEXT!>countInclusive<!>(n: Int): Int {
    preconditions { n >= 0 }
    postconditions<Int> { result -> result == n + 1 }
    var m = n
    var count = 0
    for (i in 0..m) {
        loopInvariants { count == i }
        m = 0
        count++
    }
    return count
}

@AlwaysVerify
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

@AlwaysVerify
fun <!VIPER_TEXT!>evenSlots<!>(arr: @Unique @Borrowed IntArray, s: Int) {
    for (i in 0 until arr.size step 2) {
        arr[i] = 1
    }
    for (i in arr.size - 1 downTo 0 step s) {
        arr[i] = 2
    }
}

// `continue` still steps the variable, and `break` leaves with it in bounds.
@AlwaysVerify
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
@AlwaysVerify
fun <!VIPER_TEXT!>emptyRange<!>(): Int {
    postconditions<Int> { result -> result == 0 }
    var count = 0
    for (i in 5 until 3) {
        loopInvariants { count == i - 5 }
        count++
    }
    return count
}

@AlwaysVerify
fun <!VIPER_TEXT!>writePastEnd<!>(arr: @Unique @Borrowed IntArray) {
    for (i in 0..arr.size) {
        <!POSSIBLE_INDEX_OUT_OF_BOUND!>arr[i] = 0<!>
    }
}
