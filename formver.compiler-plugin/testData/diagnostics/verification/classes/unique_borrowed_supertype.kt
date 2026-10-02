// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

open class Base

class Sub : Base() {
    var extra = 0
}

class Cell(var value: Int)

class Rich(val fixed: @Unique Cell, val plain: Int) : Base() {
    var next: @Unique Cell? = null
}

fun <!VIPER_TEXT!>inspect<!>(b: @Unique @Borrowed Base) {
    if (b is Sub) b.extra = 1
}

// The callee writes `extra` through its smart cast, which the caller cannot see: the field is havoced.
fun <!VIPER_TEXT!>subFieldAfterBorrow<!>(sub: @Unique Sub) {
    sub.extra = 0
    inspect(sub)
    verify(<!VIPER_VERIFICATION_ERROR!>sub.extra == 0<!>)
}

// A plain val is heap-independent and immutable: nothing to havoc.
fun <!VIPER_TEXT!>plainValAfterBorrow<!>(rich: @Unique Rich) {
    val p = rich.plain
    inspect(rich)
    verify(rich.plain == p)
}

// The nested predicates of `next` and of the getter value of `fixed` are havoced together with `next`.
fun <!VIPER_TEXT!>nestedAfterBorrow<!>(rich: @Unique Rich) {
    rich.next?.value = 0
    inspect(rich)
    verify(<!VIPER_VERIFICATION_ERROR!>rich.next?.value == 0<!>)
}
