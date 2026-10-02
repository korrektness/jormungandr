// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.NeverConvert
import org.jetbrains.kotlin.formver.plugin.AlwaysVerify

class Foo(var x: Int)

@NeverConvert
fun getFoo(): Foo = Foo(0)
@NeverConvert
fun sideEffect(): Int = 0

@AlwaysVerify
fun <!VIPER_TEXT!>test<!>() {
    getFoo().x = sideEffect()
    val y = getFoo().x + sideEffect()
}
