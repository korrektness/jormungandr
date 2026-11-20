// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>count<!>(vararg xs: Int): Int {
    postconditions<Int> { it == xs.size }
    return xs.size
}

fun <!VIPER_TEXT!>countChars<!>(vararg cs: Char): Int {
    postconditions<Int> { it == cs.size }
    return cs.size
}

fun <T> <!VIPER_TEXT!>countAll<!>(prefix: Int, vararg xs: T): Int {
    preconditions { prefix >= 0 && prefix < 100 && xs.size < 100 }
    postconditions<Int> { it == prefix + xs.size }
    return prefix + xs.size
}

fun <!VIPER_TEXT!>flags<!>(vararg bs: Boolean) {}

fun <!VIPER_TEXT!>elements<!>() {
    val three = count(1, 2, 3)
    val none = count()
    val chars = countChars('a', 'b')
    val strings = countAll(1, "a", "b")
    val omitted = countAll<String>(2)
    flags(true, false)
    verify(three == 3, none == 0, chars == 2, strings == 3, omitted == 2)
}

fun <!VIPER_TEXT!>spreadCopies<!>(a: @Unique IntArray) {
    preconditions { a.size == 2 }
    a[0] = 5
    val whole = count(*a)
    val mixed = count(1, *a, 2, *a)
    verify(whole == 2, mixed == 6, a[0] == 5)
}

fun <!VIPER_TEXT!>genericSpreadCopies<!>(a: @Unique Array<String?>) {
    preconditions { a.size == 1 }
    a[0] = "x"
    val copied = countAll(0, *a)
    verify(copied == 1, a[0] == "x")
}

open <!VIPER_TEXT!>class Bag<!>(vararg xs: Int) {
    val n: Int = xs.size
}

<!VIPER_TEXT!>class EmptyBag<!> : Bag()

<!VIPER_TEXT!>class PairBag<!> : Bag(1, 2)

fun <!VIPER_TEXT!>constructed<!>(a: IntArray) {
    Bag(1, *a)
}
