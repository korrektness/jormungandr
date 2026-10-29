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

fun <!VIPER_TEXT!>clampBoundaryClients<!>() {
    val minLong = Long.MIN_VALUE.clampToInt()
    val maxLong = Long.MAX_VALUE.clampToInt()
    val belowMin = (-2147483649L).clampToInt()
    val atMin = (-2147483648L).clampToInt()
    val aboveMin = (-2147483647L).clampToInt()
    val zero = 0L.clampToInt()
    val belowMax = 2147483646L.clampToInt()
    val atMax = 2147483647L.clampToInt()
    val aboveMax = 2147483648L.clampToInt()
    verify(minLong == Int.MIN_VALUE, maxLong == Int.MAX_VALUE)
    verify(belowMin == Int.MIN_VALUE, atMin == Int.MIN_VALUE)
    verify(aboveMin == -2147483647, zero == 0)
    verify(belowMax == 2147483646, atMax == Int.MAX_VALUE, aboveMax == Int.MAX_VALUE)
    refute(false)
}

// Deliberate endpoint mutant; the full-domain contract remains unchanged.
internal fun Long.<!VIPER_TEXT!>wrongPositiveClamp<!>(): Int {
    postconditions<Int> { result ->
        result.toLong() == if (this > Int.MAX_VALUE) Int.MAX_VALUE.toLong()
        else if (this < Int.MIN_VALUE) Int.MIN_VALUE.toLong() else this
    }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return when {
        this > Int.MAX_VALUE -> Int.MIN_VALUE
        this < Int.MIN_VALUE -> Int.MIN_VALUE
        else -> toInt()
    }<!>
}
