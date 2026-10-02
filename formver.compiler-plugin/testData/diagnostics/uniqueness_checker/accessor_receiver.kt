// FULL_JDK
// WITH_STDLIB
// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

open class Box(var value: Int) {
    val doubled: Int
        get() = value * 2

    var stored: Int = 0
        set(v) {
            field = v + value
        }

    open val tag: Int = 0

    fun member() {}
}

val Box.extension: Int
    get() = value

val @Borrowed Box.borrowedExtension: Int
    get() = 0

fun `field read keeps receiver`(b: @Unique Box): Int {
    val v = b.value
    return v + b.value
}

fun `custom getter moves receiver`(b: @Unique Box): Int {
    val d = b.doubled
    return d + <!INVALID_MOVED_ACCESS!>b<!>.value
}

fun `custom setter moves receiver`(b: @Unique Box): Int {
    b.stored = 1
    return <!INVALID_MOVED_ACCESS!>b<!>.value
}

fun `open property moves receiver`(b: @Unique Box): Int {
    val t = b.tag
    return t + <!INVALID_MOVED_ACCESS!>b<!>.value
}

fun `member call moves receiver`(b: @Unique Box): Int {
    b.member()
    return <!INVALID_MOVED_ACCESS!>b<!>.value
}

fun `extension getter moves receiver`(b: @Unique Box): Int {
    val e = b.extension
    return e + <!INVALID_MOVED_ACCESS!>b<!>.value
}

fun `borrowed extension getter keeps receiver`(b: @Unique Box): Int {
    val e = b.borrowedExtension
    return e + b.value
}
