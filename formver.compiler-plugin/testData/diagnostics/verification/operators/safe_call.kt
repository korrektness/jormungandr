import org.jetbrains.kotlin.formver.plugin.NeverConvert
import org.jetbrains.kotlin.formver.plugin.AlwaysVerify

class Foo {
    @NeverConvert
    fun f() {}
    val x = 0
}

@AlwaysVerify
fun <!VIPER_TEXT!>testSafeCall<!>(foo: Foo?) = foo?.f()

@Suppress("UNNECESSARY_SAFE_CALL")
@AlwaysVerify
fun <!VIPER_TEXT!>testSafeCallNonNullable<!>(foo: Foo) = foo?.f()

@AlwaysVerify
fun <!VIPER_TEXT!>testSafeCallProperty<!>(foo: Foo?): Int? = foo?.x

@Suppress("UNNECESSARY_SAFE_CALL")
@AlwaysVerify
fun <!VIPER_TEXT!>testSafeCallPropertyNonNullable<!>(foo: Foo): Int? = foo?.x

class Rec(val v: Int) {
    @NeverConvert
    fun nullable(): Rec? = this
}

@AlwaysVerify
fun <!VIPER_TEXT!>safeCallChain<!>(rec: Rec?): Int? {
    return rec?.nullable()?.nullable()?.v
}
