// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun @Unique @Borrowed StringBuilder.<!VIPER_TEXT!>addX<!>() {
    append('x')
}

@AlwaysVerify
fun @Unique @Borrowed StringBuilder.<!VIPER_TEXT!>implicitReceiver<!>() {
    addX()
}

@AlwaysVerify
fun @Unique @Borrowed StringBuilder.<!VIPER_TEXT!>explicitReceiver<!>() {
    this.addX()
}

@AlwaysVerify
fun <!VIPER_TEXT!>viaParameter<!>(sb: @Unique @Borrowed StringBuilder) {
    sb.addX()
}

@AlwaysVerify
fun @Unique @Borrowed StringBuilder.<!VIPER_TEXT!>throwsAfter<!>(n: Int) {
    if (n < 0) throw IllegalArgumentException()
    append('y')
}
