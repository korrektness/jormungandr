import org.jetbrains.kotlin.formver.plugin.AlwaysVerify


@AlwaysVerify
fun <!VIPER_TEXT!>returnUnit<!>() {}
@AlwaysVerify
fun <!VIPER_TEXT!>returnInt<!>(): Int { return 0 }
@AlwaysVerify
fun <!VIPER_TEXT!>takeIntReturnUnit<!>(@Suppress("UNUSED_PARAMETER") x: Int) {}
@AlwaysVerify
fun <!VIPER_TEXT!>takeIntReturnInt<!>(x: Int): Int { return x }
@AlwaysVerify
fun <!VIPER_TEXT!>takeIntReturnIntExpr<!>(x: Int): Int = x
@AlwaysVerify
fun <!VIPER_TEXT!>withIntDeclaration<!>(): Int {
    val x = 0
    return x
}
@AlwaysVerify
fun <!VIPER_TEXT!>intAssignment<!>() {
    var x = 0
    x = 1
}
