// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

inline fun <!VIPER_TEXT!>scaled<!>(n: Int, k: Int = 2, f: (Int) -> Int): Int {
    preconditions { -1000 <= n && n <= 1000 && -1000 <= k && k <= 1000 }
    return f(n * k)
}

fun <!VIPER_TEXT!>offset<!>(n: Int, by: Int = 1): Int {
    preconditions { -1000 <= n && n <= 1000 && -1000 <= by && by <= 1000 }
    postconditions<Int> { r -> r == n + by }
    return n + by
}

fun <!VIPER_TEXT!>difference<!>(a: Int, b: Int): Int {
    preconditions { -1000 <= a && a <= 1000 && -1000 <= b && b <= 1000 }
    postconditions<Int> { r -> r == a - b }
    return a - b
}

fun <!VIPER_TEXT!>inlineCallWithDefault<!>(n: Int): Int {
    preconditions { -1000 <= n && n <= 1000 }
    postconditions<Int> { r -> r == n * 2 + 1 }
    return scaled(n) { it + 1 }
}

fun <!VIPER_TEXT!>inlineCallWithExplicitArgument<!>(n: Int): Int {
    preconditions { -1000 <= n && n <= 1000 }
    postconditions<Int> { r -> r == n * 3 + 1 }
    return scaled(n, 3) { it + 1 }
}

fun <!VIPER_TEXT!>callWithDefault<!>(n: Int): Int {
    preconditions { -1000 <= n && n <= 1000 }
    postconditions<Int> { r -> r == n + 1 }
    return offset(n)
}

fun <!VIPER_TEXT!>callWithNamedArgumentsReordered<!>(a: Int, b: Int): Int {
    preconditions { -1000 <= a && a <= 1000 && -1000 <= b && b <= 1000 }
    postconditions<Int> { r -> r == a - b }
    return difference(b = b, a = a)
}

fun <!VIPER_TEXT!>inlineCallWithDefaultInSpecification<!>(n: Int): Int {
    preconditions { -1000 <= n && n <= 1000 }
    postconditions<Int> { r -> r == scaled(n) { it + 1 } }
    return n * 2 + 1
}

fun <!VIPER_TEXT!>wrongInlineCallWithDefault<!>(n: Int): Int {
    preconditions { -1000 <= n && n <= 1000 }
    postconditions<Int> { r -> r == n * 3 + 1 }
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return scaled(n) { it + 1 }<!>
}

fun <!VIPER_TEXT!>span<!>(lo: Int, hi: Int = lo * 2): Int {
    preconditions { -1000 <= lo && lo <= 1000 && -2000 <= hi && hi <= 2000 }
    postconditions<Int> { r -> r == hi - lo }
    return hi - lo
}

inline fun Int.<!VIPER_TEXT!>doubled<!>(k: Int = this, f: (Int) -> Int): Int {
    preconditions { -1000 <= this && this <= 1000 && -1000 <= k && k <= 1000 }
    return f(this + k)
}

fun <!VIPER_TEXT!>defaultReadsEarlierParameter<!>(n: Int): Int {
    preconditions { -1000 <= n && n <= 1000 }
    postconditions<Int> { r -> r == n }
    return span(n + 0)
}

fun <!VIPER_TEXT!>defaultReadsReceiver<!>(n: Int): Int {
    preconditions { -1000 <= n && n <= 1000 }
    postconditions<Int> { r -> r == n * 2 }
    return n.doubled { it }
}

<!VIPER_TEXT!>class Counter<!>(var x: Int)

inline fun <!VIPER_TEXT!>withCounter<!>(c: @Unique @Borrowed Counter, k: Int = c.x, f: (Int) -> Int): Int = f(k)

fun <!VIPER_TEXT!>counterPlus<!>(c: @Unique @Borrowed Counter, k: Int = c.x): Int {
    preconditions { k < 2147483647 }
    postconditions<Int> { r -> r == k + 1 }
    return k + 1
}

fun <!VIPER_TEXT!>inlineDefaultReadsUniqueField<!>(c: @Unique Counter): Int {
    postconditions<Int> { r -> r == 5 }
    c.x = 4
    return withCounter(c) { it + 1 }
}

fun <!VIPER_TEXT!>defaultReadsUniqueField<!>(c: @Unique Counter): Int {
    postconditions<Int> { r -> r == 5 }
    c.x = 4
    return counterPlus(c)
}

fun <!VIPER_TEXT!>namedArgumentCapturesMutableLocal<!>(): Int {
    postconditions<Int> { r -> r == 3 }
    var x = 1
    return difference(b = x, a = run { x = 9; 4 })
}

@NeverConvert
inline fun <T> retained(n: T, unused: Int = 0, mutate: () -> Unit): T {
    mutate()
    return n
}

fun <!VIPER_TEXT!>inlineArgumentCapturesMutableLocal<!>(): Int {
    postconditions<Int> { r -> r == 1 }
    var x = 1
    return retained(x, 0) { x = 2 }
}

fun <!VIPER_TEXT!>defaultCallCapturesMutableLocal<!>(): Int {
    postconditions<Int> { r -> r == 1 }
    var x = 1
    return retained(x) { x = 2 }
}

fun <!VIPER_TEXT!>inlineArgumentCapturesNullableLocal<!>(): Int? {
    postconditions<Int?> { r -> r == 1 }
    var x: Int? = 1
    return retained(x, 0) { x = null }
}
