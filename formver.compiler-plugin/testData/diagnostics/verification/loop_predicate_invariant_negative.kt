// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

@Manual
class Counter(
    var n: @Unique Int
)

// Without the predicate in the invariant, the loop head holds no permission to c.
@AlwaysVerify
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>bump<!>(c: @Unique @Borrowed Counter, k: Int) <!EXIT_UNIQUENESS_INCONSISTENCY!>{
    preconditions {
        k >= 0
    }
    var i = 0
    while (i < k) {
        loopInvariants {
            i >= 0
        }
        unfold(UniquePred(<!INVALID_MOVED_ACCESS, LOCALITY_MISMATCH!>c<!>))
        <!INVALID_MOVED_ACCESS!>c<!>.n = <!UNIQUENESS_MISMATCH!><!INVALID_MOVED_ACCESS!>c<!>.n + 1<!>
        fold(UniquePred(<!INVALID_MOVED_ACCESS, LOCALITY_MISMATCH!>c<!>))
        i = i + 1
    }
}<!>
