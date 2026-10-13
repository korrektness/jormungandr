import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

interface Foo
<!VIPER_TEXT!>class Bar<!> : Foo

@OptIn(ExperimentalContracts::class)
fun <!VIPER_TEXT!>asOperator<!>(foo: Foo) : Bar {
    contract {
        returns() implies (foo is Bar)
    }
    return foo as Bar
}

@OptIn(ExperimentalContracts::class)
fun <!VIPER_TEXT!>safeAsOperator<!>(foo: Foo) : Bar? {
    contract {
        returnsNotNull() implies (foo is Bar)
    }
    return foo as Bar?
}

<!VIPER_TEXT!>class IntHolder<!>(val x: Int)

@OptIn(ExperimentalContracts::class)
fun <!VIPER_TEXT!>getX<!>(a: Any): Int? {
    contract {
        returnsNotNull() implies (a is IntHolder)
        returns(null) implies (a !is IntHolder)
    }
    return (a as? IntHolder)?.x
}
