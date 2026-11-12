// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>literalDistances<!>(n: Int) {
    verify(n shr 33 == n shr 1, n shr 32 == n, n shr -31 == n shr 1, n shl 0 == n, n ushr 32 == n)
    verify(n shr 31 == if (n < 0) -1 else 0, n ushr 31 == if (n < 0) 1 else 0)
    verify((n >= 0) implies (n shr 1 == n / 2 && n ushr 1 == n / 2))
}

fun <!VIPER_TEXT!>negativeReceiver<!>(n: Int) {
    preconditions { n == -3 }
    verify(n shr 1 == -2, n ushr 1 == Int.MAX_VALUE - 1, n shl 1 == -6, n shl 30 == 1073741824)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>n shr 1 == -1<!>)
}

fun <!VIPER_TEXT!>wrappingLeftShift<!>(max: Int) {
    preconditions { max == Int.MAX_VALUE }
    verify(max shl 1 == -2, 1 shl 31 == Int.MIN_VALUE, 3 shl 31 == Int.MIN_VALUE, -1 ushr 1 == Int.MAX_VALUE)
}

fun <!VIPER_TEXT!>variableDistances<!>(x: Int, d: Int) {
    verify(x ushr d == x ushr (d and 31), x shl (d + 0) == x shl d)
    if (d == 39) verify(x shl d == x shl 7, x shr d == x shr 7)
    if (d % 32 == 0) verify(x ushr d == x)
    if (0 < d && d < 32) verify(x ushr d >= 0)
}

fun <!VIPER_TEXT!>longShifts<!>(n: Long, d: Int) {
    verify(n shr 63 == if (n < 0L) -1L else 0L, n ushr 63 == if (n < 0L) 1L else 0L, n shl 64 == n)
    verify(1L shl 63 == Long.MIN_VALUE, -1L ushr 1 == Long.MAX_VALUE, Long.MAX_VALUE shl 1 == -2L)
    if (d == 65) verify(n shl d == n shl 1, n ushr d == n ushr 1)
    if (n == -3L) verify(n ushr 1 == Long.MAX_VALUE - 1L)
}

@Pure fun <!VIPER_TEXT!>half<!>(x: Int): Int {
    postconditions<Int> { it == x shr 1 }
    return x shr 33
}

fun <!VIPER_TEXT!>shiftInSpecification<!>(x: Int): Int {
    preconditions { x ushr 31 == 0 }
    postconditions<Int> { it >= 0 }
    return half(x)
}
