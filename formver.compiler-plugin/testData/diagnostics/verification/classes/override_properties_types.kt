import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

open <!VIPER_TEXT!>class Base<!> {
    open val field: Int? = null
}

open <!VIPER_TEXT!>class OpenClassOpenFieldVarDerived<!>: Base() {
    override var field: Int = 0
}

<!VIPER_TEXT!>class FinalClassOpenFieldVarDerived<!>: Base() {
    override var field: Int = 0
}

open <!VIPER_TEXT!>class OpenClassFinalFieldVarDerived<!>: Base() {
    final override var field: Int = 0
}

<!VIPER_TEXT!>class FinalClassFinalFieldValDerived<!>: Base() {
    final override val field: Int = 0
}

@OptIn(ExperimentalContracts::class)
fun <!VIPER_TEXT!>extractInt<!>(base: Base, returnNull: Boolean): Int? {
    contract {
        returns(null) implies returnNull
    }
    return if (returnNull) null
    else if (base is OpenClassOpenFieldVarDerived) base.field
    else if (base is FinalClassOpenFieldVarDerived) base.field
    else if (base is FinalClassFinalFieldValDerived) base.field
    else if (base is OpenClassFinalFieldVarDerived) base.field
    else 0
}
