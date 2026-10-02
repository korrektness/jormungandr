// FULL_JDK
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

fun consume(a: @Unique IntArray) {}

fun `int array constructor is unique`() {
    val a: @Unique IntArray = IntArray(3)
}

fun `size does not move the array`() {
    val a: @Unique IntArray = IntArray(3)
    val n = a.size
    val m = a.size + n
    consume(a)
}

fun `size of borrowed array does not move it`(a: @Unique @Borrowed IntArray) {
    var i = 0
    while (i < a.size) {
        i++
    }
}

fun `size of a moved array is a moved access`() {
    val a: @Unique IntArray = IntArray(3)
    consume(a)
    val n = <!INVALID_MOVED_ACCESS!>a<!>.size
}
