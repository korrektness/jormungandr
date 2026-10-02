import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import org.jetbrains.kotlin.formver.plugin.AlwaysVerify

interface Foo
class Bar : Foo

@OptIn(ExperimentalContracts::class)
@AlwaysVerify
fun <!VIPER_TEXT!>asOperator<!>(foo: Foo) : Bar {
    contract {
        returns() implies (foo is Bar)
    }
    return foo as Bar
}

@OptIn(ExperimentalContracts::class)
@AlwaysVerify
fun <!VIPER_TEXT!>safeAsOperator<!>(foo: Foo) : Bar? {
    contract {
        returnsNotNull() implies (foo is Bar)
    }
    return foo as Bar?
}

class IntHolder(val x: Int)

@OptIn(ExperimentalContracts::class)
@AlwaysVerify
fun <!VIPER_TEXT!>getX<!>(a: Any): Int? {
    contract {
        returnsNotNull() implies (a is IntHolder)
        returns(null) implies (a !is IntHolder)
    }
    return (a as? IntHolder)?.x
}
