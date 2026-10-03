// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Cell(var value: Int)

open class Base {
    @AlwaysVerify
    open fun <!VIPER_TEXT!>positive<!>(x: Int): Int {
        preconditions { x > 0 }
        postconditions<Int> { result -> result > 0 }
        return x
    }

    @AlwaysVerify
    open fun <!VIPER_TEXT!>bump<!>(c: @Unique @Borrowed Cell) {
        postconditions<Unit> { c.value > old(c.value) }
        c.value = c.value + 1
    }
}

interface AtLeastOne {
    @AlwaysVerify
    fun <!VIPER_TEXT!>positive<!>(x: Int): Int {
        preconditions { x >= 0 }
        postconditions<Int> { result -> result >= 1 }
        return x + 1
    }
}

// A weaker precondition and a stronger postcondition refine both overridden declarations.
class Refining : Base(), AtLeastOne {
    @AlwaysVerify
    override fun <!VIPER_TEXT!>positive<!>(x: Int): Int {
        preconditions { x >= 0 }
        postconditions<Int> { result -> result > 1 }
        return x + 2
    }

    @AlwaysVerify
    override fun <!VIPER_TEXT!>bump<!>(c: @Unique @Borrowed Cell) {
        postconditions<Unit> { c.value == old(c.value) + 2 }
        c.value = c.value + 2
    }
}

// An override without a specification of its own is verified against the one it overrides.
open class Inheriting : Base() {
    @AlwaysVerify
    override fun <!VIPER_TEXT!>positive<!>(x: Int): Int {
        return x + 1
    }
}

// Overriding an inherited specification refines it.
class Deeper : Inheriting() {
    @AlwaysVerify
    override fun <!VIPER_TEXT!>positive<!>(x: Int): Int {
        postconditions<Int> { result -> result == x }
        return x
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>callThroughBase<!>(b: Base) {
    val r = b.positive(3)
    verify(r > 0)
}
