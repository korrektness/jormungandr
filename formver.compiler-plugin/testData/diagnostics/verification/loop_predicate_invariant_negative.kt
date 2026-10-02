// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

@Manual
class Counter(
    var n: Int
)

// Without the predicate in the invariant, the loop head holds no permission to c.
<!VIPER_VERIFICATION_ERROR!>@AlwaysVerify
fun <!VIPER_TEXT!>bump<!>(c: @Unique @Borrowed Counter, k: Int) {
    preconditions {
        k >= 0
    }
    var i = 0
    while (i < k) {
        loopInvariants {
            i >= 0
        }
        unfold(UniquePred(c))
        c.n = c.n + 1
        fold(UniquePred(c))
        i = i + 1
    }
}<!>
