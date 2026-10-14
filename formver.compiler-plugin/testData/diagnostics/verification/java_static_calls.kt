// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

<!VIPER_TEXT!>class Holder<!> {
    companion <!VIPER_TEXT!>object<!> {
        @JvmStatic
        fun <!VIPER_TEXT!>id<!>(n: Int): Int {
            postconditions<Int> { it == n }
            return n
        }
    }
}

fun <!VIPER_TEXT!>valid<!>(c: Int): Boolean = Character.isValidCodePoint(c)

fun <!VIPER_TEXT!>staticCallIsOpaque<!>() {
    val v = Character.isValidCodePoint(65)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>v<!>)
}

fun <!VIPER_TEXT!>staticField<!>(): Int = Integer.MAX_VALUE

fun <!VIPER_TEXT!>staticFieldIsOpaque<!>() {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>Integer.MAX_VALUE == 2147483647<!>)
}

fun <!VIPER_TEXT!>viaJvmStatic<!>() {
    val n = Holder.id(3)
    verify(n == 3)
}
