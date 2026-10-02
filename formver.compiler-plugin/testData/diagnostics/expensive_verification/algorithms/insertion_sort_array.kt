// FULL_JDK
// USE_STDLIB
import org.jetbrains.kotlin.formver.plugin.*

<!PURITY_VIOLATION, PURITY_VIOLATION!>@AlwaysVerify
fun <!VERIFICATION_SKIPPED!>insertionSort<!>(arr: @Unique @Borrowed IntArray) {
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
            1 <= i && i <= arr.size
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
            arr.set(j + 1, arr[j])
            j--
        }
        arr.set(j + 1, key)
        i++
    }
}<!>
