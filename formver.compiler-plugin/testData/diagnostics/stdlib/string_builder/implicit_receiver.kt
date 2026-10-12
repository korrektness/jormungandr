// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun @Unique @Borrowed StringBuilder.<!VIPER_TEXT!>addX<!>() {
    append('x')
}

fun @Unique @Borrowed StringBuilder.<!VIPER_TEXT!>implicitReceiver<!>() {
    addX()
}

fun @Unique @Borrowed StringBuilder.<!VIPER_TEXT!>explicitReceiver<!>() {
    this.addX()
}

fun <!VIPER_TEXT!>viaParameter<!>(sb: @Unique @Borrowed StringBuilder) {
    sb.addX()
}

fun @Unique @Borrowed StringBuilder.<!VIPER_TEXT!>throwsAfter<!>(n: Int) {
    if (n < 0) throw IllegalArgumentException()
    append('y')
}
