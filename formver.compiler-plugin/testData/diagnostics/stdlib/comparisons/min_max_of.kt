// FULL_JDK
// WITH_STDLIB

import org.jetbrains.kotlin.formver.plugin.*

@AlwaysVerify
fun <!VIPER_TEXT!>minOfTwo<!>(a: Int, b: Int): Int {
    postconditions<Int> { r -> r <= a && r <= b && (r == a || r == b) }
    return minOf(a, b)
}

@AlwaysVerify
fun <!VIPER_TEXT!>minOfThree<!>(a: Int, b: Int, c: Int): Int {
    postconditions<Int> { r -> r <= a && r <= b && r <= c && (r == a || r == b || r == c) }
    return minOf(a, b, c)
}

@AlwaysVerify
fun <!VIPER_TEXT!>maxOfTwo<!>(a: Int, b: Int): Int {
    postconditions<Int> { r -> r >= a && r >= b && (r == a || r == b) }
    return maxOf(a, b)
}

@AlwaysVerify
fun <!VIPER_TEXT!>maxOfThree<!>(a: Int, b: Int, c: Int): Int {
    postconditions<Int> { r -> r >= a && r >= b && r >= c && (r == a || r == b || r == c) }
    return maxOf(a, b, c)
}
