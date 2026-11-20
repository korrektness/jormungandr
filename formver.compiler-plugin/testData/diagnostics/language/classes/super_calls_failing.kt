// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun interface IntSource {
    fun <!VIPER_TEXT!>get<!>(): Int
    @GhostObserver @Pure fun <!VIPER_TEXT!>value<!>(): Int = ghostObserver()
}

open <!VIPER_TEXT!>class Counter<!> {
    open fun <!VIPER_TEXT!>next<!>(x: Int): Int {
        preconditions { x > -100 && x < 100 }
        postconditions<Int> { it >= x }
        return x + 1
    }
}

interface Coded {
    fun <!VIPER_TEXT!>code<!>(): Int = 4
}

<!VIPER_TEXT!>class Stalled<!> : Counter(), Coded {
    override fun <!VIPER_TEXT!>next<!>(x: Int): Int {
        preconditions { x > -100 && x < 100 }
        postconditions<Int> { it >= x }
        return x
    }

    fun <!VIPER_TEXT!>beyondContract<!>(x: Int): Int {
        preconditions { x > -100 && x < 100 }
        postconditions<Int> { it == x + 1 }
        <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return super.next(x)<!>
    }

    fun <!VIPER_TEXT!>outOfRange<!>(): Int = <!VIPER_VERIFICATION_ERROR!>super.next(200)<!>

    fun <!VERIFICATION_SKIPPED!>inLambda<!>(): IntSource = IntSource {
        exposes(IntSource::class) { value() == 0 }
        <!UNSUPPORTED_FEATURE!>super<!>.next(1)
    }

    fun <!VIPER_TEXT!>opaqueDefault<!>(): Int {
        postconditions<Int> { it == 4 }
        <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>return super<Coded>.code()<!>
    }
}

open <!VIPER_TEXT!>class Base<!> {
    open fun <!VIPER_TEXT!>f<!>() {}
}

<!VERIFICATION_SKIPPED!>class DuringConstruction<!> : Base() {
    init {
        <!UNSUPPORTED_FEATURE!>super<!>.f()
    }
}

<!VIPER_TEXT!>class UniqueReceiver<!> : Base() {
    @Unique
    fun <!VERIFICATION_SKIPPED!>g<!>() {
        <!UNSUPPORTED_FEATURE!>super<!>.f()
    }
}
