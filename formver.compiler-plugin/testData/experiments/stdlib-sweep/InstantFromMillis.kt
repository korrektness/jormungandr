// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/time/Instant.kt, Instant.Companion.fromEpochMilliseconds
// Long.mod has no model, so it is called through a stand-in carrying its KDoc contract.
// Adaptation: the function returns the (epochSeconds, nanosecondsOfSecond) pair as two functions instead of
// constructing an Instant; MIN/MAX become their epoch-second constants.
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

private const val MIN_SECOND_F = -31557014167219200L
private const val MAX_SECOND_F = 31556889864403199L
private const val NANOS_PER_MILLI_F = 1_000_000
private const val MILLIS_PER_SECOND_F = 1_000

/** Every value of [epochMilliseconds] is guaranteed to be representable as an Instant: epochSeconds part. */
fun <!VIPER_TEXT!>fromEpochMillisecondsSeconds<!>(epochMilliseconds: Long): Long {
    postconditions<Long> { result -> MIN_SECOND_F <= result && result <= MAX_SECOND_F }
    val epochSeconds = <!OPAQUE_CALL!>epochMilliseconds.floorDiv(MILLIS_PER_SECOND_F.toLong())<!>
    return when {
        epochSeconds < MIN_SECOND_F -> MIN_SECOND_F
        epochSeconds > MAX_SECOND_F -> MAX_SECOND_F
        else -> epochSeconds
    }
}

/**
 * Long.mod's KDoc: the result is either zero or has the same sign as the divisor and has the absolute value
 * less than the absolute value of the divisor.
 */
@NeverVerify
private fun <!VIPER_TEXT!>modStandIn<!>(a: Long, other: Long): Long {
    preconditions { other != 0L }
    postconditions<Long> { result ->
        ((other > 0L) implies (0L <= result && result < other)) && ((other < 0L) implies (other < result && result <= 0L))
    }
    return a.mod(other)
}

/** Every value of [epochMilliseconds] is guaranteed to be representable as an Instant: nanosecondsOfSecond part. */
fun <!VIPER_TEXT!>fromEpochMillisecondsNanos<!>(epochMilliseconds: Long): Int {
    postconditions<Int> { result -> 0 <= result && result <= 999_999_999 }
    return (modStandIn(epochMilliseconds, MILLIS_PER_SECOND_F.toLong()) * NANOS_PER_MILLI_F).toInt()
}
