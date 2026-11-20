// Source: kotlin v2.3.0 Duration.kt Long.addMillisWithoutOverflow, with operands in the millis range
// (precondition not in the KDoc) and coerceIn written out as its documented definition.
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

private const val MAX_MILLIS_D = Long.MAX_VALUE / 2
private const val INVALID_RAW_VALUE_D = 0x7FFFFFFFFFFFC0DE

private fun Long.<!VIPER_TEXT!>coerceInD<!>(minimumValue: Long, maximumValue: Long): Long {
    preconditions { minimumValue <= maximumValue }
    postconditions<Long> { result -> minimumValue <= result && result <= maximumValue }
    if (this < minimumValue) return minimumValue
    if (this > maximumValue) return maximumValue
    return this
}

private fun Long.<!VIPER_TEXT!>addMillisDomain<!>(other: Long): Long {
    preconditions {
        -MAX_MILLIS_D <= this && this <= MAX_MILLIS_D
        -MAX_MILLIS_D <= other && other <= MAX_MILLIS_D
    }
    postconditions<Long> { result ->
        result == INVALID_RAW_VALUE_D || (-MAX_MILLIS_D <= result && result <= MAX_MILLIS_D)
    }
    return when {
        this == MAX_MILLIS_D || this == -MAX_MILLIS_D ->
            if ((-MAX_MILLIS_D < other && other < MAX_MILLIS_D) || this xor other >= 0L) this else INVALID_RAW_VALUE_D
        other == MAX_MILLIS_D || other == -MAX_MILLIS_D -> other
        else -> (this + other).coerceInD(-MAX_MILLIS_D, MAX_MILLIS_D)
    }
}
