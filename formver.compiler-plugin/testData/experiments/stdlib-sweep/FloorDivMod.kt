// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/util/FloorDivMod.kt, Long.floorDiv(Long), Long.mod(Long)
// Adaptation: extension functions renamed so they do not shadow the stdlib members; @InlineOnly dropped.
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

/** Divides this value by the other value, flooring the result to an integer that is closer to negative infinity. */
fun Long.<!VERIFICATION_TIMEOUT, VIPER_TEXT!>floorDivL<!>(other: Long): Long {
    preconditions { other != 0L }
    postconditions<Long> { result ->
        (other > 0L) implies (result <= this / other && (result == this / other || result == this / other - 1L))
    }
    var q = this / other
    if (this xor other < 0 && q * other != this) q--
    return q
}

/**
 * Calculates the remainder of flooring division of this value (dividend) by the other value (divisor).
 *
 * The result is either zero or has the same sign as the _divisor_ and has the absolute value less than the absolute value of the divisor.
 */
fun Long.<!VERIFICATION_TIMEOUT, VIPER_TEXT!>modL<!>(other: Long): Long {
    preconditions { other != 0L }
    postconditions<Long> { result ->
        ((other > 0L) implies (0L <= result && result < other)) &&
            ((other < 0L) implies (other < result && result <= 0L))
    }
    val r = this % other
    return r + (other and (((r xor other) and (r or -r)) shr 63))
}
