// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/time/Duration.kt, Long.addMillisWithoutOverflow
// Adaptation: isInfiniteMillis and sameSign are inline. The precondition, operands in the millis range, is not in
// the KDoc.
package stdlibports

import org.jetbrains.kotlin.formver.plugin.*

private const val MAX_MILLIS_D = Long.MAX_VALUE / 2
private const val INVALID_RAW_VALUE_D = 0x7FFFFFFFFFFFC0DE

private fun Long.<!VIPER_TEXT!>addMillisWithoutOverflow<!>(other: Long): Long {
    preconditions {
        this in -MAX_MILLIS_D..MAX_MILLIS_D
        other in -MAX_MILLIS_D..MAX_MILLIS_D
    }
    postconditions<Long> { result ->
        result == INVALID_RAW_VALUE_D || result in -MAX_MILLIS_D..MAX_MILLIS_D
    }
    return when {
        this == MAX_MILLIS_D || this == -MAX_MILLIS_D ->
            if ((-MAX_MILLIS_D < other && other < MAX_MILLIS_D) || this xor other >= 0L) this else INVALID_RAW_VALUE_D
        other == MAX_MILLIS_D || other == -MAX_MILLIS_D -> other
        else -> (this + other).coerceIn(-MAX_MILLIS_D, MAX_MILLIS_D)
    }
}
