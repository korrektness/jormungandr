// WITH_STDLIB

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>sharedElementsUnconstrained<!>(l: List<Int>) {
    for (x in <!UNCHECKED_LIBRARY_CALL!>l<!>) verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>x == 0<!>)
}

fun <!VIPER_TEXT!>sharedIterationMayBeEmpty<!>(l: List<Int>) {
    var visited = false
    for (x in <!UNCHECKED_LIBRARY_CALL!>l<!>) visited = true
    verify(<!VERIFICATION_CONDITION_NOT_ESTABLISHED!>visited<!>)
}
