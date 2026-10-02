// FULL_JDK
// USE_STDLIB
import org.jetbrains.kotlin.formver.plugin.*

@AlwaysVerify
fun <!VIPER_TEXT!>insertionSort<!>(arr: @Unique @Borrowed IntArray) {
    postconditions<Unit> {
        forAll<Int> { i ->
            forAll<Int> { j ->
                (0 <= i && i < j && j < arr.size) implies (arr[i] <= arr[j])
            }
        }
    }
    var i = 1
    while (i < arr.size) {
        loopInvariants {
            1 <= i && (i <= arr.size || i == 1)
            forAll<Int> { a ->
                forAll<Int> { b ->
                    (0 <= a && a < b && b < i) implies (arr[a] <= arr[b])
                }
            }
        }
        val key = arr[i]
        var j = i - 1
        while (j >= 0 && arr[j] > key) {
            loopInvariants {
                -1 <= j && j < i && i < arr.size
                forAll<Int> { a ->
                    forAll<Int> { b ->
                        (0 <= a && a < b && b <= i && a != j + 1 && b != j + 1) implies (arr[a] <= arr[b])
                    }
                }
                forAll<Int> { a ->
                    (j + 1 < a && a <= i) implies (key < arr[a])
                }
            }
            arr[j + 1] = arr[j]
            j--
        }
        arr[j + 1] = key
        i++
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>insertionSortProbeInnerLoopBody<!>(arr: @Unique @Borrowed IntArray) {
    postconditions<Unit> {
        forAll<Int> { i ->
            forAll<Int> { j ->
                (0 <= i && i < j && j < arr.size) implies (arr[i] <= arr[j])
            }
        }
    }
    var i = 1
    while (i < arr.size) {
        loopInvariants {
            1 <= i && (i <= arr.size || i == 1)
            forAll<Int> { a ->
                forAll<Int> { b ->
                    (0 <= a && a < b && b < i) implies (arr[a] <= arr[b])
                }
            }
        }
        val key = arr[i]
        var j = i - 1
        while (j >= 0 && arr[j] > key) {
            loopInvariants {
                -1 <= j && j < i && i < arr.size
                forAll<Int> { a ->
                    forAll<Int> { b ->
                        (0 <= a && a < b && b <= i && a != j + 1 && b != j + 1) implies (arr[a] <= arr[b])
                    }
                }
                forAll<Int> { a ->
                    (j + 1 < a && a <= i) implies (key < arr[a])
                }
            }
            arr[j + 1] = arr[j]
            j--
            verify(<!VIPER_VERIFICATION_ERROR!>false<!>)
        }
        arr[j + 1] = key
        i++
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>insertionSortProbeOuterLoopBody<!>(arr: @Unique @Borrowed IntArray) {
    postconditions<Unit> {
        forAll<Int> { i ->
            forAll<Int> { j ->
                (0 <= i && i < j && j < arr.size) implies (arr[i] <= arr[j])
            }
        }
    }
    var i = 1
    while (i < arr.size) {
        loopInvariants {
            1 <= i && (i <= arr.size || i == 1)
            forAll<Int> { a ->
                forAll<Int> { b ->
                    (0 <= a && a < b && b < i) implies (arr[a] <= arr[b])
                }
            }
        }
        val key = arr[i]
        var j = i - 1
        while (j >= 0 && arr[j] > key) {
            loopInvariants {
                -1 <= j && j < i && i < arr.size
                forAll<Int> { a ->
                    forAll<Int> { b ->
                        (0 <= a && a < b && b <= i && a != j + 1 && b != j + 1) implies (arr[a] <= arr[b])
                    }
                }
                forAll<Int> { a ->
                    (j + 1 < a && a <= i) implies (key < arr[a])
                }
            }
            arr[j + 1] = arr[j]
            j--
        }
        arr[j + 1] = key
        i++
        verify(<!VIPER_VERIFICATION_ERROR!>false<!>)
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>insertionSortProbeExit<!>(arr: @Unique @Borrowed IntArray) {
    postconditions<Unit> {
        forAll<Int> { i ->
            forAll<Int> { j ->
                (0 <= i && i < j && j < arr.size) implies (arr[i] <= arr[j])
            }
        }
    }
    var i = 1
    while (i < arr.size) {
        loopInvariants {
            1 <= i && (i <= arr.size || i == 1)
            forAll<Int> { a ->
                forAll<Int> { b ->
                    (0 <= a && a < b && b < i) implies (arr[a] <= arr[b])
                }
            }
        }
        val key = arr[i]
        var j = i - 1
        while (j >= 0 && arr[j] > key) {
            loopInvariants {
                -1 <= j && j < i && i < arr.size
                forAll<Int> { a ->
                    forAll<Int> { b ->
                        (0 <= a && a < b && b <= i && a != j + 1 && b != j + 1) implies (arr[a] <= arr[b])
                    }
                }
                forAll<Int> { a ->
                    (j + 1 < a && a <= i) implies (key < arr[a])
                }
            }
            arr[j + 1] = arr[j]
            j--
        }
        arr[j + 1] = key
        i++
    }
    verify(<!VIPER_VERIFICATION_ERROR!>false<!>)
}

class Sorter(val arr: @Unique IntArray)

@AlwaysVerify
fun <!VIPER_TEXT!>insertionSortThroughVal<!>(s: @Unique @Borrowed Sorter) {
    postconditions<Unit> {
        forAll<Int> { i ->
            forAll<Int> { j ->
                (0 <= i && i < j && j < s.arr.size) implies (s.arr[i] <= s.arr[j])
            }
        }
    }
    var i = 1
    while (i < s.arr.size) {
        loopInvariants {
            1 <= i && (i <= s.arr.size || i == 1)
            forAll<Int> { a ->
                forAll<Int> { b ->
                    (0 <= a && a < b && b < i) implies (s.arr[a] <= s.arr[b])
                }
            }
        }
        val key = s.arr[i]
        var j = i - 1
        while (j >= 0 && s.arr[j] > key) {
            loopInvariants {
                -1 <= j && j < i && i < s.arr.size
                forAll<Int> { a ->
                    forAll<Int> { b ->
                        (0 <= a && a < b && b <= i && a != j + 1 && b != j + 1) implies (s.arr[a] <= s.arr[b])
                    }
                }
                forAll<Int> { a ->
                    (j + 1 < a && a <= i) implies (key < s.arr[a])
                }
            }
            s.arr[j + 1] = s.arr[j]
            j--
        }
        s.arr[j + 1] = key
        i++
    }
}
