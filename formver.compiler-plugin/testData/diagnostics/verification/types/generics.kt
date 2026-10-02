import org.jetbrains.kotlin.formver.plugin.AlwaysVerify


class Box<T>(var t: T) {
    @AlwaysVerify
    fun <!VIPER_TEXT!>genericMethod<!>(x: T): T {
        return x
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>createBox<!>(): Int {
    val boolBox = Box(true)
    val b = boolBox.t
    val intBox = Box(2)
    return intBox.t
}

@AlwaysVerify
fun <!VIPER_TEXT!>setGenericField<!>() {
    val box = Box(3)
    <!UNTRACKED_WRITE!>box.t = 5<!>
}

@AlwaysVerify
fun <T> <!VIPER_TEXT!>genericFun<!>(t: T): T = t

@AlwaysVerify
fun <!VIPER_TEXT!>callGenericFunc<!>() {
    val x = genericFun(3)
}

@AlwaysVerify
fun <!VIPER_TEXT!>genericAsIfCondition<!>(box: Box<Boolean>): Int {
    return if (box.t) 20 else 10
}
