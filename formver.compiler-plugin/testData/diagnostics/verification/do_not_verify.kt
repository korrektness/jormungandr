import org.jetbrains.kotlin.formver.plugin.NeverVerify
import org.jetbrains.kotlin.formver.plugin.NeverConvert
import kotlin.contracts.contract
import kotlin.contracts.ExperimentalContracts

@NeverVerify
@OptIn(ExperimentalContracts::class)
fun <!VIPER_TEXT!>bad_returns<!>(): Boolean {
    contract {
        returns(true)
    }
    return false
}

@NeverConvert
fun noop() {}

// The body is not converted, so its unsupported constant is not reported.
@NeverVerify
fun <!VIPER_TEXT!>unsupportedBody<!>(): Int {
    val s = 1L
    return 0
}

// The signature is still embedded, so its unsupported return type is reported.
<!UNSUPPORTED_FEATURE!>@NeverVerify
fun unsupportedSignature(): Long = 1L<!>
