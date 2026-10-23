// FULL_JDK
// TRUST_REPORT
package kotlinx.datetime.internal

import org.jetbrains.kotlin.formver.plugin.*

@Pure
internal fun <!VIPER_TEXT!>guidanceProductFits<!>(a: Long, b: Long): Boolean {
    preconditions { b != 0L }
    return if (b == -1L) a != Long.MIN_VALUE
    else if (b > 0L) a >= Long.MIN_VALUE / b && a <= Long.MAX_VALUE / b
    else a >= Long.MAX_VALUE / b && a <= Long.MIN_VALUE / b
}

// Generic proof obligation for the nonsingular multiplication/division path.
// All arithmetic here has ordinary Long values and the selected wrapping semantics.
@WrappingLongArithmetic
internal fun <!VIPER_TEXT!>productDivisionGuidance<!>(a: Long, b: Long) {
    preconditions { b != 0L; b != -1L }
    postconditions<Unit> { ((a * b) / b == a) == guidanceProductFits(a, b) }
    val total = a * b
    val quotient = total / b
    val remainder = total % b
    verify(quotient * b + remainder == total)
    if (total >= 0L) verify(remainder >= 0L)
    else verify(remainder <= 0L)
    if (b > 0L) {
        verify(remainder < b)
        verify(remainder > -b)
    } else {
        verify(remainder > b)
        if (b != Long.MIN_VALUE) verify(remainder < -b)
    }
}
