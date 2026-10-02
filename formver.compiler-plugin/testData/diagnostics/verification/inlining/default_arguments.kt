// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

inline fun <!VIPER_TEXT!>scaled<!>(n: Int, k: Int = 2, f: (Int) -> Int): Int = f(n * k)

fun <!VIPER_TEXT!>offset<!>(n: Int, by: Int = 1): Int {
    postconditions<Int> { r -> r == n + by }
    return n + by
}

fun <!VIPER_TEXT!>difference<!>(a: Int, b: Int): Int {
    postconditions<Int> { r -> r == a - b }
    return a - b
}

@AlwaysVerify
fun <!VIPER_TEXT!>inlineCallWithDefault<!>(n: Int): Int {
    postconditions<Int> { r -> r == n * 2 + 1 }
    return scaled(n) { it + 1 }
}

@AlwaysVerify
fun <!VIPER_TEXT!>inlineCallWithExplicitArgument<!>(n: Int): Int {
    postconditions<Int> { r -> r == n * 3 + 1 }
    return scaled(n, 3) { it + 1 }
}

@AlwaysVerify
fun <!VIPER_TEXT!>callWithDefault<!>(n: Int): Int {
    postconditions<Int> { r -> r == n + 1 }
    return offset(n)
}

@AlwaysVerify
fun <!VIPER_TEXT!>callWithNamedArgumentsReordered<!>(a: Int, b: Int): Int {
    postconditions<Int> { r -> r == a - b }
    return difference(b = b, a = a)
}

@AlwaysVerify
fun <!VIPER_TEXT!>inlineCallWithDefaultInSpecification<!>(n: Int): Int {
    postconditions<Int> { r -> r == scaled(n) { it + 1 } }
    return n * 2 + 1
}

<!VIPER_VERIFICATION_ERROR!>@AlwaysVerify
fun <!VIPER_TEXT!>wrongInlineCallWithDefault<!>(n: Int): Int {
    postconditions<Int> { r -> r == n * 3 + 1 }
    return scaled(n) { it + 1 }
}<!>

fun <!VIPER_TEXT!>span<!>(lo: Int, hi: Int = lo * 2): Int {
    postconditions<Int> { r -> r == hi - lo }
    return hi - lo
}

inline fun Int.<!VIPER_TEXT!>doubled<!>(k: Int = this, f: (Int) -> Int): Int = f(this + k)

@AlwaysVerify
fun <!VIPER_TEXT!>defaultReadsEarlierParameter<!>(n: Int): Int {
    postconditions<Int> { r -> r == n }
    return span(n + 0)
}

@AlwaysVerify
fun <!VIPER_TEXT!>defaultReadsReceiver<!>(n: Int): Int {
    postconditions<Int> { r -> r == n * 2 }
    return n.doubled { it }
}
