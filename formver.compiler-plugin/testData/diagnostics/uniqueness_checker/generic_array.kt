// FULL_JDK
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.*

class MutableValue(var n: Int)

fun consume(value: @Unique MutableValue) {}
fun consumeArray(arr: @Unique Array<MutableValue?>) {}

fun indexedStoreEscapes(arr: Array<MutableValue?>) {
    val value: @Unique MutableValue = MutableValue(1)
    arr[0] = value
    consume(<!INVALID_MOVED_ACCESS!>value<!>)
}

fun explicitStoreEscapes(arr: Array<MutableValue?>) {
    val value: @Unique MutableValue = MutableValue(1)
    arr.set(0, value)
    consume(<!INVALID_MOVED_ACCESS!>value<!>)
}

fun castStoreEscapes(arr: Array<Any?>) {
    val value: @Unique MutableValue = MutableValue(1)
    arr.set(0, value as Any?)
    consume(<!INVALID_MOVED_ACCESS!>value<!>)
}

@Suppress("UNCHECKED_CAST")
fun <T> genericStoreEscapes(arr: Array<T>) {
    val value: @Unique MutableValue = MutableValue(1)
    arr[0] = value as T
    consume(<!INVALID_MOVED_ACCESS!>value<!>)
}

fun receiverRemainsOwned() {
    val arr: @Unique Array<MutableValue?> = arrayOfNulls<MutableValue>(2)
    val first: @Unique MutableValue = MutableValue(1)
    val second: @Unique MutableValue = MutableValue(2)
    arr[0] = first
    arr.set(1, second)
    val read = arr[0]
    consumeArray(arr)
}

fun unstoredValueRemainsOwned(arr: Array<MutableValue?>) {
    val value: @Unique MutableValue = MutableValue(1)
    arr[0] = null
    arr.set(1, null)
    consume(value)
}
