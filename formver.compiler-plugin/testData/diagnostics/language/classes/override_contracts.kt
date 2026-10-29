// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Cell<!>(var value: Int)

open <!VIPER_TEXT!>class Base<!> {
    open fun <!VIPER_TEXT!>positive<!>(x: Int): Int {
        preconditions { x > 0 && x <= 2147483645 }
        postconditions<Int> { result -> result > 0 }
        return x
    }

    open fun <!VIPER_TEXT!>bump<!>(c: @Unique @Borrowed Cell) {
        preconditions { c.value <= 2147483645 }
        postconditions<Unit> { c.value > old(c.value) }
        c.value = c.value + 1
    }
}

interface AtLeastOne {
    fun <!VIPER_TEXT!>positive<!>(x: Int): Int {
        preconditions { x >= 0 && x <= 2147483645 }
        postconditions<Int> { result -> result >= 1 }
        return x + 1
    }
}

// A weaker precondition and a stronger postcondition refine both overridden declarations.
<!VIPER_TEXT!>class Refining<!> : Base(), AtLeastOne {
    override fun <!VIPER_TEXT!>positive<!>(x: Int): Int {
        preconditions { x >= 0 && x <= 2147483645 }
        postconditions<Int> { result -> result > 1 }
        return x + 2
    }

    override fun <!VIPER_TEXT!>bump<!>(c: @Unique @Borrowed Cell) {
        preconditions { c.value <= 2147483645 }
        postconditions<Unit> { c.value == old(c.value) + 2 }
        c.value = c.value + 2
    }
}

// An override without a specification of its own is verified against the one it overrides.
open <!VIPER_TEXT!>class Inheriting<!> : Base() {
    override fun <!VIPER_TEXT!>positive<!>(x: Int): Int {
        return x + 1
    }
}

// Overriding an inherited specification refines it.
<!VIPER_TEXT!>class Deeper<!> : Inheriting() {
    override fun <!VIPER_TEXT!>positive<!>(x: Int): Int {
        postconditions<Int> { result -> result == x }
        return x
    }
}

fun <!VIPER_TEXT!>callThroughBase<!>(b: Base) {
    val r = b.positive(3)
    verify(r > 0)
}
