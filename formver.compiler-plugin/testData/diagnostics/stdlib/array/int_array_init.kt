// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

@AlwaysVerify
fun <!VIPER_TEXT!>identity<!>(n: Int, k: Int) {
    preconditions {
        0 <= k && k < n
    }
    val a: @Unique IntArray = IntArray(n) { it }
    val x = a[k]
    verify(a.size == n, x == k)
}

@AlwaysVerify
fun <!VIPER_TEXT!>squares<!>(n: Int): @Unique IntArray {
    preconditions {
        n >= 0
    }
    postconditions<IntArray> { r ->
        r.size == n
        forAll<Int> { k -> (0 <= k && k < n) implies (r[k] == k * k) }
    }
    return IntArray(n) { it * it }
}

fun <!VIPER_TEXT!>next<!>(i: Int): Int = i + 1

// The initializer calls a function, so only the size and ownership of the array are known.
@AlwaysVerify
fun <!VIPER_TEXT!>impureInitializer<!>(n: Int): Int {
    preconditions {
        n > 0
    }
    val a: @Unique IntArray = IntArray(n) { next(it) }
    return a[n - 1]
}

@AlwaysVerify
fun <!VIPER_TEXT!>wrongElement<!>(n: Int) {
    preconditions {
        n > 1
    }
    val a: @Unique IntArray = IntArray(n) { it + 1 }
    val first = a[0]
    verify(<!VIPER_VERIFICATION_ERROR!>first == 0<!>)
}

@AlwaysVerify
fun <!VIPER_TEXT!>negativeSize<!>() {
    val a: @Unique IntArray = <!VIPER_VERIFICATION_ERROR!>IntArray(-1) { it }<!>
}

// The initializer returns from `earlyExit`, so the value it returns is not an element.
@AlwaysVerify
fun <!VIPER_TEXT!>earlyExit<!>(n: Int): Int {
    preconditions {
        n > 0
    }
    postconditions<Int> { r -> r == 7 }
    val a: @Unique IntArray = IntArray(n) { return 7 }
    return a[0]
}
