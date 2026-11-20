// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/text/HexExtensions.kt, parsedByteArrayMaxSize, charsPerSet, wholeElementsPerSet
// The preconditions are the source comment "By contract bytesPerLine and bytesPerGroup are > 0" and the `require`s.
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

internal fun <!VERIFICATION_TIMEOUT, VIPER_TEXT!>parsedByteArrayMaxSize<!>(
    stringLength: Int,
    bytesPerLine: Int,
    bytesPerGroup: Int,
    groupSeparatorLength: Int,
    byteSeparatorLength: Int,
    bytePrefixLength: Int,
    byteSuffixLength: Int
): Int {
    preconditions { bytesPerLine > 0; bytesPerGroup > 0 }
    require(stringLength > 0)

    val charsPerByte = bytePrefixLength + 2L + byteSuffixLength

    val charsPerGroup = charsPerSet(charsPerByte, bytesPerGroup, byteSeparatorLength)

    val charsPerLine = if (bytesPerLine <= bytesPerGroup) {
        charsPerSet(charsPerByte, bytesPerLine, byteSeparatorLength)
    } else {
        val groupsPerLine = bytesPerLine / bytesPerGroup
        var result = charsPerSet(charsPerGroup, groupsPerLine, groupSeparatorLength)
        val bytesPerLastGroupInLine = bytesPerLine % bytesPerGroup
        if (bytesPerLastGroupInLine != 0) {
            result += groupSeparatorLength
            result += charsPerSet(charsPerByte, bytesPerLastGroupInLine, byteSeparatorLength)
        }
        result
    }

    var numberOfChars = stringLength.toLong()

    val wholeLines = wholeElementsPerSet(numberOfChars, charsPerLine, 1)
    numberOfChars -= wholeLines * (charsPerLine + 1)

    val wholeGroupsInLastLine = wholeElementsPerSet(numberOfChars, charsPerGroup, groupSeparatorLength)
    numberOfChars -= wholeGroupsInLastLine * (charsPerGroup + groupSeparatorLength)

    val wholeBytesInLastGroup = wholeElementsPerSet(numberOfChars, charsPerByte, byteSeparatorLength)
    numberOfChars -= wholeBytesInLastGroup * (charsPerByte + byteSeparatorLength)

    val spare = if (numberOfChars > 0L) 1 else 0

    // The number of parsed bytes will always fit into Int, each parsed byte consumes at least 2 chars of the input string.
    return ((wholeLines * bytesPerLine) + (wholeGroupsInLastLine * bytesPerGroup) + wholeBytesInLastGroup + spare).toInt()
}

private fun <!VERIFICATION_TIMEOUT, VIPER_TEXT!>charsPerSet<!>(charsPerElement: Long, elementsPerSet: Int, elementSeparatorLength: Int): Long {
    require(elementsPerSet > 0)
    return (charsPerElement * elementsPerSet) + (elementSeparatorLength * (elementsPerSet - 1L))
}

private fun <!VIPER_TEXT!>wholeElementsPerSet<!>(charsPerSet: Long, charsPerElement: Long, elementSeparatorLength: Int): Long {
    return if (charsPerSet <= 0 || charsPerElement <= 0) 0
    else <!DIVISION_BY_ZERO!>(<!INTEGER_OVERFLOW!>charsPerSet + elementSeparatorLength<!>) / (<!INTEGER_OVERFLOW!>charsPerElement + elementSeparatorLength<!>)<!>
}
