// FULL_JDK
/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import org.jetbrains.kotlin.formver.plugin.*

@ExperimentalUnsignedTypes
private fun <!VIPER_TEXT!>partition<!>(
    array: @Unique @Borrowed UIntArray, left: Int, right: Int): Int {
    preconditions { 0 <= left && left <= right && right < array.size }
    postconditions<Int> { result -> left <= result && result <= right + 1 }
    var i = left
    var j = right
    val pivot = array[left + (right - left) / 2]
    while (i <= j) {
        loopInvariants {
            left <= i && i <= right + 1 && left - 1 <= j && j <= right
            (j == right) implies (i == left && array[left + (right - left) / 2] == pivot)
            (i == left) implies (j == right)
            (i > left) implies (array[i - 1] <= pivot)
            (j < right) implies (array[j + 1] >= pivot)
        }
        while (array[i] < pivot) {
            loopInvariants {
                left <= i && i <= right && left <= j && j <= right
                (j == right) implies (i <= left + (right - left) / 2 && array[left + (right - left) / 2] == pivot)
                (j < right) implies (i <= j + 1 && array[j + 1] >= pivot)
                (i == left) implies (j == right)
                (i > left) implies (array[i - 1] <= pivot)
            }
            i++
        }
        while (array[j] > pivot) {
            loopInvariants {
                left <= i && i <= right && left <= j && j <= right
                array[i] >= pivot
                (j == right) implies (i <= left + (right - left) / 2 && array[left + (right - left) / 2] == pivot)
                (i == left) implies (left + (right - left) / 2 <= j && array[left + (right - left) / 2] == pivot)
                (i > left) implies (i - 1 <= j && array[i - 1] <= pivot)
                (j < right) implies (array[j + 1] >= pivot)
            }
            j--
        }
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

@ExperimentalUnsignedTypes
private fun <!VIPER_TEXT!>overflowingMidpoint<!>(array: @Unique @Borrowed UIntArray, left: Int, right: Int): UInt {
    preconditions { 0 <= left && left <= right && right < array.size }
    return array[(<!INTEGER_OVERFLOW!>left + right<!>) / 2]
}

@ExperimentalUnsignedTypes
private fun <!VIPER_TEXT!>missingBound<!>(array: @Unique @Borrowed UIntArray, left: Int, right: Int): UInt {
    preconditions { 0 <= left && 0 <= right && right < array.size }
    return <!POSSIBLE_INDEX_OUT_OF_BOUND!>array[left + (right - left) / 2]<!>
}
