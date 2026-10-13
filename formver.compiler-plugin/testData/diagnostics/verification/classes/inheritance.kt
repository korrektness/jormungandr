// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.preconditions


open class Foo(val x: Int) {
    val y: Int = 3
    var b = false
    fun <!VIPER_TEXT!>getY<!>(): Int {
        return y
    }
}

open class Bar(x: Int) : Foo(x) {
    val z = 5

    fun <!VIPER_TEXT!>sum<!>(): Int {
        preconditions { -2147483648L <= x.toLong() + z.toLong() && x.toLong() + z.toLong() <= 2147483647L }
        return x + z
    }
}

<!VIPER_TEXT!>class Baz<!> : Bar(1)

fun <!VIPER_TEXT!>callSuperMethod<!>(bar: Bar): Int {
    return bar.getY()
}

fun <!VIPER_TEXT!>accessSuperField<!>(bar: Bar): Boolean {
    return bar.b
}

fun <!VIPER_TEXT!>accessNewField<!>(bar: Bar): Int {
    return bar.z
}

fun <!VIPER_TEXT!>callNewMethod<!>(bar: Bar): Int {
    preconditions { -2147483648L <= bar.x.toLong() + bar.z.toLong() && bar.x.toLong() + bar.z.toLong() <= 2147483647L }
    return bar.sum()
}

fun <!VIPER_TEXT!>setSuperField<!>(bar: Bar) {
    bar.b = true
}

fun <!VIPER_TEXT!>accessSuperSuperField<!>(baz: Baz): Int {
    return baz.x
}
