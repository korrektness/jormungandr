// Source: kotlin v2.3.0 HexExtensions.kt checkFormatLength, with `formatLength !in 0..Int.MAX_VALUE`
// written as the two comparisons it denotes.
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

private fun <!VIPER_TEXT!>checkFormatLengthCompare<!>(formatLength: Long): Int {
    postconditions<Int> { result -> result >= 0 && result.toLong() == formatLength }
    if (formatLength < 0L || formatLength > Int.MAX_VALUE) {
        throw IllegalArgumentException("The resulting string length is too big")
    }
    return formatLength.toInt()
}
