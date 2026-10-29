// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>nonNegativeHalf<!>(n: Int) {
    preconditions { n >= 0 }
    verify(n shr 1 == n / 2)
}

fun <!VIPER_TEXT!>negativeFloor<!>(n: Int) {
    preconditions { n == -3 }
    verify(n shr 1 == -2)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>n shr 1 == -1<!>)
}

fun <!VIPER_TEXT!>maskedDistance<!>(x: Int) {
    verify(x shr 33 == x shr 1, x shr 32 == x, x shr 0 == x)
    verify(x shr 31 == if (x < 0) -1 else 0)
    verify(x shr -31 == x shr 1, x shr -1 == x shr 31)
}

@Pure fun <!VIPER_TEXT!>pureHalf<!>(x: Int): Int {
    postconditions<Int> { it == x shr 1 }
    return x shr 33
}

fun <!VERIFICATION_SKIPPED!>variableDistance<!>(x: Int, distance: Int): Int = <!UNSUPPORTED_FEATURE!>x shr distance<!>
