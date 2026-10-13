// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

class Foo(var x: Int)

@NeverConvert
fun getFoo(): Foo = Foo(0)
@NeverConvert
fun sideEffect(): Int {
    postconditions<Int> { result -> result == 0 }
    return 0
}

fun <!VIPER_TEXT!>test<!>() {
    getFoo().x = sideEffect()
    val y = getFoo().x + sideEffect()
}
