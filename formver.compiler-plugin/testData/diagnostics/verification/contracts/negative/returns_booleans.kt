// FULL_JDK
import kotlin.contracts.contract
import kotlin.contracts.ExperimentalContracts
import org.jetbrains.kotlin.formver.plugin.AlwaysVerify

@OptIn(ExperimentalContracts::class)
@AlwaysVerify
fun <!VIPER_TEXT!>incorrectly_returns_false<!>(): Boolean {
    contract {
        <!UNEXPECTED_RETURNED_VALUE!>returns(true)<!>
    }
    return false
}

@OptIn(ExperimentalContracts::class)
@AlwaysVerify
fun <!VIPER_TEXT!>incorrectly_returns_true<!>(): Boolean {
    contract {
        <!UNEXPECTED_RETURNED_VALUE!>returns(false)<!>
    }
    return true
}
