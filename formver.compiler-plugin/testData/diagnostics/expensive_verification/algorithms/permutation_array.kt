// FULL_JDK
// USE_STDLIB
import org.jetbrains.kotlin.formver.plugin.*

@AlwaysVerify
fun <!VIPER_TEXT!>selectionSort<!>(arr: @Unique @Borrowed IntArray) {
    postconditions<Unit> {
        forAll<Int> { i ->
            forAll<Int> { j ->
                (0 <= i && i < j && j < arr.size) implies (arr[i] <= arr[j])
            }
        }
        contents(arr) == old(contents(arr))
    }
    var i = 0
    while (i < arr.size) {
        loopInvariants {
            0 <= i && i <= arr.size
            forAll<Int> { a ->
                forAll<Int> { b ->
                    (0 <= a && a < b && b < i) implies (arr[a] <= arr[b])
                }
            }
            forAll<Int> { a ->
                forAll<Int> { b ->
                    (0 <= a && a < i && i <= b && b < arr.size) implies (arr[a] <= arr[b])
                }
            }
            contents(arr) == old(contents(arr))
        }
        var m = i
        var k = i + 1
        while (k < arr.size) {
            loopInvariants {
                0 <= i && i <= m && m < k && k <= arr.size
                forAll<Int> { b ->
                    (i <= b && b < k) implies (arr[m] <= arr[b])
                }
                forAll<Int> { a ->
                    forAll<Int> { b ->
                        (0 <= a && a < b && b < i) implies (arr[a] <= arr[b])
                    }
                }
                forAll<Int> { a ->
                    forAll<Int> { b ->
                        (0 <= a && a < i && i <= b && b < arr.size) implies (arr[a] <= arr[b])
                    }
                }
                contents(arr) == old(contents(arr))
            }
            if (arr[k] < arr[m]) {
                m = k
            }
            k++
        }
        val t = arr[i]
        arr[i] = arr[m]
        arr[m] = t
        i++
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>swap<!>(arr: @Unique @Borrowed IntArray, i: Int, j: Int) {
    preconditions {
        0 <= i && i < arr.size
        0 <= j && j < arr.size
    }
    postconditions<Unit> {
        contents(arr) == old(contents(arr))
    }
    val t = arr[i]
    arr[i] = arr[j]
    arr[j] = t
}

@AlwaysVerify
fun <!VIPER_TEXT!>rotateFirstThree<!>(arr: @Unique @Borrowed IntArray) {
    preconditions {
        arr.size >= 3
    }
    postconditions<Unit> {
        contents(arr) == old(contents(arr))
    }
    swap(arr, 0, 1)
    swap(arr, 1, 2)
}

@AlwaysVerify
fun <!VIPER_TEXT!>zeroAll<!>(arr: @Unique @Borrowed IntArray) {
    postconditions<Unit> {
        <!VIPER_VERIFICATION_ERROR!>contents(arr)<!> == old(contents(arr))
    }
    var i = 0
    while (i < arr.size) {
        loopInvariants {
            0 <= i && i <= arr.size
        }
        arr[i] = 0
        i++
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>multisetOperators<!>(x: Int, y: Int) {
    verify(
        multisetOf(x, y, x).count(x) >= 2,
        y in multisetOf(x, y),
        (multisetOf(x, y) - multisetOf(y)) == multisetOf(x),
        (multisetOf(x) + multisetOf(y)).size == 2,
        multisetOf<Int>().size == 0,
    )
}
