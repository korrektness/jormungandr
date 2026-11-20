// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/io/encoding/Base64.kt, Base64.encodeSize
// Adaptation: the Base64 instance's isMimeScheme and shouldPadOnEncode() become parameters.
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

private const val bytesPerGroup: Int = 3
private const val symbolsPerGroup: Int = 4
private const val mimeLineLength: Int = 76

internal fun <!VIPER_TEXT!>encodeSize<!>(sourceSize: Int, isMimeScheme: Boolean, shouldPadOnEncode: Boolean): Int {
    postconditions<Int> { result -> result >= 0 }
    // includes padding chars if shouldPadOnEncode
    val groups = sourceSize / bytesPerGroup
    val trailingBytes = sourceSize % bytesPerGroup
    var size = <!INTEGER_OVERFLOW!>groups * symbolsPerGroup<!>
    if (trailingBytes != 0) { // trailing symbols
        <!INTEGER_OVERFLOW!>size += if (shouldPadOnEncode) symbolsPerGroup else trailingBytes + 1<!>
    }
    if (size < 0) { // Int overflow
        throw IllegalArgumentException("Input is too big")
    }
    if (isMimeScheme) { // line separators
        <!INTEGER_OVERFLOW!>size += ((size - 1) / mimeLineLength) * 2<!>
    }
    if (size < 0) { // Int overflow
        throw IllegalArgumentException("Input is too big")
    }
    return size
}
