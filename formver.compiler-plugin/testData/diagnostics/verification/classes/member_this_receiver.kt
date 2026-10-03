// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class TomlMultilineString(private val isStringType: Boolean) {
    private var isInMultilineBasic = false
    private var isInMultilineLiteral = false

    @AlwaysVerify @Unique @Borrowed
    fun <!VIPER_TEXT!>trackMultilineString<!>(line: String) {
        preconditions { !(isInMultilineBasic && isInMultilineLiteral) }
        postconditions<Unit> { !(isInMultilineBasic && isInMultilineLiteral) }
        if (isStringType) {
            return
        }
        var i = 0
        while (i <= line.length - 3) {
            loopInvariants { 0 <= i && !(isInMultilineBasic && isInMultilineLiteral) }
            // Stumbled upon a comment, no need to analyze for the rest of the line
            if (!isInMultilineBasic && !isInMultilineLiteral && line[i] == '#') {
                break
            }

            if (!isInMultilineLiteral && isNextThreeQuotes(line, i, '"')) {
                isInMultilineBasic = !isInMultilineBasic
            } else if (!isInMultilineBasic && isNextThreeQuotes(line, i, '\'')) {
                isInMultilineLiteral = !isInMultilineLiteral
            }
            i++
        }
    }

    // The end of the loop body is reachable.
    @AlwaysVerify @Unique @Borrowed
    fun <!VIPER_TEXT!>reachesLoopEnd<!>(n: Int) {
        preconditions { !(isInMultilineBasic && isInMultilineLiteral) }
        var i = 0
        while (i < n) {
            loopInvariants { !(isInMultilineBasic && isInMultilineLiteral) }
            isInMultilineBasic = !isInMultilineLiteral && !isInMultilineBasic
            i++
            verify(<!VIPER_VERIFICATION_ERROR!>false<!>)
        }
    }

    // The code after the loop is reachable.
    @AlwaysVerify @Unique @Borrowed
    fun <!VIPER_TEXT!>reachesLoopExit<!>(n: Int) {
        preconditions { !(isInMultilineBasic && isInMultilineLiteral) }
        var i = 0
        while (i < n) {
            loopInvariants { !(isInMultilineBasic && isInMultilineLiteral) }
            isInMultilineBasic = !isInMultilineLiteral && !isInMultilineBasic
            i++
        }
        verify(<!VIPER_VERIFICATION_ERROR!>false<!>)
    }

    // A shared `this` gives no permission: the write is dropped and the read is unknown.
    @AlwaysVerify
    fun <!VIPER_TEXT!>writeThroughShared<!>() {
        isInMultilineBasic = true
        val basic = isInMultilineBasic
        verify(<!VIPER_VERIFICATION_ERROR!>basic<!>)
    }

    @Pure
    private fun <!VIPER_TEXT!>isNextThreeQuotes<!>(line: String, index: Int, quote: Char): Boolean {
        preconditions { 0 <= index && index + 2 < line.length }
        return line[index] == quote && line[index + 1] == quote && line[index + 2] == quote
    }
}
