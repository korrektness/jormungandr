import org.jetbrains.kotlin.formver.plugin.AlwaysVerify

class Foo
class Bar {
    @AlwaysVerify
    fun <!VIPER_TEXT!>baz<!>(f: Foo) {  }
    @AlwaysVerify
    fun <!VIPER_TEXT!>baz<!>(b: Bar) {  }
}

@AlwaysVerify
fun <!VIPER_TEXT!>fakePrint<!>(b: Bar) {  }
@AlwaysVerify
fun <!VIPER_TEXT!>fakePrint<!>(f: Foo) {  }
@AlwaysVerify
fun <!VIPER_TEXT!>fakePrint<!>(value: Int) {  }
@AlwaysVerify
fun <!VIPER_TEXT!>fakePrint<!>(truth: Boolean) {  }

@AlwaysVerify
fun <!VIPER_TEXT!>differInNullability<!>(i: Int) {  }
@AlwaysVerify
fun <!VIPER_TEXT!>differInNullability<!>(i: Int?) {  }

@AlwaysVerify
fun <!VIPER_TEXT!>testGlobalScopeOverloading<!>() {
    fakePrint(42)
    fakePrint(true)
    fakePrint(Foo())
    fakePrint(Bar())
}

@AlwaysVerify
fun <!VIPER_TEXT!>testClassFunctionOverloading<!>() {
    val b = Bar()
    b.baz(Foo())
    b.baz(Bar())
}
