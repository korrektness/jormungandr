// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

interface Token

@PointwiseArrayWrites
fun <!VIPER_TEXT!>swapInts<!>(arr: @Unique @Borrowed IntArray, i: Int, j: Int) {
    preconditions {
        0 <= i && i < arr.size
        0 <= j && j < arr.size
    }
    postconditions<Unit> {
        arr[i] == old(arr[j]) && arr[j] == old(arr[i])
        forAll<Int> { k -> (0 <= k && k < arr.size && k != i && k != j) implies (arr[k] == old(arr[k])) }
    }
    val tmp = arr[i]
    arr[i] = arr[j]
    arr[j] = tmp
}

@PointwiseArrayWrites
fun <T : Token> <!VIPER_TEXT!>swapReferences<!>(arr: @Unique @Borrowed Array<T>, i: Int, j: Int) {
    preconditions {
        0 <= i && i < arr.size
        0 <= j && j < arr.size
    }
    postconditions<Unit> {
        arr[i] === old(arr[j]) && arr[j] === old(arr[i])
        forAll<Int> { k -> (0 <= k && k < arr.size && k != i && k != j) implies (arr[k] === old(arr[k])) }
    }
    val tmp = arr[i]
    arr[i] = arr[j]
    arr[j] = tmp
}

@PointwiseArrayWrites
fun <!VIPER_TEXT!>writeIsNotVacuous<!>(arr: @Unique @Borrowed IntArray, i: Int) <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>{
    preconditions {
        0 <= i && i < arr.size
    }
    postconditions<Unit> {
        arr[i] == 1
    }
    arr[i] = 0
}<!>

@PointwiseArrayWrites
fun <!VIPER_TEXT!>initialisedIdentity<!>(n: Int, k: Int) {
    preconditions {
        0 <= k && k < n
    }
    val a: @Unique IntArray = IntArray(n) { it }
    val x = a[k]
    verify(a.size == n, x == k)
}

@PointwiseArrayWrites
fun <!VIPER_TEXT!>writeInLambda<!>(arr: @Unique @Borrowed IntArray, i: Int, j: Int) {
    preconditions {
        0 <= i && i < arr.size
        0 <= j && j < arr.size
        i != j
    }
    postconditions<Unit> {
        arr[i] == 7 && arr[j] == old(arr[j])
    }
    run { arr[i] = 7 }
}
