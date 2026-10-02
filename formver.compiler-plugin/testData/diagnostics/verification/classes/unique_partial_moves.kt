// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Box(var value: Int)

class A(var count: Int, var x: @Unique Box)

class B(var y: @Unique A)

@AlwaysVerify
fun <!VIPER_TEXT!>consume<!>(b: @Unique Box) {}

@AlwaysVerify
fun <!VIPER_TEXT!>fresh<!>(): @Unique Box {
    val b: @Unique Box = Box(0)
    return b
}

// After the first `if`, `b.y.x` is moved on one arm only, so the merge holds `b` and `b.y` open with a hole there.
@AlwaysVerify
fun <!VIPER_TEXT!>moveInBranch<!>(b: @Unique B, c: Boolean, d: Boolean): @Unique B {
    b.y.count = 1
    if (c) consume(b.y.x)
    if (d) {
        b.y.x = fresh()
    } else {
        b.y.x = fresh()
    }
    verify(b.y.count == 1)
    return b
}

@AlwaysVerify
fun <!VIPER_TEXT!>refillEach<!>(b: @Unique @Borrowed B, n: Int) {
    var i = 0
    while (i < n) {
        consume(b.y.x)
        b.y.count = i
        b.y.x = fresh()
        i = i + 1
    }
}

// The hole at `b.y.x` stays open across the loop, whose head holds `b` and `b.y` open around it.
@AlwaysVerify
fun <!VIPER_TEXT!>holeAcrossLoop<!>(b: @Unique B, n: Int): @Unique B {
    consume(b.y.x)
    var i = 0
    while (i < n) {
        b.y.count = i
        i = i + 1
    }
    b.y.x = fresh()
    return b
}

// Moving `b.y` while it is open with a hole moves the open subtree, hole included.
@AlwaysVerify
fun <!VIPER_TEXT!>moveOpenSubtree<!>(b: @Unique B): @Unique A {
    consume(b.y.x)
    val a: @Unique A = b.y
    a.count = 2
    a.x = fresh()
    return a
}

fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>forgetsToRefill<!>(b: @Unique @Borrowed B, n: Int) <!EXIT_UNIQUENESS_INCONSISTENCY!>{
    var i = 0
    while (i < n) {
        consume(<!INVALID_MOVED_ACCESS!>b.y.x<!>)
        i = i + 1
    }
}<!>

@Pure
@AlwaysVerify
fun <!VIPER_TEXT!>countOf<!>(a: @Unique A): Int = a.count

// Passing `b.y` while the field `x` under it is moved out lets a value with a moved field escape.
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>verifyThroughHole<!>(b: @Unique B): @Unique B {
    consume(b.y.x)
    verify(countOf(<!ESCAPE_UNIQUENESS_INCONSISTENCY!>b.y<!>) == countOf(<!ESCAPE_UNIQUENESS_INCONSISTENCY!>b.y<!>))
    b.y.x = fresh()
    return b
}

// The invariant reads through `b`, which the loop head holds open around the hole at `b.y.x`.
fun <!VERIFICATION_SKIPPED, VIPER_TEXT!>invariantThroughHole<!>(b: @Unique B, n: Int): @Unique B {
    consume(b.y.x)
    var i = 0
    <!UNSUPPORTED_OWNERSHIP!>while (i < n) {
        loopInvariants { b.y.count == b.y.count }
        i = i + 1
    }<!>
    b.y.x = fresh()
    return b
}
