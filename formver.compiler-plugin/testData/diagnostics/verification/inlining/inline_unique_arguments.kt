// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

open class Counter(var x: Int)

class Tagged(x: Int) : Counter(x) {
    var tag = 0
}

class Holder(var inner: @Unique Counter, var count: Int)

class TaggedHolder(var inner: @Unique Tagged)

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>setX<!>(c: @Unique @Borrowed Counter, v: Int) {
    c.x = v
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>setShared<!>(c: @Borrowed Counter, v: Int) {
    c.x = v
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>consume<!>(c: @Unique Counter): Int = c.x

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>successor<!>(c: @Unique Counter): @Unique Counter {
    c.x = c.x + 1
    return c
}

inline fun <!VIPER_TEXT!>borrowThen<!>(c: @Unique @Borrowed Counter, f: () -> Unit) {
    c.x = 1
    f()
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>setXTwice<!>(c: @Unique @Borrowed Counter) {
    setX(c, 1)
    setX(c, 2)
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>countTo<!>(c: @Unique @Borrowed Counter, n: Int): Int {
    c.x = 0
    var i = 0
    while (i < n) {
        loopInvariants { i >= 0 && c.x == i }
        c.x = c.x + 1
        i = i + 1
    }
    return i
}

<!NOTHING_TO_INLINE!>inline<!> fun <!VIPER_TEXT!>countShared<!>(c: @Borrowed Counter, n: Int) {
    var i = 0
    while (i < n) {
        c.x = i
        i = i + 1
    }
}

inline fun <!VIPER_TEXT!>giveInner<!>(h: @Unique Holder, f: (Counter) -> Unit) {
    f(h.inner)
}

inline fun <!VIPER_TEXT!>give<!>(c: @Unique Counter, f: (Counter) -> Int): Int = f(c)

fun <!VIPER_TEXT!>fresh<!>(): @Unique Counter {
    postconditions<Counter> { r -> r.x == 3 }
    val c: @Unique Counter = Counter(3)
    return c
}

@AlwaysVerify
fun <!VIPER_TEXT!>pathToBorrowed<!>(h: @Unique @Borrowed Holder) {
    h.count = 2
    setX(h.inner, 5)
    verify(h.inner.x == 5 && h.count == 2)
}

@AlwaysVerify
fun <!VIPER_TEXT!>wrongPathToBorrowed<!>(h: @Unique @Borrowed Holder) {
    setX(h.inner, 5)
    verify(<!VIPER_VERIFICATION_ERROR!>h.inner.x == 0<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>pathToSharedBorrowed<!>(h: @Unique @Borrowed Holder) {
    setShared(h.inner, 5)
    verify(h.inner.x == 5)
}

@AlwaysVerify
fun <!VIPER_TEXT!>pathToConsumed<!>(h: @Unique Holder) {
    h.inner.x = 4
    verify(consume(h.inner) == 4)
}

@AlwaysVerify
fun <!VIPER_TEXT!>freshToConsumed<!>() {
    val r = consume(fresh())
    verify(r == 3)
}

@AlwaysVerify
fun <!VIPER_TEXT!>constructorToConsumed<!>() {
    val r = consume(Counter(2))
    verify(r == 2)
}

@AlwaysVerify
fun <!VIPER_TEXT!>nestedPathToBorrowed<!>(h: @Unique @Borrowed Holder) {
    setXTwice(h.inner)
    verify(h.inner.x == 2)
}

@AlwaysVerify
fun <!VIPER_TEXT!>loopWithPathToBorrowed<!>(h: @Unique @Borrowed Holder, n: Int) {
    val k = countTo(h.inner, n)
    verify(h.inner.x == k)
}

@AlwaysVerify
fun <!VIPER_TEXT!>loopWithPathToSharedBorrowed<!>(h: @Unique @Borrowed Holder, n: Int) {
    countShared(h.inner, n)
}

// The temporary for `h.inner` has the static type `Tagged`, so `tag` is kept across the call.
@AlwaysVerify
fun <!VIPER_TEXT!>subtypeToBorrowed<!>(h: @Unique @Borrowed TaggedHolder) {
    h.inner.tag = 1
    setX(h.inner, 5)
    verify(h.inner.tag == 1 && h.inner.x == 5)
}

@AlwaysVerify
fun <!VIPER_TEXT!>annotatedLambdaParameter<!>(h: @Unique Holder) {
    giveInner(h) { c: @Unique Counter ->
        c.x = 7
        verify(c.x == 7)
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>annotatedBorrowedLambdaParameter<!>(c: @Unique Counter) {
    val r = give(c) { d: @Unique @Borrowed Counter ->
        d.x = 1
        d.x
    }
    verify(r == 1)
}

// An inferred lambda parameter is shared, so the lambda cannot read a `var` property through it.
@AlwaysVerify
fun <!VERIFICATION_SKIPPED!>inferredLambdaParameter<!>(h: @Unique Holder) {
    giveInner(h) {
        it.x = 7
        verify(<!UNSUPPORTED_OWNERSHIP!>it.x<!> == 7)
    }
}

// The lambda assigns `h.inner` while `borrowThen` borrows it, so the borrowed value cannot be handed back.
@AlwaysVerify
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>lambdaReassignsRetainedPath<!>(h: @Unique Holder) {
    <!UNSUPPORTED_OWNERSHIP!>borrowThen(h.inner) { h.inner = Counter(0) }<!>
}

@AlwaysVerify
fun <!VIPER_TEXT!>returnInlineResult<!>(c: @Unique Counter): @Unique Counter {
    postconditions<Counter> { r -> r.x == old(c.x) + 1 }
    return successor(c)
}
