// FULL_JDK
// USE_STDLIB
import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Counter<!>(var x: Int)

<!VIPER_TEXT!>class Holder<!>(val inner: @Unique Counter)

fun <!VIPER_TEXT!>plusAssign<!>(c: @Unique @Borrowed Counter) {
    preconditions { c.x < 2147483647 }
    postconditions<Unit> { c.x == old(c.x) + 1 }
    c.x += 1
}

fun <!VIPER_TEXT!>minusAssign<!>(c: @Unique @Borrowed Counter) {
    preconditions { c.x > -2147483648 }
    postconditions<Unit> { c.x == old(c.x) - 1 }
    c.x -= 1
}

fun <!VIPER_TEXT!>postIncrement<!>(c: @Unique @Borrowed Counter) {
    preconditions { c.x < 2147483647 }
    postconditions<Unit> { c.x == old(c.x) + 1 }
    c.x++
}

fun <!VIPER_TEXT!>preIncrement<!>(c: @Unique @Borrowed Counter) {
    preconditions { c.x < 2147483647 }
    postconditions<Unit> { c.x == old(c.x) + 1 }
    ++c.x
}

fun <!VIPER_TEXT!>postIncrementValue<!>(c: @Unique @Borrowed Counter): Int {
    preconditions { c.x < 2147483647 }
    postconditions<Int> { r -> r == old(c.x) && c.x == old(c.x) + 1 }
    return c.x++
}

fun <!VIPER_TEXT!>preDecrementValue<!>(c: @Unique @Borrowed Counter): Int {
    preconditions { c.x > -2147483648 }
    postconditions<Int> { r -> r == old(c.x) - 1 && c.x == old(c.x) - 1 }
    return --c.x
}

fun <!VIPER_TEXT!>fieldPathPlusAssign<!>(h: @Unique @Borrowed Holder) {
    preconditions { h.inner.x < 2147483647 }
    postconditions<Unit> { h.inner.x == old(h.inner.x) + 1 }
    h.inner.x += 1
}

fun <!VIPER_TEXT!>fieldPathIncrement<!>(h: @Unique @Borrowed Holder) {
    preconditions { h.inner.x < 2147483647 }
    postconditions<Unit> { h.inner.x == old(h.inner.x) + 1 }
    h.inner.x++
}

fun <!VIPER_TEXT!>localPlusAssign<!>(): Int {
    postconditions<Int> { r -> r == 1 }
    val c: @Unique Counter = Counter(0)
    c.x += 1
    return c.x
}

fun <!VIPER_TEXT!>localIncrement<!>(): Int {
    postconditions<Int> { r -> r == 2 }
    val c: @Unique Counter = Counter(0)
    c.x++
    ++c.x
    return c.x
}
