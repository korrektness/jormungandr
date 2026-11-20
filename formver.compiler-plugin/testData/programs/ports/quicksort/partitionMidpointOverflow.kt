// FULL_JDK
/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

import org.jetbrains.kotlin.formver.plugin.*


// IntArray     =============================================================================
private fun <!VIPER_TEXT!>partition<!>(
        array: @Unique @Borrowed IntArray, left: Int, right: Int): Int {
    preconditions { 0 <= left && left <= right && right < array.size }
    var i = left
    var j = right
    val pivot = array[(<!INTEGER_OVERFLOW!>left + right<!>) / 2]
    while (i <= j) {
        while (<!POSSIBLE_INDEX_OUT_OF_BOUND, POSSIBLE_INDEX_OUT_OF_BOUND!>array[i]<!> < pivot)
            i++
        while (<!POSSIBLE_INDEX_OUT_OF_BOUND, POSSIBLE_INDEX_OUT_OF_BOUND!>array[j]<!> > pivot)
            j--
        if (i <= j) {
            val tmp = array[i]
            array[i] = array[j]
            array[j] = tmp
            i++
            j--
        }
    }
    return i
}

private fun <!VIPER_TEXT!>quickSort<!>(
        array: @Unique @Borrowed IntArray, left: Int, right: Int) {
    preconditions { 0 <= left && left <= right && right < array.size }
    val index = partition(array, left, right)
    if (left < <!INTEGER_OVERFLOW!>index - 1<!>)
        <!VIPER_VERIFICATION_ERROR!>quickSort(array, left, index - 1)<!>
    if (index < right)
        <!VIPER_VERIFICATION_ERROR!>quickSort(array, index, right)<!>
}
