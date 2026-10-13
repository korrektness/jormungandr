// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>identity<!>(n: Int, k: Int) {
    preconditions {
        0 <= k && k < n
    }
    val a: @Unique IntArray = IntArray(n) { it }
    val x = a[k]
    verify(a.size == n, x == k)
}

// TODO: Stabilize quantified square bounds; checked multiplication can stall verification.
@NeverVerify
fun <!VIPER_TEXT!>squares<!>(n: Int): @Unique IntArray {
    preconditions {
        0 <= n && n <= 46341
    }
    postconditions<IntArray> { r ->
        r.size == n
        forAll<Int> { k -> (0 <= k && k < n) implies (r[k] == k * k) }
    }
    return IntArray(n) { it * it }
}

fun <!VIPER_TEXT!>next<!>(i: Int): Int {
    preconditions { i < 2147483647 }
    return i + 1
}

// The initializer calls a function, so only the size and ownership of the array are known.
fun <!VIPER_TEXT!>impureInitializer<!>(n: Int): Int {
    preconditions {
        n > 0
    }
    val a: @Unique IntArray = IntArray(n) { next(it) }
    return a[n - 1]
}

fun <!VIPER_TEXT!>wrongElement<!>(n: Int) {
    preconditions {
        n > 1
    }
    val a: @Unique IntArray = IntArray(n) { it + 1 }
    val first = a[0]
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>first == 0<!>)
}

fun <!VIPER_TEXT!>negativeSize<!>() {
    val a: @Unique IntArray = <!VIPER_VERIFICATION_ERROR!>IntArray(-1) { it }<!>
}

// The initializer returns from `earlyExit`, so the value it returns is not an element.
fun <!VIPER_TEXT!>earlyExit<!>(n: Int): Int {
    preconditions {
        n > 0
    }
    postconditions<Int> { r -> r == 7 }
    val a: @Unique IntArray = IntArray(n) { return 7 }
    return a[0]
}
