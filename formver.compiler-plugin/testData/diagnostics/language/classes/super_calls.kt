// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

open <!VIPER_TEXT!>class Counter<!> {
    open fun <!VIPER_TEXT!>next<!>(x: Int): Int {
        preconditions { x > -100 && x < 100 }
        postconditions<Int> { it >= x }
        return x + 1
    }

    open val start: Int
        <!VIPER_TEXT!>get<!>() = 3
}

interface Coded {
    fun <!VIPER_TEXT!>code<!>(): Int {
        postconditions<Int> { it == 4 }
        return 4
    }
}

<!VIPER_TEXT!>class Stalled<!> : Counter(), Coded {
    override fun <!VIPER_TEXT!>next<!>(x: Int): Int {
        preconditions { x > -100 && x < 100 }
        postconditions<Int> { it >= x }
        return x
    }

    override val start: Int
        <!VIPER_TEXT!>get<!>() = super.start

    override fun <!VIPER_TEXT!>code<!>(): Int {
        postconditions<Int> { it == 4 }
        return super<Coded>.code()
    }

    fun <!VIPER_TEXT!>advanced<!>(x: Int): Int {
        preconditions { x > -100 && x < 100 }
        postconditions<Int> { it >= x }
        return super.next(x)
    }
}
