// FULL_JDK

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>verify_false<!>() {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>false<!>)
}

fun <!VIPER_TEXT!>verify_compound<!>() {
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>true && false<!>)
}

fun <!VIPER_TEXT!>requires_positive<!>(value: Int) {
    preconditions { value > 0 }
}

fun <!VIPER_TEXT!>first_call<!>() {
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>requires_positive(0)<!>
}

fun <!VIPER_TEXT!>second_call<!>() {
    <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>requires_positive(0)<!>
}

fun <!VIPER_TEXT!>verify_forall<!>() {
    verify(
        forAll<Int> { i ->
            i > 0 || i < 0 || i == 0
        }
    )
}
