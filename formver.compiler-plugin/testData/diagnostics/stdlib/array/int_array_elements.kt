// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>swap<!>(arr: @Unique @Borrowed IntArray, i: Int, j: Int) {
    preconditions {
        0 <= i && i < arr.size
        0 <= j && j < arr.size
    }
    postconditions<Unit> {
        arr[i] == old(arr[j]) && arr[j] == old(arr[i])
    }
    val tmp = arr[i]
    arr[i] = arr.get(j)
    arr.set(j, tmp)
}

fun <!VIPER_TEXT!>addTwo<!>(arr: @Unique @Borrowed IntArray, i: Int) {
    preconditions {
        0 <= i && i < arr.size
        arr[i] <= 2147483645
    }
    postconditions<Unit> {
        arr[i] == old(arr[i]) + 2
    }
    arr[i] += 1
    arr[i]++
}

fun <!VIPER_TEXT!>fill<!>(arr: @Unique @Borrowed IntArray, v: Int) {
    postconditions<Unit> {
        forAll<Int> { k -> (0 <= k && k < arr.size) implies (arr[k] == v) }
    }
    var i = 0
    while (i < arr.size) {
        loopInvariants {
            0 <= i && i <= arr.size
            forAll<Int> { k -> (0 <= k && k < i) implies (arr[k] == v) }
        }
        arr[i] = v
        i++
    }
}

// The guard of `||` reads the array as its right operand does, so both are evaluated under one `unfolding`.
fun <!VIPER_TEXT!>boundedByIndexOr<!>(arr: @Unique @Borrowed IntArray, n: Int) {
    preconditions {
        forAll<Int> { k -> (0 <= k && k < arr.size) implies (arr[k] <= k || arr[k] <= n) }
    }
    postconditions<Unit> {
        forAll<Int> { k -> (0 <= k && k < arr.size) implies (arr[k] <= k || arr[k] <= n) }
    }
}

fun <!VIPER_TEXT!>readPastEnd<!>(arr: @Unique @Borrowed IntArray): Int = <!POSSIBLE_INDEX_OUT_OF_BOUND!>arr[arr.size]<!>

fun <!VIPER_TEXT!>writeNegative<!>(arr: @Unique @Borrowed IntArray) {
    <!POSSIBLE_INDEX_OUT_OF_BOUND!>arr[-1] = 0<!>
}

fun <!VIPER_TEXT!>writeShared<!>(arr: IntArray) {
    <!POSSIBLE_INDEX_OUT_OF_BOUND!>arr[0] = 1<!>
    val first = arr[0]
}

fun <!VERIFICATION_SKIPPED!>readSharedInSpec<!>(arr: IntArray): Int {
    postconditions<Int> { r -> r == <!UNSUPPORTED_OWNERSHIP!>arr[0]<!> }
    return 0
}
