
import org.jetbrains.kotlin.formver.plugin.NeverConvert
import org.jetbrains.kotlin.formver.plugin.AlwaysVerify

@AlwaysVerify
fun <!VIPER_TEXT!>return_null<!>(): Int? = null

@AlwaysVerify
fun <!VIPER_TEXT!>useNullableTwice<!>(x: Int?): Int? {
    val a = x
    val b = x
    return a
}

@AlwaysVerify
fun <!VIPER_TEXT!>passNullableParameter<!>(x: Int?): Int? {
    useNullableTwice(x)
    return x
}

@AlwaysVerify
fun <!VIPER_TEXT!>nullableNullableComparison<!>(x: Int?, y: Int?): Boolean {
    return x == y
}

@AlwaysVerify
fun <!VIPER_TEXT!>nullableNonNullableComparison<!>(x: Int?, y: Int?): Boolean {
    return x != 3
}

@AlwaysVerify
fun <!VIPER_TEXT!>nullComparison<!>(x: Int?): Boolean {
    return x == null
}
