// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/time/Instant.kt, safeAddOrElse
// Adaptation: the inline `action: () -> Nothing` parameter becomes a throw. The sign-change check relies on
// wrapping Long `+`, so the function is @WrappingLongArithmetic; the postcondition's range guard keeps `a + b` exact.
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

/**
 * Safely adds two long values.
 * @throws ArithmeticException if the result overflows a long
 */
@WrappingLongArithmetic
private fun <!VERIFICATION_TIMEOUT, VIPER_TEXT!>safeAddW<!>(a: Long, b: Long): Long {
    postconditions<Long> { result ->
        ((b >= 0L) implies (a <= Long.MAX_VALUE - b && result == a + b)) &&
            ((b < 0L) implies (a >= Long.MIN_VALUE - b && result == a + b))
    }
    val sum = a + b
    // check for a change of sign in the result when the inputs have the same sign
    if ((a xor sum) < 0 && (a xor b) >= 0) {
        throw ArithmeticException()
    }
    return sum
}
