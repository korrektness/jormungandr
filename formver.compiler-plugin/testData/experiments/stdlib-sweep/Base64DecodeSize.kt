// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/io/encoding/Base64.kt, Base64.decodeSize
// The loop invariant is `endIndex - index <= symbols <= endIndex - startIndex`.
// Adaptation: isMimeScheme, padSymbol and base64DecodeMap become parameters. The precondition is the
// checkSourceBounds call every caller makes first; the postcondition is what ByteArray(decodeSize) needs.
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

private const val bitsPerByte: Int = 8
private const val bitsPerSymbol: Int = 6

<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>internal fun <!VIPER_TEXT!>decodeSize<!>(
    source: @Unique @Borrowed ByteArray, startIndex: Int, endIndex: Int,
    isMimeScheme: Boolean, padSymbol: Byte, base64DecodeMap: IntArray,
): Int {
    preconditions { 0 <= startIndex && startIndex <= endIndex && endIndex <= source.size; base64DecodeMap.size == 256 }
    postconditions<Int> { result -> result >= 0 }
    var symbols = endIndex - startIndex
    if (symbols == 0) {
        return 0
    }
    if (symbols == 1) {
        throw IllegalArgumentException("Input should have at least 2 symbols for Base64 decoding, startIndex: $startIndex, endIndex: $endIndex")
    }
    if (isMimeScheme) {
        for (index in startIndex until endIndex) {
            loopInvariants { endIndex - index <= symbols && symbols <= endIndex - startIndex }
            val symbol = source[index].toInt() and 0xFF
            val symbolBits = base64DecodeMap[symbol]
            if (symbolBits < 0) {
                if (symbolBits == -2) {
                    symbols -= endIndex - index
                    break
                }
                symbols--
            }
        }
    } else if (source[endIndex - 1] == padSymbol) {
        symbols--
        if (source[endIndex - 2] == padSymbol) {
            symbols--
        }
    }
    return ((symbols.toLong() * bitsPerSymbol) / bitsPerByte).toInt() // conversion due to possible Int overflow
}<!>
