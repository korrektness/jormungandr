// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Counter(var x: Int)

class Holder(var inner: @Unique Counter, var tag: Int)

fun <!VIPER_TEXT!>make<!>(v: Int): @Unique Counter {
    postconditions<Counter> { result -> result.x == v }
    return Counter(v)
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>makeInline<!>(v: Int): @Unique Counter = Counter(v)

fun <!VIPER_TEXT!>consume<!>(c: @Unique Counter) {}

fun <!VIPER_TEXT!>conditionalInlineIntoLocal<!>(b: Boolean) {
    val c: @Unique Counter = if (b) makeInline(1) else makeInline(2)
    verify((b && c.x == 1) || (!b && c.x == 2))
}

fun <!VIPER_TEXT!>wrongConditionalInlineIntoLocal<!>(b: Boolean) {
    val c: @Unique Counter = if (b) makeInline(1) else makeInline(2)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>c.x == 1<!>)
}

fun <!VIPER_TEXT!>conditionalValueIntoFieldViaLocal<!>(h: @Unique @Borrowed Holder, b: Boolean) {
    val c: @Unique Counter = if (b) makeInline(1) else makeInline(2)
    h.inner = c
    verify((b && h.inner.x == 1) || (!b && h.inner.x == 2))
}

fun <!VIPER_TEXT!>conditionalConstructorIntoField<!>(h: @Unique @Borrowed Holder, b: Boolean) {
    h.inner = if (b) Counter(1) else Counter(2)
    verify((b && h.inner.x == 1) || (!b && h.inner.x == 2))
}

fun <!VIPER_TEXT!>wrongConditionalConstructorIntoField<!>(h: @Unique @Borrowed Holder, b: Boolean) {
    h.inner = if (b) Counter(1) else Counter(2)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>h.inner.x == 2<!>)
}

fun <!VIPER_TEXT!>whenOfFreshValues<!>(n: Int) {
    preconditions { n < 2147483647 }
    val c: @Unique Counter = when (n) {
        0 -> Counter(0)
        1 -> make(1)
        else -> makeInline(n)
    }
    c.x = c.x + 1
    consume(c)
}

fun <!VIPER_TEXT!>conditionalBlocks<!>(b: Boolean): @Unique Counter {
    var c: @Unique Counter = Counter(0)
    c = if (b) {
        val d: @Unique Counter = makeInline(1)
        d.x = 5
        makeInline(d.x)
    } else {
        Counter(6)
    }
    verify(c.x == 5 || c.x == 6)
    return c
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>conditionalMoveOfOwnedPath<!>(a: @Unique Counter, b: Boolean) {
    val c: @Unique Counter = if (b) a else Counter(1)
    verify(<!UNSUPPORTED_OWNERSHIP!>c.x<!> == c.x)
}
