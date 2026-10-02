// FULL_JDK
// REPLACE_STDLIB_EXTENSIONS
import org.jetbrains.kotlin.formver.plugin.*

class Box(val v: Int)

class Counter(var x: Int)

class Node(val value: Int, var next: @Unique Node?)

class Inner(var x: Int)

class Middle(val inner: @Unique Inner)

class Outer(val middle: @Unique Middle)

@AlwaysVerify
fun <!VIPER_TEXT!>safeLetInPostcondition<!>(o: Box?): Int {
    postconditions<Int> { r -> r == (o?.let { 1 } ?: 0) }
    return if (o == null) 0 else 1
}

@AlwaysVerify
fun <!VIPER_TEXT!>runInPostcondition<!>(n: Int): Int {
    postconditions<Int> { r -> r == run { n + 1 } }
    return n + 1
}

@AlwaysVerify
fun <!VIPER_TEXT!>receiverRunInPostcondition<!>(b: Box): Int {
    postconditions<Int> { r -> r == b.run { v * 2 } }
    return b.v * 2
}

@AlwaysVerify
fun <!VIPER_TEXT!>withInPostcondition<!>(b: Box): Int {
    postconditions<Int> { r -> r == with(b) { v + 3 } }
    return b.v + 3
}

@AlwaysVerify
fun <!VIPER_TEXT!>alsoInPostcondition<!>(n: Int): Int {
    postconditions<Int> { r -> r == n.also { } }
    return n
}

@AlwaysVerify
fun <!VIPER_TEXT!>applyInPostcondition<!>(b: Box): Box {
    postconditions<Box> { r -> r.v == b.apply { }.v }
    return b
}

@AlwaysVerify
fun <!VIPER_TEXT!>takeIfInPostcondition<!>(n: Int): Int {
    postconditions<Int> { r -> r == (n.takeIf { it > 0 } ?: 0) }
    return if (n > 0) n else 0
}

inline fun <!VIPER_TEXT!>twice<!>(n: Int, f: (Int) -> Int): Int = f(f(n))

@AlwaysVerify
fun <!VIPER_TEXT!>userInlineInPostcondition<!>(n: Int): Int {
    postconditions<Int> { r -> r == twice(n) { it + 1 } }
    return n + 2
}

@AlwaysVerify
fun <!VIPER_TEXT!>scopeFunctionsInPrecondition<!>(b: Box, n: Int): Int {
    preconditions {
        n.let { it } > 0
        run { n } > 1
        b.run { v } > 2
        with(b) { v } > 3
        n.also { } > 4
        b.apply { }.v > 5
        (n.takeIf { it > 6 } ?: 7) > 6
        twice(n) { it } > 8
    }
    postconditions<Int> { r -> r > 8 }
    return n
}

@AlwaysVerify
fun <!VIPER_TEXT!>scopeFunctionsInLoopInvariant<!>(b: Box, n: Int): Int {
    preconditions { n >= 0 && b.v >= 0 }
    postconditions<Int> { r -> r == n }
    var i = 0
    while (i < n) {
        loopInvariants {
            i.let { it } <= n
            run { i } >= 0
            b.run { v } >= 0
            with(b) { v } >= 0
            i.also { } <= n
            b.apply { }.v >= 0
            (i.takeIf { it <= n } ?: 0) == i
            twice(i) { it } <= n
        }
        i = i + 1
    }
    return i
}

@AlwaysVerify
fun <!VIPER_TEXT!>scopeFunctionsOverUniqueReceivers<!>(c: @Unique @Borrowed Counter, n: @Unique @Borrowed Node?, o: @Unique @Borrowed Outer): Int {
    preconditions { c.x > 0 }
    postconditions<Int> { r ->
        r == c.let { val t = it.x; t * 2 }
        r == with(c) { x * 2 }
        r == c.run { x * 2 }
        (n?.let { it.value } ?: -1) == (if (n == null) -1 else n.value)
        o.let { it.middle.inner.x } == o.middle.inner.x
    }
    return c.x * 2
}

<!VIPER_VERIFICATION_ERROR!>@AlwaysVerify
fun <!VIPER_TEXT!>wrongLetOverUniqueReceiver<!>(c: @Unique @Borrowed Counter): Int {
    postconditions<Int> { r -> r == c.let { it.x + 1 } }
    return c.x
}<!>

@AlwaysVerify
fun <!VIPER_TEXT!>letInQuantifiers<!>(arr: @Unique @Borrowed IntArray, n: Int) {
    preconditions {
        forAll<Int> { k -> (0 <= k && k < arr.size) implies (arr[k].let { it >= 0 }) }
        exists<Int> { k -> k.let { it == n } && true }
    }
    postconditions<Unit> { forAll<Int> { k -> (0 <= k && k < arr.size) implies (arr[k] >= 0) } }
}

@AlwaysVerify
fun <!VIPER_TEXT!>letInVerify<!>(n: Int, o: Int?) {
    verify(n.let { it + 1 } == n + 1, (o?.let { it + 1 } ?: 0) >= 0 || n == n)
}

@AlwaysVerify
fun <!VERIFICATION_SKIPPED!>sideEffectsInVerify<!>(n: Int): Int {
    var x = 0
    verify(<!PURITY_VIOLATION!>n.let { x = it; true }<!>)
    verify(<!PURITY_VIOLATION!>run { val y = x++; true }<!>)
    verify(<!PURITY_VIOLATION!>n.let { if (it > 0) return 1; true }<!>)
    return 0
}

@AlwaysVerify
fun <!VIPER_TEXT!>letOverOldInPostcondition<!>(c: @Unique @Borrowed Counter) {
    postconditions<Unit> { c.x == old(c.x).let { it + 1 } }
    c.x = c.x + 1
}

@AlwaysVerify
fun <!VIPER_TEXT!>localInLambdaInPostcondition<!>(n: Int): Int {
    postconditions<Int> { r ->
        r == n.let {
            val doubled = it * 2
            doubled + 1
        }
    }
    return n * 2 + 1
}

@AlwaysVerify
fun <!VIPER_TEXT!>initializersInLambdaInPostcondition<!>(b: Box): Int {
    postconditions<Int> { r ->
        r == b.let {
            val v = it.v
            val clamped = if (v > 0) v else 0
            clamped
        }
    }
    return if (b.v > 0) b.v else 0
}

@AlwaysVerify
fun <!VIPER_TEXT!>safeCastInPostcondition<!>(x: Any): Int {
    postconditions<Int> { r -> r == ((x as? Int) ?: 0) }
    return if (x is Int) x else 0
}

<!VIPER_VERIFICATION_ERROR!>@AlwaysVerify
fun <!VIPER_TEXT!>wrongLetInPostcondition<!>(n: Int): Int {
    postconditions<Int> { r -> r == n.let { it + 1 } }
    return n
}<!>

@AlwaysVerify
fun reassignmentInPostcondition(n: Int): Int {
    postconditions<Int> { r ->
        r == run {
            var y = 0
            y = <!UNSUPPORTED_FEATURE!>n<!>
            y
        }
    }
    return n
}

@AlwaysVerify
fun assignmentInLoopInvariant(n: Int) {
    var i = 0
    var seen = 0
    while (i < n) {
        loopInvariants { i.let { seen = <!UNSUPPORTED_FEATURE!>it<!>; it >= 0 } && true }
        i = i + 1
    }
}

@AlwaysVerify
fun earlyReturnInPostcondition(n: Int): Int {
    postconditions<Int> { r -> r == n.let { <!UNSUPPORTED_FEATURE!>if (it < 0) return@let 0<!>; it } }
    return if (n < 0) 0 else n
}

@AlwaysVerify
fun loopInPostcondition(n: Int): Int {
    postconditions<Int> { r ->
        r == run {
            var i = 0
            <!UNSUPPORTED_FEATURE!>while (i < n) i = i + 1<!>
            i
        }
    }
    return n
}

fun <!VIPER_TEXT!>opaque<!>(n: Int): Int = n

@AlwaysVerify
fun methodCallInPostcondition(n: Int): Int {
    postconditions<Int> { r -> r == <!UNSUPPORTED_FEATURE!>opaque(n)<!> }
    return n
}
