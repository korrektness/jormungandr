// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/text/StringNumberConversions.kt, String.toIntOrNull(radix)
// JVM digitOf is Character.digit(char.code, radix), stated here by its Javadoc range; checkRadix's
// Character.MIN_RADIX..Character.MAX_RADIX is written as 2..36.
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

/** Returns -1 or a digit value in 0 until radix (java.lang.Character.digit). */
@NeverVerify
private fun <!VIPER_TEXT!>digitOf<!>(char: Char, radix: Int): Int {
    postconditions<Int> { result -> result == -1 || (0 <= result && result < radix) }
    return Character.digit(char.code, radix)
}

private fun <!VIPER_TEXT!>checkRadix<!>(radix: Int): Int {
    postconditions<Int> { result -> 2 <= radix && radix <= 36 && result == radix }
    if (radix < 2 || radix > 36) {
        throw IllegalArgumentException("radix $radix was not in valid range")
    }
    return radix
}

/**
 * Parses the string as an [Int] number and returns the result
 * or `null` if the string is not a valid representation of a number.
 * @throws IllegalArgumentException when [radix] is not a valid radix for string to number conversion.
 */
public fun String.<!VIPER_TEXT!>toIntOrNull<!>(radix: Int): Int? {
    checkRadix(radix)

    val length = this.length
    if (length == 0) return null

    val start: Int
    val isNegative: Boolean
    val limit: Int

    val firstChar = this[0]
    if (firstChar < '0') {  // Possible leading sign
        if (length == 1) return null  // non-digit (possible sign) only, no digits after

        start = 1

        if (firstChar == '-') {
            isNegative = true
            limit = Int.MIN_VALUE
        } else if (firstChar == '+') {
            isNegative = false
            limit = -Int.MAX_VALUE
        } else
            return null
    } else {
        start = 0
        isNegative = false
        limit = -Int.MAX_VALUE
    }


    val limitForMaxRadix = (-Int.MAX_VALUE) / 36

    var limitBeforeMul = limitForMaxRadix
    var result = 0
    for (i in start until length) {
        loopInvariants {
            limit <= result && result <= 0
            limitBeforeMul == limitForMaxRadix || limitBeforeMul == limit / radix
        }
        val digit = digitOf(this[i], radix)

        if (digit < 0) return null
        if (result < limitBeforeMul) {
            if (limitBeforeMul == limitForMaxRadix) {
                limitBeforeMul = limit / radix

                if (result < limitBeforeMul) {
                    return null
                }
            } else {
                return null
            }
        }

        result *= radix

        if (result < limit + digit) return null

        result -= digit
    }

    return if (isNegative) result else -result
}
