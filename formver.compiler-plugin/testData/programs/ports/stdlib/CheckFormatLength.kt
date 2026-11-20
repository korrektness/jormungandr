// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/text/HexExtensions.kt, checkFormatLength
package stdlibports

import org.jetbrains.kotlin.formver.plugin.*

private fun <!VIPER_TEXT!>checkFormatLength<!>(formatLength: Long): Int {
    postconditions<Int> { result -> result >= 0 && result.toLong() == formatLength }
    if (formatLength !in 0..Int.MAX_VALUE) {
        throw IllegalArgumentException("The resulting string length is too big: ${<!OPAQUE_CALL!>formatLength.toULong()<!>}")
    }
    return formatLength.toInt()
}
