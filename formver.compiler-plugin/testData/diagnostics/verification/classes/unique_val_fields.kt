// FULL_JDK
// USE_STDLIB
import org.jetbrains.kotlin.formver.plugin.*

class Inner(var x: Int)
class Outer(val inner: @Unique Inner, var y: Int)

@AlwaysVerify
fun <!VIPER_TEXT!>bumpBoth<!>(o: @Unique @Borrowed Outer) {
    postconditions<Unit> {
        o.inner.x == old(o.inner.x) + 1
        o.y == old(o.y) + 1
    }
    o.inner.x = o.inner.x + 1
    o.y = o.y + 1
}

@AlwaysVerify
fun <!VIPER_TEXT!>callBumpTwice<!>(o: @Unique @Borrowed Outer) {
    preconditions { o.inner.x == 0 && o.y == 0 }
    bumpBoth(o)
    bumpBoth(o)
    verify(o.inner.x == 2 && o.y == 2)
}

fun <!VIPER_TEXT!>bump<!>(i: @Unique @Borrowed Inner) {
    postconditions<Unit> { i.x == old(i.x) + 1 }
    i.x = i.x + 1
}

@AlwaysVerify
fun <!VIPER_TEXT!>borrowThroughVal<!>(o: @Unique @Borrowed Outer) {
    preconditions { o.inner.x == 5 }
    bump(o.inner)
    verify(o.inner.x == 6)
}

@AlwaysVerify
fun <!VIPER_TEXT!>moveOutThroughVal<!>(o: @Unique Outer): @Unique Inner {
    postconditions<Inner> { r -> r.x == 3 }
    o.inner.x = 3
    return o.inner
}

@AlwaysVerify
fun <!VIPER_TEXT!>readThroughSharedVal<!>(o: Outer): Inner {
    return o.inner
}

class Buf(val data: @Unique IntArray, var count: Int)

@AlwaysVerify
fun <!VIPER_TEXT!>push<!>(b: @Unique @Borrowed Buf, v: Int) {
    preconditions { 0 <= b.count && b.count < b.data.size }
    postconditions<Unit> {
        b.count == old(b.count) + 1
        b.data[old(b.count)] == v
        forAll<Int> { k -> (0 <= k && k < b.data.size && k != old(b.count)) implies (b.data[k] == old(b.data[k])) }
    }
    b.data[b.count] = v
    b.count = b.count + 1
}

@AlwaysVerify
fun <!VIPER_TEXT!>pushTwice<!>(b: @Unique @Borrowed Buf) {
    preconditions { b.count == 0 && b.data.size >= 2 }
    push(b, 7)
    push(b, 8)
    verify(b.data[0] == 7)
    verify(b.data[1] == 8)
}

class C(var x: Int)
class B(val c: @Unique C)
class A(val b: @Unique B)

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>xOf<!>(a: @Unique A): Int = a.b.c.x

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>innerXOf<!>(o: @Unique Outer): Int = o.inner.x

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>firstOrZero<!>(b: @Unique Buf): Int = if (b.data.size > 0) b.data[0] else 0

@AlwaysVerify
fun <!VIPER_TEXT!>bumpThroughPure<!>(a: @Unique @Borrowed A) {
    postconditions<Unit> { xOf(a) == old(xOf(a)) + 1 }
    a.b.c.x = a.b.c.x + 1
}

@AlwaysVerify
fun <!VIPER_TEXT!>pushThroughPure<!>(b: @Unique @Borrowed Buf) {
    preconditions { b.count == 0 && b.data.size >= 1 }
    push(b, 7)
    verify(firstOrZero(b) == 7)
}
