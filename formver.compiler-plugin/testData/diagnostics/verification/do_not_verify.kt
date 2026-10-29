// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.NeverVerify
import org.jetbrains.kotlin.formver.plugin.NeverConvert
import org.jetbrains.kotlin.formver.plugin.verify
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
    val s = 1.0
    return 0
}

// The signature is still embedded.
@NeverVerify
fun <!VIPER_TEXT!>unsupportedSignature<!>(): Double = 1.0

@NeverConvert
fun excludedOuter() {
    fun <!VIPER_TEXT!>selectedInner<!>(): Unit {
        verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
    }
}
