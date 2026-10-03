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
    @AlwaysVerify
    override fun <!VIPER_TEXT!>step<!>(n: Int): Int {
        preconditions { n >= -1 }
        postconditions<Int> { r -> r == n + 1 }
        return n + 1
    }
}

class Identity : Counter() {
    @AlwaysVerify
    override fun <!OVERRIDE_NOT_REFINING, VIPER_TEXT!>step<!>(n: Int): Int {
        postconditions<Int> { r -> r == n }
        return n
    }
}

class Inheriting : Counter() {
    <!VIPER_VERIFICATION_ERROR!>@AlwaysVerify
    override fun <!VIPER_TEXT!>step<!>(n: Int): Int {
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

    @AlwaysVerify
    override fun <!VIPER_TEXT!>size<!>(limit: Int): Int = limit / 2
}

class Positive : Measure {
    @SpecOf("size")
    fun positiveSpec(limit: Int) {
        preconditions { limit > 1 }
        postconditions<Int> { r -> r == 1 }
    }

    @AlwaysVerify
    override fun <!OVERRIDE_NOT_REFINING, VIPER_TEXT!>size<!>(limit: Int): Int = 1
}
