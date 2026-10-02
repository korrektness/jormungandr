import org.jetbrains.kotlin.formver.plugin.AlwaysVerify

class Foo(val x: Int) {
    @AlwaysVerify
    fun <!VIPER_TEXT!>memberFun<!>(): Int {
        return x
    }

    @AlwaysVerify
    fun <!VIPER_TEXT!>callMemberFun<!>() {
        memberFun()
    }

    @AlwaysVerify
    fun <!VIPER_TEXT!>siblingCall<!>(other: Foo) {
        other.memberFun()
    }
}

@AlwaysVerify
fun <!VIPER_TEXT!>outerMemberFunCall<!>(f: Foo) {
    f.memberFun()
}
