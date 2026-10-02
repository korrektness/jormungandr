// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.AlwaysVerify

open class Foo(val x: Int) {
    val y: Int = 3
    var b = false
    @AlwaysVerify
    fun <!VIPER_TEXT!>getY<!>(): Int {
        return y
    }
}

open class Bar(x: Int) : Foo(x) {
    val z = 5

    @AlwaysVerify
    fun <!VIPER_TEXT!>sum<!>(): Int = x + z
}

class Baz : Bar(1)

@AlwaysVerify
fun <!VIPER_TEXT!>callSuperMethod<!>(bar: Bar): Int {
    return bar.getY()
}

@AlwaysVerify
fun <!VIPER_TEXT!>accessSuperField<!>(bar: Bar): Boolean {
    return bar.b
}

@AlwaysVerify
fun <!VIPER_TEXT!>accessNewField<!>(bar: Bar): Int {
    return bar.z
}

@AlwaysVerify
fun <!VIPER_TEXT!>callNewMethod<!>(bar: Bar): Int {
    return bar.sum()
}

@AlwaysVerify
fun <!VIPER_TEXT!>setSuperField<!>(bar: Bar) {
    bar.b = true
}

@AlwaysVerify
fun <!VIPER_TEXT!>accessSuperSuperField<!>(baz: Baz): Int {
    return baz.x
}
