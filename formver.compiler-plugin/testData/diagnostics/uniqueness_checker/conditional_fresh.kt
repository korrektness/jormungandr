// UNIQUE_CHECK_ONLY

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

class Counter(var x: Int)

class Holder(var inner: @Unique Counter, var tag: Int)

fun consume(c: @Unique Counter) {}

fun make(v: Int): @Unique Counter = Counter(v)

fun makeShared(v: Int): Counter = Counter(v)

<!NOTHING_TO_INLINE!>inline<!> fun makeInline(v: Int): @Unique Counter = Counter(v)

fun `declare from conditional calls`(b: Boolean) {
    val c: @Unique Counter = if (b) make(1) else make(2)
    consume(c)
}

fun `declare from conditional inline calls`(b: Boolean) {
    val c: @Unique Counter = if (b) makeInline(1) else makeInline(2)
    consume(c)
}

fun `declare from when of constructors`(n: Int) {
    val c: @Unique Counter = when (n) {
        0 -> Counter(0)
        1 -> make(1)
        else -> makeInline(n)
    }
    consume(c)
}

fun `store conditional constructors into field`(h: @Unique @Borrowed Holder, b: Boolean) {
    h.inner = if (b) Counter(1) else Counter(2)
    consume(h.inner)
    h.inner = Counter(3)
}

fun `pass conditional to unique parameter`(b: Boolean) {
    consume(if (b) Counter(1) else make(2))
}

fun `declare from conditional with shared branch`(b: Boolean) {
    val c: @Unique Counter = <!UNIQUENESS_MISMATCH!>if (b) make(1) else makeShared(2)<!>
}

fun `store conditional with shared branch into field`(h: @Unique @Borrowed Holder, b: Boolean) {
    h.inner = <!UNIQUENESS_MISMATCH!>if (b) Counter(1) else makeShared(2)<!>
}
