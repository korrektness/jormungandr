import org.jetbrains.kotlin.formver.plugin.NeverConvert
import org.jetbrains.kotlin.formver.plugin.AlwaysVerify

@NeverConvert
fun f(x: Int): Int = x

@AlwaysVerify
fun <!VIPER_TEXT!>functionCall<!>() {
    f(0)
    f(0)
}

@AlwaysVerify
fun <!VIPER_TEXT!>functionCallNested<!>() {
    f(f(f(0)))
}

@AlwaysVerify
fun <!VIPER_TEXT!>callItself<!>() {
    callItself()
}
