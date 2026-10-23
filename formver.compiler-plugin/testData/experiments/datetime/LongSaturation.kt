// FULL_JDK
// TRUST_REPORT
/*
 * Copyright 2019-2020 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.datetime.internal

import org.jetbrains.kotlin.formver.plugin.*

@Pure
internal fun <!VIPER_TEXT!>productFitsLong<!>(a: Long, b: Long): Boolean {
    preconditions { b != 0L }
    return if (b == -1L) a != Long.MIN_VALUE
    else if (b > 0L) a >= Long.MIN_VALUE / b && a <= Long.MAX_VALUE / b
    else a >= Long.MAX_VALUE / b && a <= Long.MIN_VALUE / b
}

internal fun Long.<!VIPER_TEXT!>clampToInt<!>(): Int {
    postconditions<Int> { result ->
        result.toLong() == if (this > Int.MAX_VALUE) Int.MAX_VALUE.toLong()
        else if (this < Int.MIN_VALUE) Int.MIN_VALUE.toLong() else this
    }
    return when {
            this > Int.MAX_VALUE -> Int.MAX_VALUE
            this < Int.MIN_VALUE -> Int.MIN_VALUE
            else -> toInt()
        }
}

/** Multiplies a long value [a] by a non-zero value [b]. */
@WrappingLongArithmetic
internal fun <!VIPER_TEXT!>safeMultiplyOrClamp<!>(a: Long, b: Long): Long {
    preconditions { b != 0L }
    postconditions<Long> { result ->
        result == if (productFitsLong(a, b)) a * b
        else if ((a > 0) == (b > 0)) Long.MAX_VALUE else Long.MIN_VALUE
    }
    when (b) {
        -1L -> {
            if (a == Long.MIN_VALUE) {
                return Long.MAX_VALUE
            }
            return -a
        }
        1L -> return a
    }
    val total = a * b
    if (total / b != a) {
        // `a` is not zero, or the equality would hold, and `b` is not zero by the requirement
        return if ((a > 0) == (b > 0)) Long.MAX_VALUE else Long.MIN_VALUE
    }
    return total
}
