// Source: kotlin v2.3.0 libraries/stdlib/jvm/src/kotlin/util/UnsignedJVM.kt, ulongCompare, ulongDivide
// Adaptation: ULong parameters and results are their Long bit patterns; `v1 < v2` and `ULong(x) >= ULong(y)`
// on ULong become ulongCompare on the bit patterns, which is what ULong.compareTo calls.
// The quotient correction relies on wrapping Long `-` and `*`, so ulongDivide is @WrappingLongArithmetic.
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

/** Compares [v1] and [v2] as unsigned 64-bit values: negative, zero or positive as v1 <, ==, > v2. */
internal fun <!VIPER_TEXT!>ulongCompare<!>(v1: Long, v2: Long): Int {
    postconditions<Int> { result ->
        if ((v1 >= 0L) == (v2 >= 0L)) ((result < 0) == (v1 < v2) && (result == 0) == (v1 == v2))
        else ((result > 0) == (v1 < 0L) && result != 0)
    }
    return (v1 xor Long.MIN_VALUE).compareTo(v2 xor Long.MIN_VALUE)
}

@WrappingLongArithmetic
/** Divides [v1] by [v2] as unsigned 64-bit values, truncating the result. */
internal fun <!VIPER_TEXT!>ulongDivide<!>(v1: Long, v2: Long): Long {
    preconditions { v2 != 0L }
    postconditions<Long> { result ->
        (v1 >= 0L && v2 > 0L) implies (result == v1 / v2)
    }
    val dividend = v1
    val divisor = v2
    if (divisor < 0) { // i.e., divisor >= 2^63:
        return if (ulongCompare(v1, v2) < 0) 0L else 1L
    }

    // Optimization - use signed division if both dividend and divisor < 2^63
    if (dividend >= 0) {
        return dividend / divisor
    }

    // Otherwise, approximate the quotient, check, and correct if necessary.
    val quotient = ((dividend ushr 1) / divisor) shl 1
    val rem = dividend - quotient * divisor
    return quotient + if (ulongCompare(rem, divisor) >= 0) 1 else 0
}
