import kotlin.contracts.contract
import kotlin.contracts.ExperimentalContracts
import org.jetbrains.kotlin.formver.plugin.AlwaysVerify

@AlwaysVerify
fun <!VIPER_TEXT!>without_contract<!>() {}

@OptIn(ExperimentalContracts::class)
@AlwaysVerify
fun <!VIPER_TEXT!>with_contract<!>() {
    contract() {
        returns()
    }
}
