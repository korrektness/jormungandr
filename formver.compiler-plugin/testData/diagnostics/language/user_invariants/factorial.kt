// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

// The factorials that fit in Int.
@Pure
fun <!VIPER_TEXT!>factTable<!>(n: Int): Int {
    preconditions { 0 <= n && n <= 12 }
    return when (n) {
        0 -> 1
        1 -> 1
        2 -> 2
        3 -> 6
        4 -> 24
        5 -> 120
        6 -> 720
        7 -> 5040
        8 -> 40320
        9 -> 362880
        10 -> 3628800
        11 -> 39916800
        else -> 479001600
    }
}

@Pure
fun <!VIPER_TEXT!>fact<!>(n: Int): Int {
    preconditions {
        decreases(n)
        0 <= n && n <= 12
    }
    postconditions<Int> { res -> res == factTable(n) }

    return if (n == 0) {
        1
    } else {
        n * fact(n - 1)
    }
}

fun <!VIPER_TEXT!>factStep<!>(n: Int) {
    preconditions { 1 <= n && n <= 12 }
    verify(fact(n) == n * fact(n - 1))
}
