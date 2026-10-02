import org.jetbrains.kotlin.formver.plugin.NeverConvert
import org.jetbrains.kotlin.formver.plugin.AlwaysVerify

@NeverConvert
fun id(x: Int?): Int? = x

@AlwaysVerify
fun <!VIPER_TEXT!>elvisOperator<!>(x: Int?): Int {
    return x ?: 3
}

@AlwaysVerify
fun <!VIPER_TEXT!>elvisOperatorComplex<!>(x: Int?): Int {
    return id(x) ?: elvisOperator(2)
}

@AlwaysVerify
fun <!VIPER_TEXT!>elvisOperatorReturn<!>(x: Int?): Int {
    val y = x ?: return 0
    return y
}
