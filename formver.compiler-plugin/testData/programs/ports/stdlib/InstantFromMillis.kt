// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/time/Instant.kt, Instant.Companion.fromEpochMilliseconds
// Adaptation: the function returns the (epochSeconds, nanosecondsOfSecond) pair as two functions instead of
// constructing an Instant; MIN/MAX become their epoch-second constants.
package stdlibports

import org.jetbrains.kotlin.formver.plugin.*

private const val MIN_SECOND_F = -31557014167219200L
private const val MAX_SECOND_F = 31556889864403199L
private const val NANOS_PER_MILLI_F = 1_000_000
private const val MILLIS_PER_SECOND_F = 1_000

/** Every value of [epochMilliseconds] is guaranteed to be representable as an Instant: epochSeconds part. */
fun <!VIPER_TEXT!>fromEpochMillisecondsSeconds<!>(epochMilliseconds: Long): Long {
    postconditions<Long> { result -> MIN_SECOND_F <= result && result <= MAX_SECOND_F }
    val epochSeconds = epochMilliseconds.floorDiv(MILLIS_PER_SECOND_F.toLong())
    return when {
        epochSeconds < MIN_SECOND_F -> MIN_SECOND_F
        epochSeconds > MAX_SECOND_F -> MAX_SECOND_F
        else -> epochSeconds
    }
}

/** Every value of [epochMilliseconds] is guaranteed to be representable as an Instant: nanosecondsOfSecond part. */
fun <!VIPER_TEXT!>fromEpochMillisecondsNanos<!>(epochMilliseconds: Long): Int {
    postconditions<Int> { result -> 0 <= result && result <= 999_999_999 }
    return (epochMilliseconds.mod(MILLIS_PER_SECOND_F.toLong()) * NANOS_PER_MILLI_F).toInt()
}
