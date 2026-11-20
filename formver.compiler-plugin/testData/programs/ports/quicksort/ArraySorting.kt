// FULL_JDK
/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

import org.jetbrains.kotlin.formver.plugin.*


// IntArray     =============================================================================
@WrappingIntArithmetic
@PointwiseArrayWrites
private fun <!VIPER_TEXT!>partition<!>(
        array: @Unique @Borrowed IntArray, left: Int, right: Int): Int {
    preconditions { 0 <= left && left <= right && right < array.size }
    postconditions<Int> { result -> left < result && result - 1 <= right }
    var i = left
    var j = right
    val pivot = array[(left + right).ushr(1)]
    while (i <= j) {
        loopInvariants {
            left <= i && i - 1 <= right && left - 1 <= j && j <= right && i - 1 <= j + 1
            forAll<Int> { k -> (left <= k && k < i) implies (array[k] <= pivot) }
            forAll<Int> { k -> (j < k && k <= right) implies (array[k] >= pivot) }
            (i == left && j == right) || (left < i && j < right)
            forAll<Int> { k -> (j == right && k == midpoint(left, right)) implies (array[k] == pivot) }
        }
        while (array[i] < pivot) {
            loopInvariants {
                left <= i && i - 1 <= right && left - 1 <= j && j <= right
                forAll<Int> { k -> (left <= k && k < i) implies (pivot >= array[k]) }
                forAll<Int> { k -> (j < k && k <= right) implies (pivot <= array[k]) }
                (j == right && left <= i && i <= midpoint(left, right)) || (j < right && left < i && i <= j + 1)
                forAll<Int> { k -> (j == right && k == midpoint(left, right)) implies (pivot == array[k]) }
            }
            i++
        }
        while (array[j] > pivot) {
            loopInvariants {
                left <= i && i - 1 <= right && left - 1 <= j && j <= right
                forAll<Int> { m -> (left <= m && m < i) implies (array[m] <= pivot) }
                forAll<Int> { m -> (j < m && m <= right) implies (array[m] >= pivot) }
                (i <= midpoint(left, right) && midpoint(left, right) <= j) || (left < i && i <= j + 1 && j < right)
                forAll<Int> { m -> (i == left && m == midpoint(left, right)) implies (array[m] == pivot) }
                array[i] >= pivot
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

private fun <!VIPER_TEXT!>quickSort<!>(
        array: @Unique @Borrowed IntArray, left: Int, right: Int) {
    preconditions { 0 <= left && left <= right && right < array.size }
    val index = partition(array, left, right)
    if (left < index - 1)
        quickSort(array, left, index - 1)
    if (index < right)
        quickSort(array, index, right)
}

@Pure
private fun <!VIPER_TEXT!>midpoint<!>(left: Int, right: Int): Int {
    preconditions { 0 <= left && left <= right }
    postconditions<Int> { result -> left <= result && result <= right }
    return left + (right - left) / 2
}
