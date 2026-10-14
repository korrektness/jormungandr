// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

// The literals here are several hundred characters long. An encoding whose verification cost grows with the length
// of a literal runs past the verification timeout on them.

fun <!VIPER_TEXT!>longMessage<!>(): String =
    "According to the TOML specification, all structures must have an associated key and name. For example, you cannot serialize a plain string or other primitive-like value without wrapping it " +
        "in an object field, such as: data class A(val myStr: String). Please ensure your value is wrapped and given a proper name. This sentence only pads the message so that it runs to several hundred characters."

fun <!VIPER_TEXT!>throwsLongMessage<!>(x: Int): Int {
    postconditions<Int> { it >= 0 }
    if (x < 0) throw IllegalArgumentException("According to the TOML specification, all structures must have an associated key and name. For example, you cannot serialize a plain string or other primitive-like value without wrapping it in an object field, such as: data class A(val myStr: String). Please ensure your value is wrapped and given a proper name. This sentence only pads the message so that it runs to several hundred characters.")
    return x
}

fun <!VIPER_TEXT!>inspectsLongLiteral<!>() {
    val message = "According to the TOML specification, all structures must have an associated key and name. For example, you cannot serialize a plain string or other primitive-like value without wrapping it in an object field, such as: data class A(val myStr: String). Please ensure your value is wrapped and given a proper name. This sentence only pads the message so that it runs to several hundred characters."
    val first = "According to the TOML specification, all structures must have an associated key and name. For example, you cannot serialize a plain string or other primitive-like value without wrapping it "
    val second = "in an object field, such as: data class A(val myStr: String). Please ensure your value is wrapped and given a proper name. This sentence only pads the message so that it runs to several hundred characters."
    verify(
        message.length == 394,
        message[3] == 'o',
        message[393] == '.',
        first + second == message,
    )
}

fun <!VIPER_TEXT!>wrongLengthOfLongLiteral<!>() {
    val message = "According to the TOML specification, all structures must have an associated key and name. For example, you cannot serialize a plain string or other primitive-like value without wrapping it in an object field, such as: data class A(val myStr: String). Please ensure your value is wrapped and given a proper name. This sentence only pads the message so that it runs to several hundred characters."
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>message.length == 395<!>)
}
