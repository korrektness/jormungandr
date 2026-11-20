// Source: kotlin v2.3.0 libraries/stdlib/native-wasm/src/kotlin/collections/ArrayList.kt,
// ensureExtraCapacity / ensureCapacityInternal, with AbstractList.newCapacity.
// newCapacity has wrapping Int semantics and the postcondition result >= minCapacity (verified in
// NewCapacityWrapping); ensureExtraCapacity relies on `length + n` wrapping negative to reach the
// OutOfMemoryError check. ensureCapacityInternal's postcondition says it returns only for a non-negative minCapacity.
// Adaptation: the list's backing array and length become (backingSize, length) parameters and the
// function returns the backing size after the call instead of reallocating.
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

private const val maxArraySizeA = Int.MAX_VALUE - 8

/** [oldCapacity] and [minCapacity] must be non-negative. */
@WrappingIntArithmetic
internal fun <!VIPER_TEXT!>newCapacityA<!>(oldCapacity: Int, minCapacity: Int): Int {
    preconditions { oldCapacity >= 0; minCapacity >= 0 }
    postconditions<Int> { result -> result >= minCapacity }
    var newCapacity = oldCapacity + (oldCapacity shr 1)
    if (newCapacity - minCapacity < 0)
        newCapacity = minCapacity
    if (newCapacity - maxArraySizeA > 0)
        newCapacity = if (minCapacity > maxArraySizeA) Int.MAX_VALUE else maxArraySizeA
    return newCapacity
}

private fun <!VIPER_TEXT!>ensureCapacityInternal<!>(backingSize: Int, minCapacity: Int): Int {
    preconditions { backingSize >= 0 }
    postconditions<Int> { result -> minCapacity >= 0 && result >= minCapacity && result >= backingSize }
    if (minCapacity < 0) throw OutOfMemoryError()    // overflow
    if (minCapacity > backingSize) {
        return newCapacityA(backingSize, minCapacity)
    }
    return backingSize
}

@WrappingIntArithmetic
/** Ensures that the capacity is at least [length] + [n] (insertAtInternal / addAllInternal growth). */
fun <!VIPER_TEXT!>ensureExtraCapacity<!>(backingSize: Int, length: Int, n: Int): Int {
    preconditions { 0 <= length && length <= backingSize; n >= 0 }
    postconditions<Int> { result -> result.toLong() >= length.toLong() + n.toLong() }
    return ensureCapacityInternal(backingSize, length + n)
}
