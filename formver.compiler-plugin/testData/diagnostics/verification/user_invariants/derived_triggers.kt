// FULL_JDK
// USE_STDLIB
import org.jetbrains.kotlin.formver.plugin.*

@AlwaysVerify
fun <!VIPER_TEXT!>swap<!>(arr: @Unique @Borrowed IntArray, i: Int, j: Int) {
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

@AlwaysVerify
fun <!VIPER_TEXT!>swapKeepsSingleQuantifiedFact<!>(arr: @Unique @Borrowed IntArray, i: Int, j: Int, v: Int) {
    preconditions {
        0 <= i && i < arr.size
        0 <= j && j < arr.size
        forAll<Int> { k -> (0 <= k && k < arr.size) implies (arr[k] <= v) }
    }
    postconditions<Unit> {
        forAll<Int> { k -> (0 <= k && k < arr.size) implies (arr[k] <= v) }
    }
    swap(arr, i, j)
}

@AlwaysVerify
fun <!VIPER_TEXT!>swapKeepsSortedPrefix<!>(arr: @Unique @Borrowed IntArray, n: Int) {
    preconditions {
        0 <= n && n + 2 <= arr.size
        forAll<Int> { a -> forAll<Int> { b -> (0 <= a && a < b && b < n) implies (arr[a] <= arr[b]) } }
    }
    postconditions<Unit> {
        forAll<Int> { a -> forAll<Int> { b -> (0 <= a && a < b && b < n) implies (arr[a] <= arr[b]) } }
    }
    swap(arr, n, n + 1)
}

@AlwaysVerify
fun <!VIPER_TEXT!>selectionSort<!>(arr: @Unique @Borrowed IntArray) {
    postconditions<Unit> {
        forAll<Int> { a -> forAll<Int> { b -> (0 <= a && a < b && b < arr.size) implies (arr[a] <= arr[b]) } }
    }
    var i = 0
    while (i < arr.size) {
        loopInvariants {
            0 <= i && i <= arr.size
            forAll<Int> { a -> forAll<Int> { b -> (0 <= a && a < b && b < i) implies (arr[a] <= arr[b]) } }
            forAll<Int> { a -> forAll<Int> { b -> (0 <= a && a < i && i <= b && b < arr.size) implies (arr[a] <= arr[b]) } }
        }
        var m = i
        var j = i + 1
        while (j < arr.size) {
            loopInvariants {
                0 <= i && i < arr.size && i <= m && m < j && j <= arr.size
                forAll<Int> { a -> (i <= a && a < j) implies (arr[m] <= arr[a]) }
                forAll<Int> { a -> forAll<Int> { b -> (0 <= a && a < b && b < i) implies (arr[a] <= arr[b]) } }
                forAll<Int> { a -> forAll<Int> { b -> (0 <= a && a < i && i <= b && b < arr.size) implies (arr[a] <= arr[b]) } }
            }
            if (arr[j] < arr[m]) {
                m = j
            }
            j++
        }
        swap(arr, i, m)
        i++
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>insertionSortWithSwap<!>(arr: @Unique @Borrowed IntArray) {
    postconditions<Unit> {
        forAll<Int> { a -> forAll<Int> { b -> (0 <= a && a < b && b < arr.size) implies (arr[a] <= arr[b]) } }
    }
    var i = 1
    while (i < arr.size) {
        loopInvariants {
            1 <= i && (i <= arr.size || i == 1)
            forAll<Int> { a -> forAll<Int> { b -> (0 <= a && a < b && b < i) implies (arr[a] <= arr[b]) } }
        }
        var j = i
        while (j > 0 && arr[j - 1] > arr[j]) {
            loopInvariants {
                0 <= j && j <= i && i < arr.size
                forAll<Int> { a -> forAll<Int> { b -> (0 <= a && a < b && b <= i && a != j && b != j) implies (arr[a] <= arr[b]) } }
                forAll<Int> { a -> (j < a && a <= i) implies (arr[j] < arr[a]) }
            }
            swap(arr, j - 1, j)
            j--
        }
        i++
    }
}

// The frame is a loop invariant, and the goal after the loop mentions only the current state.
@AlwaysVerify
fun <!VIPER_TEXT!>loopFrameKeepsBound<!>(arr: @Unique @Borrowed IntArray, v: Int) {
    preconditions {
        forAll<Int> { k -> (0 <= k && k < arr.size) implies (arr[k] <= v) }
    }
    postconditions<Unit> {
        forAll<Int> { k -> (0 <= k && k < arr.size) implies (arr[k] <= v) }
    }
    var i = 0
    while (i < arr.size) {
        loopInvariants {
            0 <= i && i <= arr.size
            forAll<Int> { k -> (0 <= k && k < arr.size) implies (arr[k] == old(arr[k])) }
        }
        i++
    }
}

// User triggers are kept as written, with nothing derived next to them.
@AlwaysVerify
fun <!VIPER_TEXT!>userTriggerOnFrame<!>(arr: @Unique @Borrowed IntArray, i: Int) {
    preconditions { 0 <= i && i < arr.size }
    postconditions<Unit> {
        forAll<Int> { k ->
            triggers(arr[k])
            (0 <= k && k < arr.size && k != i) implies (arr[k] == old(arr[k]))
        }
    }
    arr[i] = 0
}

@Pure
fun <!VIPER_TEXT!>first<!>(x: Int, y: Int): Int = x

// The current-state reads that mention `k` have an arithmetic index, so no trigger is derived for `k`. The read of
// `first` mentions `b`, which is bound inside `a`'s quantifier, so `a`'s derived trigger is a subterm without `b`.
@AlwaysVerify
fun <!VIPER_TEXT!>rejectedCandidates<!>(arr: @Unique @Borrowed IntArray) {
    preconditions {
        forAll<Int> { k -> (0 <= k && k + 1 < arr.size) implies (arr[k + 1] == arr[k]) }
    }
    postconditions<Unit> {
        forAll<Int> { k -> (0 <= k && k + 1 < arr.size) implies (arr[k + 1] == old(arr[k])) }
        forAll<Int> { a -> forAll<Int> { b -> (0 <= a && a < arr.size) implies (first(arr[a], b) == old(arr[a])) } }
    }
}
