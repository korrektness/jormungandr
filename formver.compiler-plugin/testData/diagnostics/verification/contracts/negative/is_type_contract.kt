import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import org.jetbrains.kotlin.formver.plugin.AlwaysVerify

@Suppress("IMPOSSIBLE_IS_CHECK_WARNING")
@OptIn(ExperimentalContracts::class)
@AlwaysVerify
fun <!VIPER_TEXT!>unverifiableTypeCheck<!>(x: Int?): Boolean {
    contract {
        <!CONDITIONAL_EFFECT_ERROR!>returns() implies (x is Unit)<!>
    }
    return x is String
}

@OptIn(ExperimentalContracts::class)
@AlwaysVerify
fun <!VIPER_TEXT!>nullableNotNonNullable<!>(x: Int?) {
    contract {
        <!CONDITIONAL_EFFECT_ERROR!>returns() implies (x is Int)<!>
    }
}
