// Source: kotlin v2.3.0 AbstractList.Companion.newCapacity, with Kotlin's wrapping Int + and -.
// The postcondition is not in the KDoc; it is what ArrayList.ensureCapacity relies on.
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

private const val maxArraySizeW = Int.MAX_VALUE - 8

@WrappingIntArithmetic
internal fun <!VIPER_TEXT!>newCapacityWrapping<!>(oldCapacity: Int, minCapacity: Int): Int {
    preconditions { oldCapacity >= 0; minCapacity >= 0 }
    postconditions<Int> { result -> result >= minCapacity }
    var newCapacity = oldCapacity + (oldCapacity shr 1)
    if (newCapacity - minCapacity < 0)
        newCapacity = minCapacity
    if (newCapacity - maxArraySizeW > 0)
        newCapacity = if (minCapacity > maxArraySizeW) Int.MAX_VALUE else maxArraySizeW
    return newCapacity
}
