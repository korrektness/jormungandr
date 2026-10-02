import kotlin.contracts.contract
import kotlin.contracts.ExperimentalContracts
import org.jetbrains.kotlin.formver.plugin.AlwaysVerify

@OptIn(ExperimentalContracts::class)
@AlwaysVerify
fun <!VIPER_TEXT!>returns_null<!>(): Int? {
    contract {
        <!UNEXPECTED_RETURNED_VALUE!>returnsNotNull()<!>
    }
    return null
}
