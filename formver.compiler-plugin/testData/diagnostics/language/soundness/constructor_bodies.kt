// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class CheckedPrimary<!>(val value: Int) {
    init {
        require(value > 0)
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
    }
    @Law @Pure fun positive(): Boolean = value > 0
}

inline fun <!VIPER_TEXT!>initializeValue<!>(block: () -> Int): Int = block()

<!VIPER_TEXT!>class CheckedInitializer<!> {
    val value = initializeValue {
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
        1
    }
    @Law @Pure fun positive(): Boolean = value > 0
}

<!VIPER_TEXT!>class CheckedSecondary<!> {
    val value = 1
    constructor()
    constructor(checked: Boolean): this() {
        if (checked) verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
    }
    @Law @Pure fun positive(): Boolean = value > 0
}

<!VIPER_TEXT!>class LawFreePrimary<!>(val value: Int) {
    init {
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
    }
}

<!VIPER_TEXT!>class LawFreeSecondary<!> {
    val value: Int
    constructor(value: Int) {
        this.value = value
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>value > 0<!>)
    }
}
