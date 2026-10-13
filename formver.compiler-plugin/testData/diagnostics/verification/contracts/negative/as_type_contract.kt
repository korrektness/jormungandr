import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

<!VIPER_TEXT!>class IntHolder<!>(val x: Int)

@OptIn(ExperimentalContracts::class)
fun <!VIPER_TEXT!>getX<!>(a: Any): Int? {
    contract {
        <!CONDITIONAL_EFFECT_ERROR!>returnsNotNull() implies (a !is IntHolder)<!>
    }
    return (a as? IntHolder)?.x
}
