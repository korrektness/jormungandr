// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

@Manual
<!VIPER_TEXT!>class Counter<!>(
    var n: Int
)

fun <!VIPER_TEXT!>bump<!>(c: @Unique @Borrowed Counter, k: Int) {
    preconditions {
        k >= 0
        c.n <= 2147483647 - k
    }
    var i = 0
    while (i < k) {
        loopInvariants {
            acc(UniquePred(c))
            i >= 0
            i <= k
            c.n <= 2147483647 - (k - i)
        }
        unfold(UniquePred(c))
        c.n = c.n + 1
        fold(UniquePred(c))
        i = i + 1
    }
}
