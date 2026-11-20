// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/text/HexExtensions.kt, formattedStringLength, checkFormatLength
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

private fun <!VERIFICATION_TIMEOUT, VIPER_TEXT!>formattedStringLength<!>(
    numberOfBytes: Int,
    byteSeparatorLength: Int,
    bytePrefixLength: Int,
    byteSuffixLength: Int
): Int {
    require(numberOfBytes > 0)

    val charsPerByte = 2L + bytePrefixLength + byteSuffixLength + byteSeparatorLength
    val formatLength = numberOfBytes * charsPerByte - byteSeparatorLength
    return checkFormatLength(formatLength)
}

internal fun <!VERIFICATION_TIMEOUT, VIPER_TEXT!>formattedStringLength<!>(
    numberOfBytes: Int,
    bytesPerLine: Int,
    bytesPerGroup: Int,
    groupSeparatorLength: Int,
    byteSeparatorLength: Int,
    bytePrefixLength: Int,
    byteSuffixLength: Int
): Int {
    preconditions { bytesPerLine > 0; bytesPerGroup > 0 }
    require(numberOfBytes > 0)
    // By contract bytesPerLine and bytesPerGroup are > 0

    val lineSeparators = (numberOfBytes - 1) / bytesPerLine
    val groupSeparators = run {
        val groupSeparatorsPerLine = (bytesPerLine - 1) / bytesPerGroup
        val bytesInLastLine = (numberOfBytes % bytesPerLine).let { if (it == 0) bytesPerLine else it }
        val groupSeparatorsInLastLine = (bytesInLastLine - 1) / bytesPerGroup
        lineSeparators * groupSeparatorsPerLine + groupSeparatorsInLastLine
    }
    val byteSeparators = numberOfBytes - 1 - lineSeparators - groupSeparators

    val formatLength: Long = lineSeparators.toLong() /* * lineSeparator.length = 1 */ +
            groupSeparators.toLong() * groupSeparatorLength.toLong() +
            byteSeparators.toLong() * byteSeparatorLength.toLong() +
            numberOfBytes.toLong() * (bytePrefixLength.toLong() + 2L + byteSuffixLength.toLong())

    return checkFormatLength(formatLength)
}

private fun <!VIPER_TEXT!>checkFormatLength<!>(formatLength: Long): Int {
    postconditions<Int> { result -> result >= 0 && result.toLong() == formatLength }
    if (<!OPAQUE_CALL!>formatLength !in <!OPAQUE_CALL!>0..Int.MAX_VALUE<!><!>) {
        throw IllegalArgumentException("The resulting string length is too big: ${<!OPAQUE_CALL!>formatLength.toULong()<!>}")
    }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return <!INTEGER_OVERFLOW!>formatLength.toInt()<!><!>
}
