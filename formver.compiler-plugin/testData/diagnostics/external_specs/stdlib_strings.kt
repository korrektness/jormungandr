// FULL_JDK
// WITH_STDLIB
// EXTERNAL_SPEC: formver.compiler-plugin/plugin/resources/external-specs/Strings.kt

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>substringBasics<!>() {
    val middle = "hello".substring(1, 4)
    verify(middle.length == 3)
    verify(middle[0] == 'e' && middle[2] == 'l')
    val suffix = "hello".substring(3)
    verify(suffix.length == 2 && suffix[1] == 'o')
    val empty = "hello".substring(5, 5)
    verify(empty.length == 0)
}

fun <!VIPER_TEXT!>substringBounds<!>() {
    <!VIPER_VERIFICATION_ERROR!>"hello".substring(2, 6)<!>
}

fun <!VIPER_TEXT!>substringWrongCharacter<!>() {
    val middle = "hello".substring(1, 4)
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>middle[0] == 'h'<!>)
}
