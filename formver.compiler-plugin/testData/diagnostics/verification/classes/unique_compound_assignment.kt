// FULL_JDK
// USE_STDLIB
import org.jetbrains.kotlin.formver.plugin.*

class Counter(var x: Int)

class Holder(val inner: @Unique Counter)

@AlwaysVerify
fun <!VIPER_TEXT!>plusAssign<!>(c: @Unique @Borrowed Counter) {
    postconditions<Unit> { c.x == old(c.x) + 1 }
    c.x += 1
}

@AlwaysVerify
fun <!VIPER_TEXT!>minusAssign<!>(c: @Unique @Borrowed Counter) {
    postconditions<Unit> { c.x == old(c.x) - 1 }
    c.x -= 1
}

@AlwaysVerify
fun <!VIPER_TEXT!>postIncrement<!>(c: @Unique @Borrowed Counter) {
    postconditions<Unit> { c.x == old(c.x) + 1 }
    c.x++
}

@AlwaysVerify
fun <!VIPER_TEXT!>preIncrement<!>(c: @Unique @Borrowed Counter) {
    postconditions<Unit> { c.x == old(c.x) + 1 }
    ++c.x
}

@AlwaysVerify
fun <!VIPER_TEXT!>postIncrementValue<!>(c: @Unique @Borrowed Counter): Int {
    postconditions<Int> { r -> r == old(c.x) && c.x == old(c.x) + 1 }
    return c.x++
}

@AlwaysVerify
fun <!VIPER_TEXT!>preDecrementValue<!>(c: @Unique @Borrowed Counter): Int {
    postconditions<Int> { r -> r == old(c.x) - 1 && c.x == old(c.x) - 1 }
    return --c.x
}

@AlwaysVerify
fun <!VIPER_TEXT!>fieldPathPlusAssign<!>(h: @Unique @Borrowed Holder) {
    postconditions<Unit> { h.inner.x == old(h.inner.x) + 1 }
    h.inner.x += 1
}

@AlwaysVerify
fun <!VIPER_TEXT!>fieldPathIncrement<!>(h: @Unique @Borrowed Holder) {
    postconditions<Unit> { h.inner.x == old(h.inner.x) + 1 }
    h.inner.x++
}

@AlwaysVerify
fun <!VIPER_TEXT!>localPlusAssign<!>(): Int {
    postconditions<Int> { r -> r == 1 }
    val c: @Unique Counter = Counter(0)
    c.x += 1
    return c.x
}

@AlwaysVerify
fun <!VIPER_TEXT!>localIncrement<!>(): Int {
    postconditions<Int> { r -> r == 2 }
    val c: @Unique Counter = Counter(0)
    c.x++
    ++c.x
    return c.x
}
