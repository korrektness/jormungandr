// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

@Manual
class Counter(
    var n: Int
)

@AlwaysVerify
fun <!VIPER_TEXT!>bump<!>(c: @Unique @Borrowed Counter, k: Int) {
    preconditions {
        k >= 0
    }
    var i = 0
    while (i < k) {
        loopInvariants {
            acc(UniquePred(c))
        }
        unfold(UniquePred(c))
        c.n = c.n + 1
        fold(UniquePred(c))
        i = i + 1
    }
}
