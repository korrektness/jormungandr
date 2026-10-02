// FULL_JDK
// USE_STDLIB
import org.jetbrains.kotlin.formver.plugin.*

<!PURITY_VIOLATION, PURITY_VIOLATION!>@AlwaysVerify
fun <!VERIFICATION_SKIPPED!>insertionSort<!>(arr: @Unique @Borrowed IntArray) <!EXIT_UNIQUENESS_INCONSISTENCY!>{
    postconditions<Unit> <!LOCALITY_MISMATCH!>{
        forAll<Int> <!LOCALITY_MISMATCH!>{ i ->
            forAll<Int> <!LOCALITY_MISMATCH!>{ j ->
                (0 <= i && i < j && j < arr.size) implies (arr[i] <= arr[j])
            }<!>
        }<!>
    }<!>
    var i = 1
    while (i < <!INVALID_MOVED_ACCESS!>arr<!>.size) {
        loopInvariants <!LOCALITY_MISMATCH!>{
            1 <= i && i <= arr.size
            forAll<Int> <!LOCALITY_MISMATCH!>{ a ->
                forAll<Int> <!LOCALITY_MISMATCH!>{ b ->
                    (0 <= a && a < b && b < i) implies (arr[a] <= arr[b])
                }<!>
            }<!>
        }<!>
        val key = <!INVALID_MOVED_ACCESS!>arr<!>[i]
        var j = i - 1
        while (j >= 0 && <!INVALID_MOVED_ACCESS!>arr<!>[j] > key) {
            loopInvariants <!LOCALITY_MISMATCH!>{
                -1 <= j && j < i && i < arr.size
                forAll<Int> <!LOCALITY_MISMATCH!>{ a ->
                    forAll<Int> <!LOCALITY_MISMATCH!>{ b ->
                        (0 <= a && a < b && b <= i && a != j + 1 && b != j + 1) implies (arr[a] <= arr[b])
                    }<!>
                }<!>
                forAll<Int> <!LOCALITY_MISMATCH!>{ a ->
                    (j + 1 < a && a <= i) implies (key < arr[a])
                }<!>
            }<!>
            <!INVALID_MOVED_ACCESS!>arr<!>.set(j + 1, <!INVALID_MOVED_ACCESS!>arr<!>[j])
            j--
        }
        <!INVALID_MOVED_ACCESS!>arr<!>.set(j + 1, key)
        i++
    }
}<!><!>
