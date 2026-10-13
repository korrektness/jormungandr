// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

abstract class Counter {
    @SpecOf("step")
    fun stepSpec(n: Int) {
        preconditions { n >= 0 }
        postconditions<Int> { r -> r > n }
    }

    abstract fun <!VIPER_TEXT!>step<!>(n: Int): Int

    @SpecOf("step")
    fun stepTwiceSpec(n: Int, m: Int) {
        preconditions {
            -1000000000 <= n && n <= 1000000000
            -1000000000 <= m && m <= 1000000000
        }
        postconditions<Int> { r -> r >= n + m }
    }

    abstract fun <!VIPER_TEXT!>step<!>(n: Int, m: Int): Int
}

interface Measure {
    @SpecOf("size")
    fun sizeSpec(limit: Int) {
        preconditions { limit > 0 }
        postconditions<Int> { r -> 0 <= r && r <= limit }
    }

    fun <!VIPER_TEXT!>size<!>(limit: Int): Int
}

<!VIPER_TEXT!>class Doubler<!> {
    @SpecOf("twice")
    fun twiceSpec(x: Int) {
        preconditions { 0 <= x && x <= 1073741823 }
        postconditions<Int> { r -> r == 2 * x }
    }

    fun <!VIPER_TEXT!>twice<!>(x: Int): Int = x + x
}

fun <!VIPER_TEXT!>stepRelies<!>(c: Counter): Int {
    val r = c.step(3)
    verify(r > 3)
    return r
}

fun <!VIPER_TEXT!>stepBreaksPrecondition<!>(c: Counter): Int {
    return <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>c.step(-1)<!>
}

fun <!VIPER_TEXT!>sizeRelies<!>(m: Measure): Int {
    val r = m.size(10)
    verify(r <= 10)
    return r
}

fun <!VIPER_TEXT!>twiceRelies<!>(d: Doubler): Int {
    val r = d.twice(4)
    verify(r == 8)
    return r
}
