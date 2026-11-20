// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/time/Duration.kt, addValuesMixedRanges, durationOfMillisNormalized,
// durationOfNanos, durationOfMillis. Adaptation: functions return the raw Long instead of wrapping it in Duration;
// one-line private helpers are inline, ranges are written as comparisons, and coerceIn is written out with its
// KDoc contract. addMillisWithoutOverflow has the millis-range precondition and its KDoc postcondition, plus the
// clause that a finite [other] never yields INVALID_RAW_VALUE, which the source comment in addValuesMixedRanges
// relies on. millisToNanos has the range its callers pass as its precondition.
// The postcondition is the representation check Duration.fromRawValue asserts.
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

private const val NANOS_IN_MILLIS_N = 1_000_000
private const val MAX_NANOS_N = Long.MAX_VALUE / 2 / NANOS_IN_MILLIS_N * NANOS_IN_MILLIS_N - 1
private const val MAX_MILLIS_N = Long.MAX_VALUE / 2
private const val MAX_NANOS_IN_MILLIS_N = MAX_NANOS_N / NANOS_IN_MILLIS_N

@Pure
private fun <!VIPER_TEXT!>validRaw<!>(raw: Long): Boolean {
    return if (raw % 2L == 0L) -MAX_NANOS_N <= raw / 2L && raw / 2L <= MAX_NANOS_N
    else -MAX_MILLIS_N <= (raw shr 1) && (raw shr 1) <= MAX_MILLIS_N &&
        ((raw shr 1) < -MAX_NANOS_IN_MILLIS_N || (raw shr 1) > MAX_NANOS_IN_MILLIS_N)
}

private <!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>nanosToMillis<!>(nanos: Long): Long = nanos / NANOS_IN_MILLIS_N
private <!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>millisToNanos<!>(millis: Long): Long {
    preconditions { -MAX_NANOS_IN_MILLIS_N <= millis && millis <= MAX_NANOS_IN_MILLIS_N }
    return millis * NANOS_IN_MILLIS_N
}
private <!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>durationOfNanos<!>(normalNanos: Long): Long = normalNanos shl 1
private <!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>durationOfMillis<!>(normalMillis: Long): Long = (normalMillis shl 1) + 1

private fun <!VIPER_TEXT!>durationOfMillisNormalized<!>(millis: Long): Long {
    postconditions<Long> { result -> validRaw(result) }
    return if (-MAX_NANOS_IN_MILLIS_N <= millis && millis <= MAX_NANOS_IN_MILLIS_N) {
        durationOfNanos(millisToNanos(millis))
    } else {
        durationOfMillis(millis.coerceInN(-MAX_MILLIS_N, MAX_MILLIS_N))
    }
}

private fun Long.<!VIPER_TEXT!>coerceInN<!>(minimumValue: Long, maximumValue: Long): Long {
    preconditions { minimumValue <= maximumValue }
    postconditions<Long> { result -> minimumValue <= result && result <= maximumValue && (result == this || result == minimumValue || result == maximumValue) && ((minimumValue <= this && this <= maximumValue) implies (result == this)) }
    if (this < minimumValue) return minimumValue
    if (this > maximumValue) return maximumValue
    return this
}

private fun <!VIPER_TEXT!>addMillisWithoutOverflowN<!>(a: Long, other: Long): Long {
    preconditions { -MAX_MILLIS_N <= a && a <= MAX_MILLIS_N; -MAX_MILLIS_N <= other && other <= MAX_MILLIS_N }
    postconditions<Long> { result ->
        (result == 0x7FFFFFFFFFFFC0DE || (-MAX_MILLIS_N <= result && result <= MAX_MILLIS_N)) &&
            ((-MAX_MILLIS_N < other && other < MAX_MILLIS_N) implies (result != 0x7FFFFFFFFFFFC0DE))
    }
    return when {
    a == MAX_MILLIS_N || a == -MAX_MILLIS_N ->
        if ((-MAX_MILLIS_N < other && other < MAX_MILLIS_N) || a xor other >= 0L) a else 0x7FFFFFFFFFFFC0DE
    other == MAX_MILLIS_N || other == -MAX_MILLIS_N -> other
    else -> (a + other).coerceInN(-MAX_MILLIS_N, MAX_MILLIS_N)
    }
}

/** thisMillis is a millisecond-unit Duration value; otherNanos a nanosecond-unit one. */
private fun <!VIPER_TEXT!>addValuesMixedRanges<!>(thisMillis: Long, otherNanos: Long): Long {
    preconditions {
        -MAX_MILLIS_N <= thisMillis && thisMillis <= MAX_MILLIS_N
        -MAX_NANOS_N <= otherNanos && otherNanos <= MAX_NANOS_N
    }
    postconditions<Long> { result -> validRaw(result) }
    val otherMillis = nanosToMillis(otherNanos)
    val resultMillis = addMillisWithoutOverflowN(thisMillis, otherMillis)
    return if (-MAX_NANOS_IN_MILLIS_N <= resultMillis && resultMillis <= MAX_NANOS_IN_MILLIS_N) {
        val otherNanoRemainder = otherNanos - millisToNanos(otherMillis)
        durationOfNanos(millisToNanos(resultMillis) + otherNanoRemainder)
    } else {
        durationOfMillis(resultMillis)
    }
}
