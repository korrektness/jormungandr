// FULL_JDK
import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Counter<!>(var x: Int)

fun <!VIPER_TEXT!>ifInPrecondition<!>(flag: Boolean, n: Int): Int {
    preconditions { n == (if (flag) 1 else 0) }
    postconditions<Int> { r -> r == n }
    return if (flag) 1 else 0
}

fun <!VIPER_TEXT!>ifInPostcondition<!>(flag: Boolean): Int {
    postconditions<Int> { r -> r == (if (flag) 1 else 0) }
    return if (flag) 1 else 0
}

fun <!VIPER_TEXT!>whenInPostcondition<!>(n: Int): Int {
    postconditions<Int> { r ->
        r == when {
            n < 0 -> -1
            n == 0 -> 0
            else -> 1
        }
    }
    return if (n < 0) -1 else if (n == 0) 0 else 1
}

fun <!VIPER_TEXT!>ifOverOldInPostcondition<!>(c: @Unique @Borrowed Counter, flag: Boolean) {
    preconditions { flag implies (c.x < 2147483647) }
    postconditions<Unit> { c.x == (if (flag) old(c.x) + 1 else old(c.x)) }
    if (flag) {
        c.x = c.x + 1
    }
}

fun <!VIPER_TEXT!>oldOfIfInPostcondition<!>(c: @Unique @Borrowed Counter, flag: Boolean) {
    preconditions { flag implies (c.x < 2147483647) }
    postconditions<Unit> { old(if (flag) c.x else 0) + 1 == (if (flag) c.x else 1) }
    if (flag) {
        c.x = c.x + 1
    }
}

fun <!VIPER_TEXT!>ifInLoopInvariant<!>(n: Int, flag: Boolean): Int {
    preconditions { n >= 0 }
    postconditions<Int> { r -> r == (if (flag) n else 0) }
    var i = 0
    var sum = 0
    while (i < n) {
        loopInvariants {
            i <= n
            sum == (if (flag) i else 0)
        }
        if (flag) sum = sum + 1
        i = i + 1
    }
    return sum
}

@Pure
fun <!VIPER_TEXT!>clamp<!>(n: Int, bound: Int): Int {
    preconditions { (if (bound > 0) bound else 0) >= 0 }
    postconditions<Int> { r -> r == (if (n > bound) bound else n) }
    return if (n > bound) bound else n
}

fun <!VIPER_TEXT!>callsClamp<!>(n: Int) {
    verify(clamp(n, 5) <= 5)
}

<!VIPER_TEXT!>class Link<!>(val n: Int, val next: Link?)

fun <!VIPER_TEXT!>nullableOperatorsInPostcondition<!>(l: Link, fallback: Int): Int {
    postconditions<Int> { r -> r == (l.next?.n ?: fallback) }
    val next = l.next
    return if (next != null) next.n else fallback
}
