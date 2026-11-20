// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/time/Duration.kt, Long.addMillisWithoutOverflow
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

internal const val INVALID_RAW_VALUE = 0x7FFFFFFFFFFFC0DE
internal const val MAX_MILLIS = Long.MAX_VALUE / 2

private <!NOTHING_TO_INLINE!>inline<!> fun Long.<!VIPER_TEXT!>isInfiniteMillis<!>(): Boolean = this == MAX_MILLIS || this == -MAX_MILLIS
private <!NOTHING_TO_INLINE!>inline<!> fun Long.<!VIPER_TEXT!>isFiniteMillis<!>(): Boolean = -MAX_MILLIS < this && this < MAX_MILLIS
private <!NOTHING_TO_INLINE!>inline<!> fun <!VERIFICATION_TIMEOUT, VIPER_TEXT!>sameSign<!>(a: Long, b: Long): Boolean = a xor b >= 0L

/**
 * Adds two millisecond values, handling infinite values and ensuring the result stays within the valid range.
 *
 * @return the sum in milliseconds clamped to `[-[MAX_MILLIS], [MAX_MILLIS]]` range, or [INVALID_RAW_VALUE] for invalid operations
 */
private fun Long.<!VIPER_TEXT!>addMillisWithoutOverflow<!>(other: Long): Long {
    postconditions<Long> { result ->
        result == INVALID_RAW_VALUE || (-MAX_MILLIS <= result && result <= MAX_MILLIS)
    }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return when {
        isInfiniteMillis() -> if (other.isFiniteMillis() || sameSign(this, other)) this else INVALID_RAW_VALUE
        other.isInfiniteMillis() -> other
        else -> <!OPAQUE_CALL!>(<!INTEGER_OVERFLOW!>this + other<!>).coerceIn(-MAX_MILLIS, MAX_MILLIS)<!>
    }<!>
}
