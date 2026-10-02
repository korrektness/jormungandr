// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

open class Base(var count: Int)

class Sub(count: Int) : Base(count) {
    var extra = 0
}

fun <!VIPER_TEXT!>keepBase<!>(b: @Unique @Borrowed Base) {}

// An upcast move exposes the Base instance and leaks Sub's own fields.
fun <!VIPER_TEXT!>upcastMove<!>(sub: @Unique Sub): @Unique Base {
    sub.extra = 1
    val b: @Unique Base = sub
    b.count = 2
    keepBase(b)
    return b
}

// A downcast move would need Sub's fields, which a Base path does not hold.
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>downcastMove<!>(base: @Unique Base): @Unique Sub {
    <!UNSUPPORTED_OWNERSHIP!>val s: @Unique Sub = base as Sub<!>
    return s
}
