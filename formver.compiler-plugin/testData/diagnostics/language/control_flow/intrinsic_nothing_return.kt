// FULL_JDK
// DIAGNOSTIC_KINDS

import org.jetbrains.kotlin.formver.plugin.*

fun <!VIPER_TEXT!>checkedStop<!>(value: Int): Nothing {
    preconditions { value > 0 }
    throw IllegalStateException()
}

fun <!VIPER_TEXT!>unavailableMultiset<!>(): Multiset<Int> = checkedStop(1)

fun <!VIPER_TEXT!>invalidUnavailableSequence<!>(): IterationSequence<Int> = <!VERIFICATION_CONDITION_NOT_ESTABLISHED!>checkedStop(0)<!>

@Pure
fun <!VIPER_TEXT!>absurd<!>(value: Nothing): Int = value
