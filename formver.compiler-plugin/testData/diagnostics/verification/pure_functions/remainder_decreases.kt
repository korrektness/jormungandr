// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

// Remainder supplies a strict natural-number decrease when the divisor is positive.
@Pure
fun <!VIPER_TEXT!>gcd<!>(m: Int, n: Int): Int {
    preconditions {
        m >= 0
        n >= 0
        decreases(n)
    }
    postconditions<Int> { result -> result >= 0 }
    return if (n == 0) m else gcd(n, m % n)
}

fun <!VIPER_TEXT!>gcdBaseCase<!>(m: Int) {
    preconditions { m >= 0 }
    refute(false)
    verify(gcd(m, 0) == m)
}
