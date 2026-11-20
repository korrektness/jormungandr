// FULL_JDK
// WITH_STDLIB
import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>inclusive<!>(x: Int) {
    if (x in 2..36) verify(2 <= x && x <= 36) else verify(x < 2 || x > 36)
}

fun <!VIPER_TEXT!>exclusive<!>(x: Int, n: Int) {
    if (x in 0 until n) verify(0 <= x && x < n) else verify(x < 0 || x >= n)
    if (x in 0..<n) verify(x < n)
}

fun <!VIPER_TEXT!>inclusiveReachesBound<!>(x: Int, n: Int) {
    if (x in 0..n) verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x < n<!>)
}

fun <!VIPER_TEXT!>notIn<!>(x: Int) {
    if (x !in 0..9) verify(x < 0 || x > 9) else verify(0 <= x && x <= 9)
}

fun <!VIPER_TEXT!>longInIntRange<!>(v: Long): Int {
    if (v !in 0..Int.MAX_VALUE) throw IllegalArgumentException()
    return v.toInt()
}

fun <!VIPER_TEXT!>intInLongRange<!>(x: Int, limit: Long) {
    if (x in 0L..limit) verify(x.toLong() <= limit)
}

fun <!VIPER_TEXT!>whenSubject<!>(x: Int): Int {
    postconditions<Int> { result -> result in 0..2 }
    return when (x) {
        in Int.MIN_VALUE..-1 -> 0
        in 0 until 10 -> 1
        else -> 2
    }
}

fun <!VIPER_TEXT!>boundsBeforeElement<!>(): Int {
    var log = 0
    val inside = run { log = log * 10 + 3; 5 } in run { log = log * 10 + 1; 0 }..run { log = log * 10 + 2; 9 }
    verify(inside, log == 123)
    return log
}

fun <!VIPER_TEXT!>atLeast<!>(requested: Int, minimum: Int) {
    val capacity = requested.coerceAtLeast(minimum)
    verify(capacity >= requested, capacity >= minimum)
    verify(capacity == if (requested < minimum) minimum else requested)
}

fun <!VIPER_TEXT!>atLeastIsNotStrict<!>(requested: Int) {
    val capacity = requested.coerceAtLeast(10)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>capacity > requested<!>)
}

fun <!VIPER_TEXT!>atMost<!>(v: Long, max: Long) {
    val c = v.coerceAtMost(max)
    verify(c <= max, c == v || c == max)
}

fun <!VIPER_TEXT!>inLiteralBounds<!>(v: Long): Long {
    postconditions<Long> { result -> -5L <= result && result <= 5L && (v in -5L..5L) implies (result == v) }
    return v.coerceIn(-5L, 5L)
}

fun <!VIPER_TEXT!>inBoundsThrowsWhenEmpty<!>(v: Int, min: Int, max: Int): Int {
    val c = v.coerceIn(min, max)
    verify(min <= max, c in min..max)
    return c
}

fun <!VIPER_TEXT!>coercionInSpec<!>(x: Int): Int {
    postconditions<Int> { result -> result == x.coerceAtLeast(0) }
    return if (x < 0) 0 else x
}
