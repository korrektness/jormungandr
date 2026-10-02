// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Box(var value: Int) {
    val doubled: Int
        get() = value * 2
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>readCustomGetter<!>(b: @Unique Box): Int {
    val d = b.doubled
    return d + <!INVALID_MOVED_ACCESS!>b<!>.value
}

fun <!VIPER_TEXT!>customGetterLast<!>(b: @Unique Box): Int {
    b.value = 1
    return b.doubled
}
