// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

open class Base {
    open fun <!VIPER_TEXT!>positive<!>(x: Int): Int {
        preconditions { x > 0 }
        postconditions<Int> { result -> result > 0 }
        return x
    }
}

class StrongerPrecondition : Base() {
    override fun <!OVERRIDE_NOT_REFINING, VIPER_TEXT!>positive<!>(x: Int): Int {
        preconditions { x > 1 }
        postconditions<Int> { result -> result > 0 }
        return x
    }
}

class WeakerPostcondition : Base() {
    override fun <!OVERRIDE_NOT_REFINING, VIPER_TEXT!>positive<!>(x: Int): Int {
        preconditions { x > 0 }
        postconditions<Int> { result -> result >= 0 }
        return x - 1
    }
}

class InheritedViolated : Base() {
    <!VIPER_VERIFICATION_ERROR!>override fun <!VIPER_TEXT!>positive<!>(x: Int): Int {
        return x - 1
    }<!>
}
