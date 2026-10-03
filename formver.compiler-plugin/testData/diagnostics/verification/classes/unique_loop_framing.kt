// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

class Counter(var x: Int)

class Holder(var inner: @Unique Counter, var count: Int)

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

inline fun <!VIPER_TEXT!>repeat<!>(n: Int, f: () -> Unit) {
    var i = 0
    while (i < n) {
        f()
        i = i + 1
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>loopKeepsOtherRoot<!>(a: @Unique Counter, c: @Unique Counter, n: Int) {
    a.x = 1
    var i = 0
    while (i < n) {
        c.x = i
        i = i + 1
    }
    verify(a.x == 1)
}

@AlwaysVerify
fun <!VIPER_TEXT!>returnFromLoopKeepsOtherRoot<!>(a: @Unique @Borrowed Counter, c: @Unique Counter, n: Int) {
    postconditions<Unit> { a.x == 1 }
    a.x = 1
    var i = 0
    while (i < n) {
        c.x = i
        if (i == 2) return
        i = i + 1
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>breakFromNestedLoopKeepsOtherRoot<!>(a: @Unique Counter, c: @Unique Counter, n: Int) {
    a.x = 1
    var i = 0
    outer@ while (i < n) {
        var j = 0
        while (j < n) {
            c.x = j
            if (j == 2) break@outer
            j = j + 1
        }
        i = i + 1
    }
    verify(a.x == 1)
}

// The loop writes `a`, so it holds `a` and its value is unknown after the loop.
@AlwaysVerify
fun <!VIPER_TEXT!>loopWritingRootForgetsIt<!>(a: @Unique Counter, n: Int) {
    a.x = 1
    var i = 0
    while (i < n) {
        a.x = i
        i = i + 1
    }
    verify(<!VIPER_VERIFICATION_ERROR!>a.x == 1<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>invariantNamesRoot<!>(a: @Unique Counter, n: Int) {
    a.x = 1
    var i = 0
    while (i < n) {
        loopInvariants { a.x == 1 }
        i = i + 1
    }
    verify(a.x == 1)
}

@AlwaysVerify
fun <!VIPER_TEXT!>inlinedLoopKeepsOtherRoot<!>(a: @Unique Counter, c: @Unique Counter, n: Int) {
    a.x = 1
    countTo(c, n)
    verify(a.x == 1)
}

@AlwaysVerify
fun <!VIPER_TEXT!>inlinedLoopKeepsSiblingField<!>(h: @Unique Holder, n: Int) {
    h.count = 2
    countTo(h.inner, n)
    verify(h.count == 2 && h.inner.x >= 0)
}

// The lambda inlined in the loop writes `a`, so the loop holds `a`.
@AlwaysVerify
fun <!VIPER_TEXT!>lambdaInLoopReachesRoot<!>(a: @Unique Counter, n: Int) {
    a.x = 1
    repeat(n) { a.x = 2 }
    verify(<!VIPER_VERIFICATION_ERROR!>a.x == 1<!>)
}
