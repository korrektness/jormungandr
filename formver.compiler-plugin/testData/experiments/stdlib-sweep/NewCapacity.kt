// Source: kotlin v2.3.0 libraries/stdlib/src/kotlin/collections/AbstractList.kt, AbstractList.Companion.newCapacity
package stdlibsweep

import org.jetbrains.kotlin.formver.plugin.*

private const val maxArraySize = Int.MAX_VALUE - 8

/** [oldCapacity] and [minCapacity] must be non-negative. */
internal fun <!VIPER_TEXT!>newCapacity<!>(oldCapacity: Int, minCapacity: Int): Int {
    preconditions { oldCapacity >= 0; minCapacity >= 0 }
    // overflow-conscious
    var newCapacity = <!INTEGER_OVERFLOW!>oldCapacity + (oldCapacity shr 1)<!>
    if (newCapacity - minCapacity < 0)
        newCapacity = minCapacity
    if (newCapacity - maxArraySize > 0)
        newCapacity = if (minCapacity > maxArraySize) Int.MAX_VALUE else maxArraySize
    return newCapacity
}
