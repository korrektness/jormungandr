import org.jetbrains.kotlin.formver.plugin.AlwaysVerify


@AlwaysVerify
fun <!VIPER_TEXT!>anyArgumentReturn<!>(x: Any): Any {
    return x
}

@AlwaysVerify
fun <!VIPER_TEXT!>anyCast<!>(x: Int): Any {
    return x
}
