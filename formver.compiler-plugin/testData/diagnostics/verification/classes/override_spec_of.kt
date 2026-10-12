// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

abstract class Counter {
    @SpecOf("step")
    fun stepSpec(n: Int) {
        preconditions { n >= 0 }
        postconditions<Int> { r -> r > n }
    }

    abstract fun <!VIPER_TEXT!>step<!>(n: Int): Int
}

class Incrementer : Counter() {
    override fun <!VIPER_TEXT!>step<!>(n: Int): Int {
        preconditions { n >= -1 }
        postconditions<Int> { r -> r == n + 1 }
        return n + 1
    }
}

class Identity : Counter() {
    override fun <!OVERRIDE_NOT_REFINING, VIPER_TEXT!>step<!>(n: Int): Int {
        postconditions<Int> { r -> r == n }
        return n
    }
}

class Inheriting : Counter() {
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>override fun <!VIPER_TEXT!>step<!>(n: Int): Int {
        return n
    }<!>
}

interface Measure {
    @SpecOf("size")
    fun sizeSpec(limit: Int) {
        preconditions { limit > 0 }
        postconditions<Int> { r -> 0 <= r && r <= limit }
    }

    fun <!VIPER_TEXT!>size<!>(limit: Int): Int
}

class Halving : Measure {
    @SpecOf("size")
    fun halvingSpec(limit: Int) {
        preconditions { limit > 0 }
        postconditions<Int> { r -> r == limit / 2 }
    }

    override fun <!VIPER_TEXT!>size<!>(limit: Int): Int = limit / 2
}

class Positive : Measure {
    @SpecOf("size")
    fun positiveSpec(limit: Int) {
        preconditions { limit > 1 }
        postconditions<Int> { r -> r == 1 }
    }

    override fun <!OVERRIDE_NOT_REFINING, VIPER_TEXT!>size<!>(limit: Int): Int = 1
}
