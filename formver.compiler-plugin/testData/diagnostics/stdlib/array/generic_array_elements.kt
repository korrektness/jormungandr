// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

open <!VIPER_TEXT!>class Token<!>

fun <T : Token> <!VIPER_TEXT!>swap<!>(arr: @Unique @Borrowed Array<T>, i: Int, j: Int) {
    preconditions {
        0 <= i && i < arr.size
        0 <= j && j < arr.size
    }
    postconditions<Unit> {
        arr[i] === old(arr[j]) && arr[j] === old(arr[i])
        forAll<Int> { k -> (0 <= k && k < arr.size && k != i && k != j) implies (arr[k] === old(arr[k])) }
    }
    val tmp = arr[i]
    arr[i] = arr.get(j)
    arr.set(j, tmp)
}

fun <!VIPER_TEXT!>fillNulls<!>(arr: @Unique @Borrowed Array<Any?>) {
    postconditions<Unit> {
        forAll<Int> { k -> (0 <= k && k < arr.size) implies (arr[k] == null) }
    }
    var i = 0
    while (i < arr.size) {
        loopInvariants {
            0 <= i && i <= arr.size
            forAll<Int> { k -> (0 <= k && k < i) implies (arr[k] == null) }
        }
        arr[i] = null
        i++
    }
}

fun <!VIPER_TEXT!>newArrayIsNull<!>(n: Int) {
    preconditions { n >= 0 }
    val arr: @Unique Array<Any?> = arrayOfNulls<Any?>(n)
    verify(arr.size == n)
    verify(forAll<Int> { k -> (0 <= k && k < n) implies (arr[k] == null) })
    if (n > 0) {
        arr[0] = "value"
        verify(arr[0] == "value")
    }
    val last = arr.lastIndex
    verify(last == n - 1)
    if (last >= 0) {
        arr[last] = null
        verify(arr[last] == null)
    }
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>last == n<!>)
}

fun <!VIPER_TEXT!>empty<!>() {
    val arr: @Unique Array<String> = emptyArray<String>()
    verify(arr.size == 0 && arr.lastIndex == -1)
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>lastIndexAfterMove<!>() {
    val arr: @Unique Array<Any?> = arrayOfNulls(1)
    val moved: @Unique Array<Any?> = arr
    val last = <!INVALID_MOVED_ACCESS!>arr<!>.lastIndex
    moved[0] = null
}

fun <!VIPER_TEXT!>storeOwned<!>(arr: @Unique @Borrowed Array<Token?>) {
    preconditions { arr.size > 0 }
    val value: @Unique Token = Token()
    arr[0] = value
    verify(arr[0] != null)
    arr.set(0, null)
    verify(arr[0] == null)
}

fun <!VIPER_TEXT!>readTyped<!>(arr: @Unique @Borrowed Array<String>, i: Int): Int {
    preconditions { 0 <= i && i < arr.size }
    postconditions<Int> { it >= 0 }
    return arr[i].length
}

fun <!VIPER_TEXT!>writeShared<!>(arr: Array<Any?>) {
    preconditions { arr.size > 0 }
    arr[0] = null
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>arr[0] == null<!>)
}

fun <T> <!VIPER_TEXT!>readPastEnd<!>(arr: @Unique @Borrowed Array<T>): T = <!POSSIBLE_INDEX_OUT_OF_BOUND!>arr[arr.size]<!>

fun <!VIPER_TEXT!>newArrayNonNull<!>() {
    val arr: @Unique Array<String?> = arrayOfNulls<String>(1)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>arr[0] != null<!>)
}
