// Source: kotlin v2.3.0 Base64.encodeSize, with Kotlin's wrapping Int arithmetic. Int `*` has no wrapping
// mode, so `groups * symbolsPerGroup` is written as the Long product truncated to Int, which is its JVM meaning.
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

@WrappingIntArithmetic
@WrappingNarrowing
internal fun <!VIPER_TEXT!>encodeSizeWrapping<!>(sourceSize: Int, isMimeScheme: Boolean, shouldPadOnEncode: Boolean): Int {
    postconditions<Int> { result -> result >= 0 }
    val groups = sourceSize / 3
    val trailingBytes = sourceSize % 3
    var size = (groups.toLong() * 4L).toInt()
    if (trailingBytes != 0) {
        size += if (shouldPadOnEncode) 4 else trailingBytes + 1
    }
    if (size < 0) {
        throw IllegalArgumentException("Input is too big")
    }
    if (isMimeScheme) {
        size += ((size - 1) / 76) * 2
    }
    if (size < 0) {
        throw IllegalArgumentException("Input is too big")
    }
    return size
}
