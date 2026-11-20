// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/collections/Collections.kt, List<T?>.binarySearch(element, fromIndex, toIndex) and rangeCheck
// rangeCheck's postcondition states its throw conditions, and binarySearch's postcondition guards its reads with
// the facts rangeCheck establishes. The loop invariant orders its clauses so its reads and `high + 1` are guarded,
// and repeats the sortedness precondition, since a loop keeps only what its invariant states about the unique list
// it reads. `(low + high).ushr(1)` and `-(low + 1)` rely on wrapping Int `+` (the source comment says "safe from
// overflows"), so the function is @WrappingIntArithmetic; the postcondition decodes the insertion point in Long.
// `verify` calls check mid's range and one sortedness instance, and instantiate sortedness at mid in each branch.
// Adaptation: receiver List<T?> with T : Comparable<T> becomes a borrowed List<Int> parameter; compareValues(midVal, element) becomes midVal.compareTo(element).
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

private fun <!VIPER_TEXT!>rangeCheck<!>(size: Int, fromIndex: Int, toIndex: Int) {
    postconditions<Unit> { 0 <= fromIndex && fromIndex <= toIndex && toIndex <= size }
    when {
        fromIndex > toIndex -> throw IllegalArgumentException("fromIndex ($fromIndex) is greater than toIndex ($toIndex).")
        fromIndex < 0 -> throw IndexOutOfBoundsException("fromIndex ($fromIndex) is less than zero.")
        toIndex > size -> throw IndexOutOfBoundsException("toIndex ($toIndex) is greater than size ($size).")
    }
}

/**
 * The list is expected to be sorted into ascending order, otherwise the result is undefined.
 * @return the index of the element, if it is contained in the list within the specified range;
 * otherwise, the inverted insertion point `(-insertion point - 1)`.
 */
@WrappingIntArithmetic
fun <!VIPER_TEXT!>binarySearch<!>(list: @Unique @Borrowed List<Int>, element: Int, fromIndex: Int, toIndex: Int): Int {
    preconditions {
        forAll<Int> { i -> forAll<Int> { j -> (0 <= fromIndex && fromIndex <= i && i <= j && j < toIndex && toIndex <= list.size) implies (list[i] <= list[j]) } }
    }
    postconditions<Int> { result ->
        if (result >= 0) fromIndex <= result && result < toIndex && toIndex <= list.size && list[result] == element
        else 0 <= fromIndex && toIndex <= list.size && fromIndex.toLong() <= -result.toLong() - 1L && -result.toLong() - 1L <= toIndex.toLong() &&
            forAll<Int> { k -> (fromIndex <= k && k.toLong() < -result.toLong() - 1L) implies (list[k] < element) } &&
            forAll<Int> { k -> (-result.toLong() - 1L <= k.toLong() && k < toIndex) implies (list[k] > element) }
    }
    rangeCheck(list.size, fromIndex, toIndex)

    var low = fromIndex
    var high = toIndex - 1

    while (low <= high) {
        loopInvariants {
            0 <= fromIndex && toIndex <= list.size && high < toIndex && fromIndex <= low && low <= high + 1
            forAll<Int> { i -> forAll<Int> { j -> (0 <= fromIndex && fromIndex <= i && i <= j && j < toIndex && toIndex <= list.size) implies (list[i] <= list[j]) } }
            forAll<Int> { k -> (fromIndex <= k && k < low) implies (list[k] < element) }
            forAll<Int> { k -> (high < k && k < toIndex) implies (list[k] > element) }
        }
        val mid = (low + high).ushr(1) // safe from overflows
        verify(low <= mid && mid <= high)
        verify(list[low] <= list[mid] && list[mid] <= list[high])
        val midVal = list.get(mid)
        val cmp = midVal.compareTo(element)

        if (cmp < 0) {
            verify(forAll<Int> { k -> (fromIndex <= k && k <= mid) implies (list[k] <= list[mid]) })
            low = mid + 1
        } else if (cmp > 0) {
            verify(forAll<Int> { k -> (mid <= k && k < toIndex) implies (list[mid] <= list[k]) })
            high = mid - 1
        }
        else
            return mid // key found
    }
    return <!INTEGER_OVERFLOW!>-(low + 1)<!>  // key not found
}
