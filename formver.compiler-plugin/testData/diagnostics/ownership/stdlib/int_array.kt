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

fun `index read does not move the array`(a: @Unique @Borrowed IntArray) {
    val x = a[0]
    val y = a.get(1)
    val z = a[x + y]
}

fun `index write does not move the array`(a: @Unique @Borrowed IntArray) {
    a[0] = 1
    a.set(1, a[0])
    a[a[1]] = a[0]
}

fun `compound index assignment does not move the array`(a: @Unique @Borrowed IntArray) {
    a[0] += 1
    a[1] -= a[0]
}

fun `index increment does not move the array`(a: @Unique @Borrowed IntArray) {
    a[0]++
    --a[1]
    val x = a[0]++
}

fun `indexing an array then consuming it`() {
    val a: @Unique IntArray = IntArray(3)
    a[0] = 1
    a[1]++
    val x = a[0]
    consume(a)
}

fun `indexing a moved array is a moved access`() {
    val a: @Unique IntArray = IntArray(3)
    consume(a)
    val x = <!INVALID_MOVED_ACCESS!>a<!>[0]
}

fun `compound assignment to a moved array is a moved access`() {
    val a: @Unique IntArray = IntArray(3)
    consume(a)
    <!INVALID_MOVED_ACCESS!>a<!>[0] += 1
}
